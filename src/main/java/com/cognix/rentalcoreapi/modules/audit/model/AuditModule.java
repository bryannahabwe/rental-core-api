package com.cognix.rentalcoreapi.modules.audit.model;

/** The functional area an audit event belongs to (used for filtering). */
public enum AuditModule {
    TENANT,
    UNIT,
    RENTAL_AGREEMENT,
    PAYMENT,
    PROPERTY,
    USER,
    AUTHENTICATION
}
