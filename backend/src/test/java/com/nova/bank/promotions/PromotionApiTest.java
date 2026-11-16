package com.nova.bank.promotions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise bearer authentication and interest persistence at the HTTP boundary. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@AutoConfigureMockMvc class PromotionApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    String login(String user) throws Exception { String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("token").asText(); }
    @Test void customerSeesEligibleOffersAndRecordsInterest() throws Exception {
        String token=login("miguel"); String body=mvc.perform(get("/api/v1/promotions").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.demoDate").value("2026-10-14")).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("payday-ipon-boost","cashback-week","dining-cashback-5").doesNotContain("home-loan-fee-waiver","new-year-cashback-expired");
        UUID customer=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"), promotion=UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
        int before=jdbc.queryForObject("select count(*) from promotion_interests where customer_id=? and promotion_id=?",Integer.class,customer,promotion);
        mvc.perform(post("/api/v1/promotions/"+promotion+"/express-interest").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(PromotionService.MESSAGE));
        assertThat(jdbc.queryForObject("select count(*) from promotion_interests where customer_id=? and promotion_id=?",Integer.class,customer,promotion)).isEqualTo(before+1);
    }
    @Test void unauthorizedIneligibleInactiveAndInvalidRequestsFailClosed() throws Exception {
        String path="/api/v1/promotions/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01/express-interest";
        mvc.perform(get("/api/v1/promotions")).andExpect(status().isUnauthorized()); mvc.perform(post(path)).andExpect(status().isUnauthorized()); mvc.perform(get("/api/v1/promotions").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        String rosa=login("rosa"); String body=mvc.perform(get("/api/v1/promotions").header("Authorization","Bearer "+rosa)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("home-loan-fee-waiver").doesNotContain("payday-ipon-boost");
        mvc.perform(post(path).header("Authorization","Bearer "+rosa)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/promotions/d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a10/express-interest").header("Authorization","Bearer "+rosa)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/promotions/not-a-uuid/express-interest").header("Authorization","Bearer "+rosa)).andExpect(status().isBadRequest());
    }
    @Test @Transactional void expiredActiveOfferIsAbsentAndCannotRegister() throws Exception {
        String token=login("miguel"); UUID promotion=UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
        jdbc.update("update promotions set valid_until='2026-10-13', active=true where id=?",promotion);
        String body=mvc.perform(get("/api/v1/promotions").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("payday-ipon-boost");
        mvc.perform(post("/api/v1/promotions/"+promotion+"/express-interest").header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
    }
}
