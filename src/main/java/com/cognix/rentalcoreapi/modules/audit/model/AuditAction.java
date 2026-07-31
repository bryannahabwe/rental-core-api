package com.cognix.rentalcoreapi.modules.audit.model;

/** The kind of action an audit event records. */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    MOVE_OUT,
    RECORD_PAYMENT,
    ISSUE_RECEIPT,
    VIEW_REPORT,
    REGISTER,
    LOGIN,
    LOGIN_FAILED,
    INVITE,
    RESEND_INVITE,
    ACCEPT_INVITE,
    DEACTIVATE,
    ROLE_CHANGE,
    PROFILE_UPDATE
}
