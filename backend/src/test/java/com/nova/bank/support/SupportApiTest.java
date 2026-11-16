package com.nova.bank.support;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Test authenticated HTTP requests and PostgreSQL persistence. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@AutoConfigureMockMvc class SupportApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    String login(String user) throws Exception { var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();return json.readTree(response).get("token").asText(); }
    @Test void inquiryPersistsAndInvalidRequestsFail() throws Exception {
        String body="{\"topic\":\"Account\",\"subject\":\"Test\",\"message\":\"Example\"}";
        mvc.perform(post("/api/v1/support/inquiries").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String token=login("miguel");
        String result=mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECEIVED")).andExpect(jsonPath("$.estimatedResponse").value("24h")).andReturn().getResponse().getContentAsString();
        String ref=json.readTree(result).get("referenceNumber").asText();
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where reference_number=? and customer_id=? and created_at is not null",Integer.class,ref,UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"))).isEqualTo(1);
        mvc.perform(post("/api/v1/support/inquiries").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"topic\":\" \",\"subject\":\"x\",\"message\":\"x\"}")).andExpect(status().isBadRequest());
    }
    @Test void chatRejectsUnauthenticatedAndSeparatesPersonas() throws Exception {
        String body="{\"message\":\"balance\"}";
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        String rosa=login("rosa"); String response=mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains("54,300.00").doesNotContain("142,860.25");
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/chat").header("Authorization","Bearer "+rosa).contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Speak to Agent\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no live agent was contacted")));
    }
}
