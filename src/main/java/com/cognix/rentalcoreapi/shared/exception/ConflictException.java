package com.cognix.rentalcoreapi.shared.exception;

/**
 * The request conflicts with the current state of the resource — a duplicate,
 * or an operation the resource's state does not permit (e.g. paying a
 * terminated agreement). Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
