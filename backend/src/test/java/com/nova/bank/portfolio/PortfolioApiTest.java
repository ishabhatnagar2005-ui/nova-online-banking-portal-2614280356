package com.nova.bank.portfolio;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise login and portfolio isolation through the real security filter. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@AutoConfigureMockMvc class PortfolioApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    private String login(String user) throws Exception {
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")) .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
    @Test void bothPersonasReceiveOnlyTheirMaskedAccounts() throws Exception {
        String miguel=login("miguel");
        String m=mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+miguel)).andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Miguel De La Cruz")).andExpect(jsonPath("$.relationshipBalance").value(142860.25)).andExpect(jsonPath("$.accounts.length()").value(4)).andExpect(jsonPath("$.accounts[2].availableCredit").value(41550.00)).andReturn().getResponse().getContentAsString();
        assertThat(m).contains("4821","9034","1188","7712").doesNotContain("2204","6640","identifierSuffix","ownerId");
        String rosa=login("rosa");
        String r=mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+rosa)).andExpect(status().isOk()).andExpect(jsonPath("$.customer").value("Rosa Santos")).andExpect(jsonPath("$.relationshipBalance").value(54300.00)).andExpect(jsonPath("$.accounts.length()").value(2)).andReturn().getResponse().getContentAsString();
        assertThat(r).contains("2204","6640").doesNotContain("4821","9034","1188","7712","identifierSuffix","ownerId");
    }
    @Test void missingAndInvalidTokensDoNotReturnPortfolio() throws Exception {
        mvc.perform(get("/api/v1/portfolio/summary")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.accounts").doesNotExist());
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.accounts").doesNotExist());
    }
    @Test void previousLoginIsCapturedAtLoginForEachSession() throws Exception {
        jdbc.update("UPDATE bank_users SET last_login='2026-10-14T08:10:00Z' WHERE username='miguel'");
        String token=login("miguel");
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.lastLogin").value("2026-10-14T08:10:00Z"));
        login("miguel");
        mvc.perform(get("/api/v1/portfolio/summary").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.lastLogin").value("2026-10-14T08:10:00Z"));
    }
}
