package com.cognix.rentalcoreapi.modules.expenses.dto;

import com.cognix.rentalcoreapi.modules.expenses.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ExpenseResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID unitId,
        String roomNumber,
        UUID categoryId,
        String categoryName,
        LocalDate expenseDate,
        BigDecimal amount,
        String method,
        String paidBy,
        String receiptUrl,
        String notes,
        LocalDateTime createdAt
) {
    public static ExpenseResponse from(Expense e) {
        return new ExpenseResponse(
                e.getId(),
                e.getProperty() != null ? e.getProperty().getId() : null,
                e.getProperty() != null ? e.getProperty().getName() : null,
                e.getUnit() != null ? e.getUnit().getId() : null,
                e.getUnit() != null ? e.getUnit().getRoomNumber() : null,
                e.getCategory() != null ? e.getCategory().getId() : null,
                e.getCategory() != null ? e.getCategory().getName() : null,
                e.getExpenseDate(),
                e.getAmount(),
                e.getMethod(),
                e.getPaidBy(),
                e.getReceiptUrl(),
                e.getNotes(),
                e.getCreatedAt()
        );
    }
}
