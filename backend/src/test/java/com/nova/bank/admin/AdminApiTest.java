package com.nova.bank.admin;

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

/** Exercise bearer authorization, reset and simulation at the HTTP boundary. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@AutoConfigureMockMvc class AdminApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    String login(String user) throws Exception { String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("token").asText(); }
    @Test void adminCanResetRetainSessionAndUseAuditedSimulation() throws Exception {
        String token=login("admin"); String bearer="Bearer "+token;
        mvc.perform(post("/api/v1/admin/reset").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Database successfully restored to original demonstration fixtures."));
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/simulation/probe").header("Authorization",bearer)).andExpect(status().isServiceUnavailable());
        String audit=mvc.perform(get("/api/v1/admin/audit").header("Authorization",bearer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("DEMO_RESET","SIMULATED_ERROR").doesNotContain("miguel","admin","password","userId");
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
    }
    @Test void customerAndAnonymousCannotAccessControls() throws Exception {
        String bearer="Bearer "+login("miguel");
        for(String path:new String[]{"/reset","/simulation/probe"}) { mvc.perform(post("/api/v1/admin"+path)).andExpect(status().isUnauthorized()); mvc.perform(post("/api/v1/admin"+path).header("Authorization",bearer)).andExpect(status().isForbidden()); }
        mvc.perform(get("/api/v1/admin/audit").header("Authorization",bearer)).andExpect(status().isForbidden());
    }
}
