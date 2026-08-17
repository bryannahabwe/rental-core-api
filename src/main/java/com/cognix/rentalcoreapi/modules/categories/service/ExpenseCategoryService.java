package com.cognix.rentalcoreapi.modules.categories.service;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.categories.dto.ExpenseCategoryRequest;
import com.cognix.rentalcoreapi.modules.categories.dto.ExpenseCategoryResponse;
import com.cognix.rentalcoreapi.modules.categories.model.ExpenseCategory;
import com.cognix.rentalcoreapi.modules.categories.repository.ExpenseCategoryRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Account-managed expense categories (see {@link ExpenseCategory}). */
@Service
@RequiredArgsConstructor
public class ExpenseCategoryService {

    /** Seeded for every new account so the expense picker isn't empty. */
    static final List<String> DEFAULT_CATEGORIES = List.of(
            "Repairs & maintenance", "Utilities", "Staff & wages", "Management fees",
            "Security", "Cleaning", "Taxes & levies", "Insurance", "Supplies", "Other");

    private final ExpenseCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final AuditWriter auditWriter;

    public List<ExpenseCategoryResponse> getAll() {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        return categoryRepository.findAllByLandlordIdOrderByNameAsc(landlordId)
                .stream().map(ExpenseCategoryResponse::from).toList();
    }

    @Transactional
    public ExpenseCategoryResponse create(ExpenseCategoryRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        String name = request.name().trim();

        if (categoryRepository.existsByLandlordIdAndNameIgnoreCase(landlordId, name)) {
            throw new ConflictException("A category with this name already exists: " + name);
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .name(name)
                .active(request.active() == null || request.active())
                .build();
        ExpenseCategory saved = categoryRepository.save(category);

        auditWriter.record(AuditModule.CATEGORY, AuditAction.CREATE, null, saved.getId().toString(),
                "%s added the expense category \"%s\".".formatted(JwtUtils.getCurrentUserName(), name));
        return ExpenseCategoryResponse.from(saved);
    }

    @Transactional
    public ExpenseCategoryResponse update(UUID id, ExpenseCategoryRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        ExpenseCategory category = categoryRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        String name = request.name().trim();
        // Only re-check uniqueness when the name actually changed.
        if (!category.getName().equalsIgnoreCase(name)
                && categoryRepository.existsByLandlordIdAndNameIgnoreCase(landlordId, name)) {
            throw new ConflictException("A category with this name already exists: " + name);
        }

        category.setName(name);
        if (request.active() != null) {
            category.setActive(request.active());
        }
        ExpenseCategory saved = categoryRepository.save(category);

        auditWriter.record(AuditModule.CATEGORY, AuditAction.UPDATE, null, saved.getId().toString(),
                "%s updated the expense category \"%s\".".formatted(JwtUtils.getCurrentUserName(), name));
        return ExpenseCategoryResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        ExpenseCategory category = categoryRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        // Soft-retire rather than hard-delete: expenses reference categories with
        // ON DELETE RESTRICT, and deactivating hides it from pickers while keeping
        // past expenses valid.
        category.setActive(false);
        categoryRepository.save(category);

        auditWriter.record(AuditModule.CATEGORY, AuditAction.DELETE, null, id.toString(),
                "%s removed the expense category \"%s\".".formatted(
                        JwtUtils.getCurrentUserName(), category.getName()));
    }

    /** Seeds the default categories for a new account (idempotent per name). */
    @Transactional
    public void seedDefaults(User owner) {
        for (String name : DEFAULT_CATEGORIES) {
            if (!categoryRepository.existsByLandlordIdAndNameIgnoreCase(owner.getId(), name)) {
                categoryRepository.save(ExpenseCategory.builder()
                        .landlord(owner).name(name).active(true).build());
            }
        }
    }
}
