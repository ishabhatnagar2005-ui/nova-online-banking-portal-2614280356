package com.nova.bank.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.bank.DisposableDatabaseGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercise bearer authorization, reset and simulation at the HTTP boundary. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@org.springframework.transaction.annotation.Transactional
@AutoConfigureMockMvc @ContextConfiguration(initializers=DisposableDatabaseGuard.class) class AdminApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void verifyDisposableTarget() { DisposableDatabaseGuard.verify(jdbc); }
    String login(String user) throws Exception { String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"demo-only\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(); return json.readTree(body).get("token").asText(); }
    @Test void adminCanResetRetainSessionAndUseAuditedSimulation() throws Exception {
        String token=login("admin"); String bearer="Bearer "+token;
        DisposableDatabaseGuard.verify(jdbc);
        mvc.perform(post("/api/v1/admin/reset").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Database successfully restored to original demonstration fixtures."));
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/simulation").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        mvc.perform(post("/api/v1/admin/simulation/probe").header("Authorization",bearer)).andExpect(status().isServiceUnavailable());
        String audit=mvc.perform(get("/api/v1/admin/audit").header("Authorization",bearer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("DEMO_RESET","SIMULATED_ERROR").doesNotContain("miguel","admin","password","userId");
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/simulation").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/admin/simulation/probe").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Demonstration probe is available."));
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }
    @Test void simulationRejectsNonBooleanValuesWithoutChangingState() throws Exception {
        String bearer="Bearer "+login("admin");
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        for (String value:new String[]{"null","\"true\"","\"false\"","1","0","1.0","{}","[]"}) {
            mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":"+value+"}")).andExpect(status().isBadRequest());
            mvc.perform(get("/api/v1/admin/simulation").header("Authorization",bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        }
    }
    @Test void customerAndAnonymousCannotAccessControls() throws Exception {
        String bearer="Bearer "+login("miguel");
        for(String path:new String[]{"/reset","/simulation/probe"}) { DisposableDatabaseGuard.verify(jdbc); mvc.perform(post("/api/v1/admin"+path)).andExpect(status().isUnauthorized()); DisposableDatabaseGuard.verify(jdbc); mvc.perform(post("/api/v1/admin"+path).header("Authorization",bearer)).andExpect(status().isForbidden()); }
        mvc.perform(get("/api/v1/admin/audit").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/simulation")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/simulation").header("Authorization",bearer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/simulation").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/simulation").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}")).andExpect(status().isForbidden());
    }
}
