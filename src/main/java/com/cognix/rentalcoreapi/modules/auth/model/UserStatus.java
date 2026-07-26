package com.cognix.rentalcoreapi.modules.auth.model;

/**
 * Account state of a user.
 *
 * <ul>
 *   <li>{@code ACTIVE} — can authenticate.</li>
 *   <li>{@code INVITED} — created via an email invite but has not yet set a
 *       password; cannot authenticate until they accept.</li>
 *   <li>{@code DEACTIVATED} — disabled by an admin; cannot authenticate.</li>
 * </ul>
 */
public enum UserStatus {
    ACTIVE,
    INVITED,
    DEACTIVATED
}
