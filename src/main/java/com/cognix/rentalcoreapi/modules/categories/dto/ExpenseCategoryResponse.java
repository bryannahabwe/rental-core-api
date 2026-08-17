package com.cognix.rentalcoreapi.modules.categories.dto;

import com.cognix.rentalcoreapi.modules.categories.model.ExpenseCategory;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseCategoryResponse(
        UUID id,
        String name,
        boolean active,
        LocalDateTime createdAt
) {
    public static ExpenseCategoryResponse from(ExpenseCategory c) {
        return new ExpenseCategoryResponse(c.getId(), c.getName(), c.isActive(), c.getCreatedAt());
    }
}
