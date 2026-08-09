package com.cognix.rentalcoreapi.shared.exception;

/**
 * A requested resource does not exist (or is not visible to the caller within
 * their account). Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
