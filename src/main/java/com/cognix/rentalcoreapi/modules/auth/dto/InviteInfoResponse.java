package com.cognix.rentalcoreapi.modules.auth.dto;

/**
 * Shown on the public accept-invite page so the invitee can confirm who/what
 * they're joining before setting a password.
 */
public record InviteInfoResponse(
        String name,
        String email,
        String accountName
) {
}
