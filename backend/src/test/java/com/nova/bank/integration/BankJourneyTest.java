package com.nova.bank.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.bank.DisposableDatabaseGuard;
import com.nova.bank.admin.AdminService;
import com.nova.bank.auth.AuthService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercise cross-feature journeys through the security filter and PostgreSQL. */
@SpringBootTest(properties = {
    "spring.datasource.url=${TEST_DATABASE_URL}",
    "spring.datasource.username=${TEST_DB_USERNAME:agent}",
    "spring.datasource.password=${TEST_DB_PASSWORD:}",
    "nova.jwt-secret=test-only-signing-secret-at-least-32-bytes",
    "nova.demo-mode=true", "nova.demo-password=demo-only", "nova.demo-date=2026-10-14"
})
@AutoConfigureMockMvc
@ContextConfiguration(initializers = DisposableDatabaseGuard.class)
class BankJourneyTest {
    private static final UUID MIGUEL = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID ROSA = UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
    private static final UUID SAVINGS = UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
    private static final UUID OFFER = UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    private static final String INQUIRY = "{\"topic\":\"Account\",\"subject\":\"Savings offer\",\"message\":\"Please explain the offer.\"}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminService adminService;

    @BeforeEach void verifyDisposableTarget() { DisposableDatabaseGuard.verify(jdbc); }

    @AfterEach void restoreFixtures() {
        DisposableDatabaseGuard.verify(jdbc);
        adminService.reset(new AuthService.Principal(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13"), UUID.randomUUID(), "admin", "ADMIN", null));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String bearer) throws Exception {
        DisposableDatabaseGuard.verify(jdbc);
        return mvc.perform(post("/api/v1/admin/reset").header("Authorization", bearer));
    }

    private String login(String username) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("username", username, "password", "demo-only"));
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("token").asText();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void customerJourneyPersistsAcrossFeaturesAndResetRestoresFixtures() throws Exception {
        String admin = login("admin");
        reset(admin).andExpect(status().isOk());
        String miguel = login("miguel");
        String rosa = login("rosa");

        String portfolio = mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", miguel))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Miguel De La Cruz"))
            .andExpect(jsonPath("$.relationshipBalance").value(142860.25))
            .andReturn().getResponse().getContentAsString();
        JsonNode accounts = json.readTree(portfolio).get("accounts");
        assertThat(accounts.toString()).contains(SAVINGS.toString(), "9034").doesNotContain("2204", "6640");
        String savingsId = null;
        for (JsonNode account : accounts) {
            if (account.get("id").asText().equals(SAVINGS.toString())) savingsId = account.get("id").asText();
        }
        assertThat(savingsId).isNotNull();
        String accountPath = "/api/v1/accounts/" + savingsId;
        mvc.perform(get(accountPath + "/transactions").header("Authorization", miguel).param("search", "882194"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.account.id").value(savingsId))
            .andExpect(jsonPath("$.items[0].reference").value("REF-TX-882194"));
        String denied = mvc.perform(get(accountPath + "/transactions").header("Authorization", rosa))
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        assertThat(denied).doesNotContain("9034", "882194", "Peso Savings");
        mvc.perform(get(accountPath + "/statement/download").header("Authorization", rosa)).andExpect(status().isForbidden());
        String rosaPortfolio = mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", rosa))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Rosa Santos"))
            .andReturn().getResponse().getContentAsString();
        assertThat(rosaPortfolio).contains("2204", "6640").doesNotContain("9034", "4821");

        String offers = mvc.perform(get("/api/v1/promotions").header("Authorization", miguel))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(offers).contains(OFFER.toString(), "payday-ipon-boost").doesNotContain("home-loan-fee-waiver");
        String interestPath = "/api/v1/promotions/" + OFFER + "/express-interest";
        mvc.perform(post(interestPath).header("Authorization", miguel)).andExpect(status().isOk());
        assertThat(count("select count(*) from promotion_interests where customer_id=? and promotion_id=? and registered_at is not null", MIGUEL, OFFER)).isEqualTo(1);
        int interests = count("select count(*) from promotion_interests");
        mvc.perform(post(interestPath).header("Authorization", rosa)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/promotions/not-a-uuid/express-interest").header("Authorization", miguel)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/promotions/" + UUID.randomUUID() + "/express-interest").header("Authorization", miguel)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/promotions/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a10/express-interest").header("Authorization", miguel)).andExpect(status().isNotFound());
        assertThat(count("select count(*) from promotion_interests")).isEqualTo(interests);
        assertThat(count("select count(*) from promotion_interests where customer_id=?", ROSA)).isZero();

