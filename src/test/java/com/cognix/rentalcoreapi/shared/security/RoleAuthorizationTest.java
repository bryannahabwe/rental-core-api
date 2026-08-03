package com.cognix.rentalcoreapi.shared.security;

import com.cognix.rentalcoreapi.modules.audit.controller.AuditController;
import com.cognix.rentalcoreapi.modules.audit.service.AuditService;
import com.cognix.rentalcoreapi.modules.auth.repository.UserRepository;
import com.cognix.rentalcoreapi.modules.payments.controller.PaymentController;
import com.cognix.rentalcoreapi.modules.payments.service.PaymentService;
import com.cognix.rentalcoreapi.modules.reports.controller.ReportController;
import com.cognix.rentalcoreapi.modules.reports.service.ReportService;
import com.cognix.rentalcoreapi.modules.settings.controller.LandlordSettingsController;
import com.cognix.rentalcoreapi.modules.settings.service.LandlordSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the role matrix at the controller boundary: who gets through and who gets
 * a 403. The services are mocked, so a 200 here means "authorized", nothing more.
 *
 * <p>Runs with the filter chain off — {@code @WithMockUser} populates the
 * security context directly, and it's {@code @EnableMethodSecurity} that the
 * {@code @PreAuthorize} annotations hang off.
 */
@WebMvcTest(controllers = {
        LandlordSettingsController.class,
        PaymentController.class,
        ReportController.class,
        AuditController.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(MethodSecurityTestConfig.class)
class RoleAuthorizationTest {

    /**
     * A body that binds and validates cleanly, so the request reaches the
     * {@code @PreAuthorize} check. Argument resolution runs first, and a 400 from
     * a junk body would mask whatever authorization decided.
     */
    private static final String VALID_PAYMENT = """
            {
              "agreementId": "00000000-0000-0000-0000-000000000001",
              "paymentDate": "2026-08-01",
              "amount": 500000,
              "method": "CASH",
              "periodStartDate": "2026-08-01",
              "periodEndDate": "2026-08-31"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LandlordSettingsService settingsService;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private ReportService reportService;
    @MockitoBean
    private AuditService auditService;

    // JwtAuthFilter is a Filter bean, so the slice instantiates it even with the
    // chain switched off. Its collaborators only need to exist, not work.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private PropertyAccessGuard propertyAccessGuard;

    // ── The receipt fix: scoped staff can read branding and draw a number ──

    @Test
    @WithMockUser(roles = "PROPERTY_MANAGER")
    void managerCanReadSettingsAndDrawAReceiptNumber() throws Exception {
        mockMvc.perform(get("/settings")).andExpect(status().isOk());
        mockMvc.perform(post("/settings/receipt-number")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CARETAKER")
    void caretakerCanReadSettingsAndDrawAReceiptNumber() throws Exception {
        mockMvc.perform(get("/settings")).andExpect(status().isOk());
        mockMvc.perform(post("/settings/receipt-number")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "PROPERTY_MANAGER")
    void managerStillCannotEditBranding() throws Exception {
        mockMvc.perform(multipart("/settings/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1})))
                .andExpect(status().isForbidden());
    }

    // ── Caretaker: records payments, sees no portfolio reports ─────────────

    @Test
    @WithMockUser(roles = "CARETAKER")
    void caretakerCanRecordAPayment() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "CARETAKER")
    void caretakerCannotSeeReportsOrTheActivityFeed() throws Exception {
        mockMvc.perform(get("/reports/summary")).andExpect(status().isForbidden());
        mockMvc.perform(get("/activity")).andExpect(status().isForbidden());
    }

    // ── Accountant: reads finance, writes nothing ─────────────────────────

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCanReadReportsAndTheActivityFeed() throws Exception {
        mockMvc.perform(get("/reports/summary")).andExpect(status().isOk());
        mockMvc.perform(get("/activity")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ACCOUNTANT")
    void accountantCannotRecordPaymentsOrDrawReceiptNumbers() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/settings/receipt-number")).andExpect(status().isForbidden());
    }
}
