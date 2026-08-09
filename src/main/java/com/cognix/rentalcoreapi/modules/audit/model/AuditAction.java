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
    TRANSFER_OWNERSHIP,
    PROFILE_UPDATE,
    // Cognix staff opening/closing read-only access to a customer account.
    // Recorded against the CUSTOMER's account, so it appears in their own
    // activity feed — they see who looked, when, and why, without asking.
    SUPPORT_ACCESS_START,
    SUPPORT_ACCESS_END
}
