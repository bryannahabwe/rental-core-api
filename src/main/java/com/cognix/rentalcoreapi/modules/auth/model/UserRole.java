package com.cognix.rentalcoreapi.modules.auth.model;

/**
 * Roles within a single landlord account.
 *
 * <p>Account-wide roles reach every property; property-scoped roles reach only
 * the properties assigned to them in {@code user_properties}, and are held
 * <em>per property</em> — the same person can manage one property and only
 * caretake another.
 *
 * <ul>
 *   <li>{@code SUPER_ADMIN} — the account owner (original registrant); full
 *       control, including managing users and properties.</li>
 *   <li>{@code ADMIN} — full-access staff: all data across all properties,
 *       reports, and settings; may manage every non-admin role.</li>
 *   <li>{@code PROPERTY_MANAGER} — staff limited to assigned properties; manages
 *       tenants, units, agreements, and payments there only.</li>
 *   <li>{@code CARETAKER} — staff limited to assigned properties, and to the
 *       day-to-day there: records payments and reads tenants and units, but
 *       cannot write agreements or see portfolio reports.</li>
 *   <li>{@code ACCOUNTANT} — read-only finance across the whole account:
 *       reports, payments, tenant ledgers, and the activity feed. Writes
 *       nothing.</li>
 * </ul>
 */
public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    PROPERTY_MANAGER,
    CARETAKER,
    ACCOUNTANT;

    public String withPrefix() {
        return "ROLE_" + name();
    }

    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }

    /**
     * Roles whose reach is limited to the properties listed for them in
     * {@code user_properties}. Everything else is account-wide, and passes
     * straight through {@code PropertyAccessGuard}.
     */
    public boolean isPropertyScoped() {
        return this == ADMIN || this == PROPERTY_MANAGER || this == CARETAKER;
    }
}
