package com.cognix.rentalcoreapi.modules.platform.dto;

import java.util.UUID;

/**
 * A platform sign-in. The token reaches {@code /platform/**} and no customer
 * data — that requires opening a support session. There is deliberately no
 * refresh token: platform sessions are short and re-authenticating is cheap.
 */
public record PlatformLoginResponse(
        String accessToken,
        UUID platformUserId,
        String name,
        String email
) {
}
