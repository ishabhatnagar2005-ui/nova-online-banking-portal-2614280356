package com.nova.bank.accounts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise the real bearer security and PostgreSQL HTTP boundary. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@org.springframework.transaction.annotation.Transactional
@AutoConfigureMockMvc class AccountApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired com.nova.bank.auth.JwtService jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    final String base="/api/v1/accounts/b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02";
    @Autowired jakarta.persistence.EntityManager entityManager;
    String login(String user) throws Exception { String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); entityManager.flush(); return json.readTree(body).get("token").asText(); }
    @Test void ownerCanFilterAndDownloadMaskedStatement() throws Exception {
        String token=login("miguel");
        mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param("category","DINING").param("direction","DEBIT").param("search","882194").param("startDate","2026-10-09").param("endDate","2026-10-09").param("page","0").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.account.pending").value(185.00)).andExpect(jsonPath("$.items[0].reference").value("REF-TX-882194"));
        String statement=mvc.perform(get(base+"/statement/download").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=\"statement-9034-demo.txt\"")).andReturn().getResponse().getContentAsString();
        assertThat(statement).contains("9034","REF-TX-882191","REF-TX-882194").doesNotContain("owner_id","identifier_suffix");
    }
    @Test void crossOwnerAndMissingOrInvalidTokensNeverLeakAccount() throws Exception {
        String token=login("rosa");
        for(String path:new String[]{"/transactions","/statement/download"}) {
            String denied=mvc.perform(get(base+path).header("Authorization","Bearer "+token)).andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Account Not Available")).andReturn().getResponse().getContentAsString();
            assertThat(denied).doesNotContain("9034","Peso Savings","882191");
            mvc.perform(get(base+path)).andExpect(status().isUnauthorized());
            mvc.perform(get(base+path).header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        }
    }
    @Test void invalidInputsAreRejected() throws Exception {
        String token=login("miguel");
        for(String[] pair:new String[][]{{"page","-1"},{"size","0"},{"size","101"},{"direction","bad"},{"category","bad"},{"startDate","bad"},{"page","not-a-number"}})
            mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param(pair[0],pair[1])).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param("startDate","2026-10-14").param("endDate","2026-10-01")).andExpect(status().isBadRequest());
    }
    @Test void signedExpiredTokenIsRejectedEvenWithAnActiveSession() throws Exception {
        var ids=jwt.verify(login("miguel")); var now=java.time.Instant.now();
        String expired=jwt.issue(ids.userId(),ids.sessionId(),now.minusSeconds(120),now.minusSeconds(60));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jwt.verify(expired)).isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select revoked from auth_sessions where id=?",Boolean.class,ids.sessionId())).isFalse();
    }
    @Test void injectionAndOversizedSearchAreHandledAsInput() throws Exception {
        String token=login("miguel"); int before=jdbc.queryForObject("select count(*) from account_transactions",Integer.class);
        mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param("search","' OR 1=1; DROP TABLE account_transactions; --"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from account_transactions",Integer.class)).isEqualTo(before);
        mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param("search","x".repeat(201))).andExpect(status().isBadRequest());
        mvc.perform(get(base+"/transactions").header("Authorization","Bearer "+token).param("page","2147483647").param("size","100")).andExpect(status().isBadRequest());
    }
    @Test void loginRejectsInjectionAndInvalidFieldsWithoutChangingUser() throws Exception {
        String body=json.writeValueAsString(java.util.Map.of("username","miguel' OR '1'='1","password","demo-only"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select failed_attempts from bank_users where username='miguel'",Integer.class)).isZero();
        for(String invalid:new String[]{"{}","{\"username\":\"miguel\"}","{\"password\":\"demo-only\"}","{\"username\":\" \",\"password\":\"x\"}",json.writeValueAsString(java.util.Map.of("username","x".repeat(65),"password","x")),json.writeValueAsString(java.util.Map.of("username","miguel","password","x".repeat(73)))})
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        login("miguel");
    }
}
