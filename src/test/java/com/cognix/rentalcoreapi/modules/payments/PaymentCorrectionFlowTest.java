package com.cognix.rentalcoreapi.modules.payments;

import com.cognix.rentalcoreapi.modules.agreements.model.AgreementStatus;
import com.cognix.rentalcoreapi.modules.agreements.model.BillingModel;
import com.cognix.rentalcoreapi.modules.agreements.model.RentalAgreement;
import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.properties.model.Property;
import com.cognix.rentalcoreapi.modules.tenants.model.Tenant;
import com.cognix.rentalcoreapi.modules.units.model.RentalUnit;
import com.cognix.rentalcoreapi.shared.security.AuthenticatedUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The correction feature end to end over real HTTP, against a real database:
 * the request bodies the web app actually sends, through the controllers,
 * services and allocation replay, and back out through the responses it reads.
 *
 * <p>This is where a wrong field name, a rejected payload or a figure that
 * fails to come back down the wire surfaces — the layer the unit and service
 * tests deliberately skip. Rolls back; needs the same Postgres the context test
 * does.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class PaymentCorrectionFlowTest {

    private static final BigDecimal RENT = new BigDecimal("100000.00");
    private static final LocalDate JAN_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JAN_END = LocalDate.of(2026, 1, 31);

    @Autowired private MockMvc mockMvc;
    // Reading responses only, so a plain mapper is enough — the app's own
    // configuration is what wrote them.
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired private EntityManager em;

    private Property property;
    private RentalAgreement agreement;

    @BeforeEach
    void seed() {
        User landlord = User.builder()
                .name("Owner").email(UUID.randomUUID() + "@test.local").build();
        landlord.setId(UUID.randomUUID());
        landlord.setAccountOwnerId(landlord.getId());
        em.persist(landlord);

        property = Property.builder().landlord(landlord).name("Block A").build();
        em.persist(property);

        Tenant tenant = Tenant.builder().landlord(landlord).property(property).name("Jane").build();
        em.persist(tenant);

        RentalUnit unit = RentalUnit.builder().landlord(landlord).property(property)
                .roomNumber("A1").rentAmount(RENT).build();
        em.persist(unit);

        agreement = RentalAgreement.builder()
                .landlord(landlord).property(property).tenant(tenant).unit(unit)
                .rentAmount(RENT).status(AgreementStatus.ACTIVE)
                .startDate(JAN_START).billingDay(1).billingModel(BillingModel.ADVANCE)
                .openingBalance(BigDecimal.ZERO).openingBalanceEntered(BigDecimal.ZERO)
                .build();
        em.persist(agreement);
        em.flush();

        // The principal JwtAuthFilter would have built from a valid token. Owner
        // role, so nothing here is testing property scoping — that is
        // RoleAuthorizationTest's job.
        AuthenticatedUser principal = new AuthenticatedUser(
                landlord.getId(), landlord.getId(), UserRole.SUPER_ADMIN, "Tester", "0700000000");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aPaymentCanBeRecorded_corrected_andRemoved() throws Exception {
        // ── Record 300k against a 100k-rent January: two months roll forward.
        String created = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("300000")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        assertThat(cycleAmounts()).containsExactly("100000.00", "100000.00", "100000.00");

        // ── Correct it down to 150k: only February keeps credit, and only 50k.
        mockMvc.perform(put("/payments/" + paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("150000")))
                .andExpect(status().isOk());

        assertThat(cycleAmounts()).containsExactly("100000.00", "50000.00");

        // ── Remove it: the agreement is left holding nothing at all.
        mockMvc.perform(delete("/payments/" + paymentId))
                .andExpect(status().isNoContent());

        assertThat(cycleAmounts()).isEmpty();
    }

    @Test
    void derivedCreditCannotBeCorrectedDirectly() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("300000")))
                .andExpect(status().isCreated());

        UUID rolloverId = rows().stream()
                .filter(r -> "ROLLOVER".equals(r.get("source").asText()))
                .map(r -> UUID.fromString(r.get("id").asText()))
                .findFirst().orElseThrow();

        // 409, not 500 — nothing has gone wrong, the row is simply not one that
        // records money changing hands.
        mockMvc.perform(delete("/payments/" + rolloverId))
                .andExpect(status().isConflict());
    }

    @Test
    void incomeTakesAManagedMethodNameAndAPersonWhoReceivedIt() throws Exception {
        // Exactly the body AddOtherIncomeModal now sends: the method is a name
        // from the account's managed list, not the old IncomeMethod enum, and
        // receivedBy is the person the label always claimed to be capturing.
        String body = """
                {
                  "propertyId": "%s",
                  "incomeDate": "2026-01-10",
                  "amount": 250000,
                  "category": "Late fee",
                  "method": "Mobile Money",
                  "receivedBy": "Front desk",
                  "reference": "RCP-77"
                }
                """.formatted(property.getId());

        mockMvc.perform(post("/other-income")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // And it comes back down the unified ledger the Income page reads.
        String ledger = mockMvc.perform(get("/income"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode entry = objectMapper.readTree(ledger).get("content").get(0);
        assertThat(entry.get("source").asText()).isEqualTo("OTHER");
        assertThat(entry.get("method").asText()).isEqualTo("Mobile Money");
        assertThat(entry.get("receivedBy").asText()).isEqualTo("Front desk");
    }

    @Test
    void rentShowsUpInTheLedgerNamingATenderTypeRatherThanAnEnumConstant() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("100000")))
                .andExpect(status().isCreated());

        String ledger = mockMvc.perform(get("/income"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode entry = objectMapper.readTree(ledger).get("content").get(0);
        assertThat(entry.get("source").asText()).isEqualTo("RENT");
        // Both halves of the union speak the same language now.
        assertThat(entry.get("method").asText()).isEqualTo("Cash");
        assertThat(entry.get("receivedBy").isNull()).isTrue();
    }

    @Test
    void aReceiptNumberIsDrawnOnceAndStaysWithThePayment() throws Exception {
        String created = mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("100000")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID paymentId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        // A freshly recorded payment carries no receipt until one is issued.
        assertThat(objectMapper.readTree(created).get("receiptNo").isNull()).isTrue();

        String first = mockMvc.perform(post("/payments/" + paymentId + "/receipt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/payments/" + paymentId + "/receipt"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(first).isNotBlank();
        // Downloading the receipt again reproduces the tenant's copy instead of
        // drawing a new number out of the sequence.
        assertThat(second).isEqualTo(first);

        String fetched = mockMvc.perform(get("/payments/" + paymentId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(fetched).get("receiptNo").asText()).isEqualTo(first);
    }

    @Test
    void carriedForwardCreditCannotBeReceipted() throws Exception {
        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentBody("300000")))
                .andExpect(status().isCreated());

        UUID rolloverId = rows().stream()
                .filter(r -> "ROLLOVER".equals(r.get("source").asText()))
                .map(r -> UUID.fromString(r.get("id").asText()))
                .findFirst().orElseThrow();

        mockMvc.perform(post("/payments/" + rolloverId + "/receipt"))
                .andExpect(status().isConflict());
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private String paymentBody(String amount) {
        return """
                {
                  "agreementId": "%s",
                  "paymentDate": "2026-01-05",
                  "amount": %s,
                  "method": "CASH",
                  "periodStartDate": "%s",
                  "periodEndDate": "%s"
                }
                """.formatted(agreement.getId(), amount, JAN_START, JAN_END);
    }

    private List<JsonNode> rows() throws Exception {
        String json = mockMvc.perform(get("/payments")
                        .param("size", "50").param("sortBy", "periodStartDate").param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("content").findParents("periodStartDate");
    }

    /**
     * What each cycle ended up holding, earliest first: {@code amount -
     * overpayment}, the figure the ledger reads a period by. A CASH row keeps
     * its full amount for the audit trail even when most of it spilled forward,
     * so the raw amount is not what a cycle holds.
     */
    private List<String> cycleAmounts() throws Exception {
        return rows().stream()
                .filter(r -> agreement.getId().toString().equals(r.get("agreementId").asText()))
                .map(r -> r.get("amount").decimalValue()
                        .subtract(r.get("overpayment").decimalValue())
                        .setScale(2).toPlainString())
                .toList();
    }
}