        String inquiry = mvc.perform(post("/api/v1/support/inquiries").header("Authorization", miguel)
                .contentType(MediaType.APPLICATION_JSON).content(INQUIRY))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECEIVED"))
            .andReturn().getResponse().getContentAsString();
        String reference = json.readTree(inquiry).get("referenceNumber").asText();
        assertThat(count("select count(*) from support_inquiries where reference_number=? and customer_id=? and subject='Savings offer' and created_at is not null", reference, MIGUEL)).isEqualTo(1);
        assertThat(count("select count(*) from support_inquiries where reference_number=? and customer_id=?", reference, ROSA)).isZero();
        String chat = mvc.perform(post("/api/v1/chat").header("Authorization", miguel)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"balance\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(chat).contains("142,860.25").doesNotContain("54,300.00");
        reset(miguel).andExpect(status().isForbidden());

        jdbc.update("update accounts set available_balance=1 where id=?", SAVINGS);
        reset(admin).andExpect(status().isOk());
        assertThat(count("select count(*) from promotion_interests where customer_id=?", MIGUEL)).isZero();
        assertThat(count("select count(*) from support_inquiries where reference_number=?", reference)).isZero();
        assertThat(count("select count(*) from auth_sessions where user_id in (?,?)", MIGUEL, ROSA)).isZero();
        assertThat(jdbc.queryForObject("select available_balance from accounts where id=?", BigDecimal.class, SAVINGS)).isEqualByComparingTo("116620.10");
        assertThat(count("select count(*) from account_transactions where account_id=?", SAVINGS)).isEqualTo(5);
        assertThat(count("select count(*) from auth_audit where event='DEMO_RESET'")).isGreaterThanOrEqualTo(1);
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", miguel)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", rosa)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/audit").header("Authorization", admin)).andExpect(status().isOk());
        String renewed = login("miguel");
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", renewed))
            .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value(142860.25));
        mvc.perform(get(accountPath + "/transactions").header("Authorization", renewed).param("search", "882194"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].reference").value("REF-TX-882194"));
        mvc.perform(get("/api/v1/promotions").header("Authorization", renewed)).andExpect(status().isOk());
    }

    @Test
    void resetConflictPreservesCrossFeatureStateAndSessions() throws Exception {
        String admin = login("admin");
        reset(admin).andExpect(status().isOk());
        String miguel = login("miguel");
        mvc.perform(post("/api/v1/promotions/" + OFFER + "/express-interest").header("Authorization", miguel)).andExpect(status().isOk());
        String inquiry = mvc.perform(post("/api/v1/support/inquiries").header("Authorization", miguel)
                .contentType(MediaType.APPLICATION_JSON).content(INQUIRY))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String reference = json.readTree(inquiry).get("referenceNumber").asText();
        UUID outsider = UUID.randomUUID();
        UUID outsideInterest = UUID.randomUUID();
        try {
            jdbc.update("insert into bank_users(id,username,password_hash,full_name,role) values (?,?,?,'Outside','CUSTOMER')", outsider, "journey-" + outsider, "unused");
            jdbc.update("insert into promotion_interests(id,customer_id,promotion_id,registered_at) values (?,?,?,now())", outsideInterest, outsider, OFFER);
            jdbc.update("update accounts set available_balance=1 where id=?", SAVINGS);
            int resetAudits = count("select count(*) from auth_audit where event='DEMO_RESET'");
            int sessions = count("select count(*) from auth_sessions where user_id=?", MIGUEL);
            reset(admin).andExpect(status().isConflict());
            assertThat(jdbc.queryForObject("select available_balance from accounts where id=?", BigDecimal.class, SAVINGS)).isEqualByComparingTo("1");
            assertThat(count("select count(*) from promotion_interests where customer_id=? and promotion_id=?", MIGUEL, OFFER)).isEqualTo(1);
            assertThat(count("select count(*) from promotion_interests where id=? and customer_id=?", outsideInterest, outsider)).isEqualTo(1);
            assertThat(count("select count(*) from support_inquiries where reference_number=? and customer_id=?", reference, MIGUEL)).isEqualTo(1);
            assertThat(count("select count(*) from auth_sessions where user_id=?", MIGUEL)).isEqualTo(sessions).isGreaterThan(0);
            assertThat(count("select count(*) from auth_audit where event='DEMO_RESET'")).isEqualTo(resetAudits);
            assertThat(count("select count(*) from account_transactions where account_id=?", SAVINGS)).isEqualTo(5);
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", miguel))
                .andExpect(status().isOk()).andExpect(jsonPath("$.relationshipBalance").value(26241.15));
        } finally {
            jdbc.update("delete from promotion_interests where id=?", outsideInterest);
            jdbc.update("delete from bank_users where id=?", outsider);
            reset(admin).andExpect(status().isOk());
        }
    }

    /** Verify that lockout survives failed requests until an authorized reset restores login. */
    @Test
    void lockoutAdminResetAndReloginPreserveIdentityAndSessionBoundaries() throws Exception {
        String admin = login("admin");
        reset(admin).andExpect(status().isOk());
        try {
            String miguel = login("miguel");
            String rosa = login("rosa");
            int failures = count("select count(*) from auth_audit where user_id=? and event='LOGIN_FAILED'", MIGUEL);
            int lockedEvents = count("select count(*) from auth_audit where user_id=? and event='LOGIN_LOCKED'", MIGUEL);
            String wrong = "{\"username\":\"miguel\",\"password\":\"wrong\"}";
            for (int attempt = 1; attempt <= 3; attempt++) {
                mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().is(attempt == 3 ? 423 : 401))
                    .andExpect(jsonPath("$.status").value(attempt == 3 ? 423 : 401));
                assertThat(count("select count(*) from bank_users where id=? and failed_attempts=? and locked=?", MIGUEL, attempt, attempt == 3)).isEqualTo(1);
            }
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"miguel\",\"password\":\"demo-only\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.status").value(423));
            assertThat(count("select count(*) from auth_audit where user_id=? and event='LOGIN_FAILED'", MIGUEL)).isEqualTo(failures + 2);
            assertThat(count("select count(*) from auth_audit where user_id=? and event='LOGIN_LOCKED'", MIGUEL)).isEqualTo(lockedEvents + 2);
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", miguel)).andExpect(status().isUnauthorized());
            reset(rosa).andExpect(status().isForbidden());
            assertThat(count("select count(*) from bank_users where id=? and failed_attempts=3 and locked", MIGUEL)).isEqualTo(1);
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", rosa))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Rosa Santos"));

            reset(admin).andExpect(status().isOk());
            assertThat(count("select count(*) from bank_users where id=? and failed_attempts=0 and not locked", MIGUEL)).isEqualTo(1);
            assertThat(count("select count(*) from auth_sessions where user_id in (?,?)", MIGUEL, ROSA)).isZero();
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", miguel)).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", rosa)).andExpect(status().isUnauthorized());
            String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"miguel\",\"password\":\"demo-only\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(MIGUEL.toString()))
                .andExpect(jsonPath("$.user.username").value("miguel"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
            String renewed = "Bearer " + json.readTree(response).get("token").asText();
            assertThat(count("select count(*) from auth_sessions where user_id=? and not revoked", MIGUEL)).isEqualTo(1);
            mvc.perform(get("/api/v1/portfolio/summary").header("Authorization", renewed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Miguel De La Cruz"));
            mvc.perform(get("/api/v1/admin/audit").header("Authorization", admin)).andExpect(status().isOk());
        } finally {
            reset(admin).andExpect(status().isOk());
        }
    }

    /** Verify that the assistant reflects persisted offer eligibility for each customer. */
    @Test
    void offerStateFlowsIntoAssistantWithoutLeakingOtherCustomersOffers() throws Exception {
        String admin = login("admin");
        reset(admin).andExpect(status().isOk());
        try {
            String miguel = login("miguel");
            String rosa = login("rosa");
            String question = "{\"message\":\"What offers are available?\"}";
            String miguelReply = mvc.perform(post("/api/v1/chat").header("Authorization", miguel)
                    .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actionPath").value("/offers"))
                .andReturn().getResponse().getContentAsString();
            assertThat(miguelReply).contains("Payday Ipon Boost", "Term Deposit Promo", "2026-10-14")
                .doesNotContain("Home Loan Fee Waiver", "New Year Cashback");
            String rosaReply = mvc.perform(post("/api/v1/chat").header("Authorization", rosa)
                    .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actionPath").value("/offers"))
                .andReturn().getResponse().getContentAsString();
            assertThat(rosaReply).contains("Home Loan Fee Waiver", "Term Deposit Promo")
                .doesNotContain("Payday Ipon Boost", "New Year Cashback");

            String interestPath = "/api/v1/promotions/" + OFFER + "/express-interest";
            mvc.perform(post(interestPath).header("Authorization", miguel)).andExpect(status().isOk());
            mvc.perform(post(interestPath).header("Authorization", rosa)).andExpect(status().isNotFound());
            assertThat(count("select count(*) from promotion_interests where customer_id=? and promotion_id=? and registered_at is not null", MIGUEL, OFFER)).isEqualTo(1);
            assertThat(count("select count(*) from promotion_interests where customer_id=? and promotion_id=?", ROSA, OFFER)).isZero();
            String afterInterest = mvc.perform(post("/api/v1/chat").header("Authorization", miguel)
                    .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(afterInterest).contains("Payday Ipon Boost").doesNotContain("Home Loan Fee Waiver");

            assertThat(jdbc.update("update promotions set active=false where id=?", OFFER)).isEqualTo(1);
            assertThat(count("select count(*) from promotions where id=? and not active", OFFER)).isEqualTo(1);
            mvc.perform(post(interestPath).header("Authorization", miguel)).andExpect(status().isNotFound());
            assertThat(count("select count(*) from promotion_interests where customer_id=? and promotion_id=?", MIGUEL, OFFER)).isEqualTo(1);
            String after = mvc.perform(post("/api/v1/chat").header("Authorization", miguel)
                    .contentType(MediaType.APPLICATION_JSON).content(question))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actionPath").value("/offers"))
                .andReturn().getResponse().getContentAsString();
            assertThat(after).contains("Term Deposit Promo", "Payday Ipon Bonus")
                .doesNotContain("Payday Ipon Boost", "Home Loan Fee Waiver");
            mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(question)).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/chat").header("Authorization", miguel).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
            assertThat(count("select count(*) from promotion_interests where promotion_id=?", OFFER)).isEqualTo(1);
        } finally {
            reset(admin).andExpect(status().isOk());
        }
    }
}
