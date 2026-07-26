package com.cognix.rentalcoreapi.modules.audit.model;

/** The kind of action an audit event records. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    MOVE_OUT,
    RECORD_PAYMENT,
    LOGIN,
    INVITE,
    ACCEPT_INVITE,
    DEACTIVATE,
    ROLE_CHANGE
}
