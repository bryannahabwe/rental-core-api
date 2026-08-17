package com.cognix.rentalcoreapi.modules.expenses.service;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.service.AuditWriter;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.categories.model.ExpenseCategory;
import com.cognix.rentalcoreapi.modules.categories.repository.ExpenseCategoryRepository;
import com.cognix.rentalcoreapi.modules.expenses.dto.ExpenseRequest;
import com.cognix.rentalcoreapi.modules.expenses.dto.ExpenseResponse;
import com.cognix.rentalcoreapi.modules.expenses.model.Expense;
import com.cognix.rentalcoreapi.modules.expenses.repository.ExpenseRepository;
import com.cognix.rentalcoreapi.modules.paymentmethods.service.PaymentMethodService;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.properties.repository.PropertyRepository;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.modules.units.repository.RentalUnitRepository;
import com.cognix.rentalcoreapi.shared.exception.ConflictException;
import com.cognix.rentalcoreapi.shared.exception.NotFoundException;
import com.cognix.rentalcoreapi.shared.response.PagedResponse;
import com.cognix.rentalcoreapi.shared.security.JwtUtils;
import com.cognix.rentalcoreapi.shared.security.PropertyAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final PropertyRepository propertyRepository;
    private final RentalUnitRepository unitRepository;
    private final UserRepository userRepository;
    private final PaymentMethodService paymentMethodService;
    private final PropertyAccessGuard propertyAccessGuard;
    private final AuditWriter auditWriter;

    public PagedResponse<ExpenseResponse> getAllExpenses(
            Pageable pageable, UUID categoryId, String search, LocalDate from, LocalDate to) {

        UUID landlordId = JwtUtils.getCurrentLandlordId();
        UUID propertyId = propertyAccessGuard.requireAccessibleProperty();

        Page<Expense> page = expenseRepository.findAllWithFilters(
                landlordId, propertyId, categoryId, search, from, to, pageable);

        return PagedResponse.from(page.map(ExpenseResponse::from));
    }

    public ExpenseResponse getExpense(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();
        Expense expense = expenseRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        propertyAccessGuard.assertCanAccess(expense.getProperty().getId());
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public ExpenseResponse recordExpense(ExpenseRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Property property = propertyRepository
                .findByIdAndLandlordId(request.propertyId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        propertyAccessGuard.assertCanAccess(property.getId());

        ExpenseCategory category = categoryRepository
                .findByIdAndLandlordId(request.categoryId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        RentalUnit unit = resolveUnit(request.unitId(), landlordId, property);
        String method = paymentMethodService.resolveOrCreate(landlordId, request.method());

        Expense expense = Expense.builder()
                .landlord(userRepository.getReferenceById(landlordId))
                .property(property)
                .unit(unit)
                .category(category)
                .expenseDate(request.expenseDate())
                .amount(request.amount())
                .method(method)
                .paidBy(trimToNull(request.paidBy()))
                .receiptUrl(trimToNull(request.receiptUrl()))
                .notes(request.notes())
                .build();

        Expense saved = expenseRepository.saveAndFlush(expense);

        auditWriter.record(AuditModule.EXPENSE, AuditAction.RECORD_EXPENSE,
                property.getId(), saved.getId().toString(),
                "%s recorded a UGX %,.0f %s expense for %s.".formatted(
                        JwtUtils.getCurrentUserName(), request.amount(),
                        category.getName(), property.getName()));

        return ExpenseResponse.from(saved);
    }

    @Transactional
    public ExpenseResponse updateExpense(UUID id, ExpenseRequest request) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Expense expense = expenseRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        propertyAccessGuard.assertCanAccess(expense.getProperty().getId());

        Property property = propertyRepository
                .findByIdAndLandlordId(request.propertyId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Property not found"));
        propertyAccessGuard.assertCanAccess(property.getId());

        ExpenseCategory category = categoryRepository
                .findByIdAndLandlordId(request.categoryId(), landlordId)
                .orElseThrow(() -> new NotFoundException("Category not found"));

        expense.setProperty(property);
        expense.setUnit(resolveUnit(request.unitId(), landlordId, property));
        expense.setCategory(category);
        expense.setExpenseDate(request.expenseDate());
        expense.setAmount(request.amount());
        expense.setMethod(paymentMethodService.resolveOrCreate(landlordId, request.method()));
        expense.setPaidBy(trimToNull(request.paidBy()));
        expense.setReceiptUrl(trimToNull(request.receiptUrl()));
        expense.setNotes(request.notes());

        Expense saved = expenseRepository.saveAndFlush(expense);

        auditWriter.record(AuditModule.EXPENSE, AuditAction.UPDATE,
                property.getId(), saved.getId().toString(),
                "%s updated a %s expense for %s.".formatted(
                        JwtUtils.getCurrentUserName(), category.getName(), property.getName()));

        return ExpenseResponse.from(saved);
    }

    @Transactional
    public void deleteExpense(UUID id) {
        UUID landlordId = JwtUtils.getCurrentLandlordId();

        Expense expense = expenseRepository.findByIdAndLandlordId(id, landlordId)
                .orElseThrow(() -> new NotFoundException("Expense not found"));
        propertyAccessGuard.assertCanAccess(expense.getProperty().getId());

        expenseRepository.delete(expense);

        auditWriter.record(AuditModule.EXPENSE, AuditAction.DELETE,
                expense.getProperty().getId(), id.toString(),
                "%s deleted a %s expense for %s.".formatted(
                        JwtUtils.getCurrentUserName(), expense.getCategory().getName(),
                        expense.getProperty().getName()));
    }

    private RentalUnit resolveUnit(UUID unitId, UUID landlordId, Property property) {
        if (unitId == null) {
            return null;
        }
        RentalUnit unit = unitRepository.findByIdAndLandlordId(unitId, landlordId)
                .orElseThrow(() -> new NotFoundException("Unit not found"));
        if (!unit.getProperty().getId().equals(property.getId())) {
            throw new ConflictException("Unit does not belong to the selected property");
        }
        return unit;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
