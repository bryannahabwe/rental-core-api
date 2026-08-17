package com.cognix.rentalcoreapi.modules.categories.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code active} may be omitted on create (defaults to true). */
public record ExpenseCategoryRequest(
        @NotBlank String name,
        Boolean active
) {
}
