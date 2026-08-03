package com.cognix.rentalcoreapi.modules.platform.controller;

import com.cognix.rentalcoreapi.modules.platform.dto.AccountSummaryResponse;
import com.cognix.rentalcoreapi.modules.platform.dto.StartSupportSessionRequest;
import com.cognix.rentalcoreapi.modules.platform.dto.SupportSessionResponse;
import com.cognix.rentalcoreapi.modules.platform.service.PlatformSupportService;
import com.cognix.rentalcoreapi.shared.security.PlatformPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Opening and closing support access to a customer account.
 *
 * <p>Reached with a PLATFORM token, never a SUPPORT one — which is why ending a
 * session still works while the session itself is read-only.
 */
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class SupportSessionController {

    private final PlatformSupportService platformSupportService;

    /** Customer accounts, to choose which to support. No business data. */
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountSummaryResponse>> searchAccounts(
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(platformSupportService.searchAccounts(q));
    }

    @PostMapping("/support-sessions")
    public ResponseEntity<SupportSessionResponse> startSession(
            @AuthenticationPrincipal PlatformPrincipal staff,
            @Valid @RequestBody StartSupportSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(platformSupportService.startSession(staff.platformUserId(), request));
    }

    @PostMapping("/support-sessions/{id}/end")
    public ResponseEntity<SupportSessionResponse> endSession(
            @AuthenticationPrincipal PlatformPrincipal staff,
            @PathVariable UUID id) {
        return ResponseEntity.ok(platformSupportService.endSession(staff.platformUserId(), id));
    }

    @GetMapping("/support-sessions")
    public ResponseEntity<List<SupportSessionResponse>> listSessions(
            @RequestParam(required = false) UUID accountId) {
        return ResponseEntity.ok(platformSupportService.listSessions(accountId));
    }
}
