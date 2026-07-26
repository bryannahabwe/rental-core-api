package com.cognix.rentalcoreapi.modules.auth.model;

/**
 * Roles within a single landlord account.
 *
 * <ul>
 *   <li>{@code SUPER_ADMIN} — the account owner (original registrant); full
 *       control, including managing users and properties.</li>
 *   <li>{@code ADMIN} — full-access staff: all data across all properties,
 *       reports, and settings; may manage PROPERTY_MANAGERs.</li>
 *   <li>{@code PROPERTY_MANAGER} — staff limited to assigned properties; manages
 *       tenants, units, agreements, and payments there only.</li>
 * </ul>
 */
public enum UserRole {
    SUPER_ADMIN,
    ADMIN,
    PROPERTY_MANAGER;

    public String withPrefix() {
        return "ROLE_" + name();
    }

    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
