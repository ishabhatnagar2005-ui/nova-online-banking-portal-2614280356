package com.nova.bank.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** Exercise the real security filter and HTTP contract against PostgreSQL. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_identity_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@AutoConfigureMockMvc class AuthApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired SessionRepository sessions; @Autowired AuditRepository audit;
    @BeforeEach void clean() { audit.deleteAll(); sessions.deleteAll(); users.deleteAll(); var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(); users.save(new BankUser(java.util.UUID.randomUUID(),"miguel",encoder.encode("demo-only"),"Miguel De La Cruz","CUSTOMER")); }
    @Test void loginActivityLogoutAndRoleBoundary() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(post("/api/v1/auth/activity")).andExpect(status().isUnauthorized());
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"miguel\",\"password\":\"demo-only\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(86400)).andExpect(jsonPath("$.user.fullName").value("Miguel De La Cruz")).andReturn().getResponse().getContentAsString();
        String token=json.readTree(body).get("token").asText();
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out successfully"));
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void validationBadTokenAndLockoutAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer garbage")).andExpect(status().isUnauthorized());
        for (int i=0;i<3;i++) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"miguel\",\"password\":\"wrong\"}"))
            .andExpect(status().is(i==2 ? 423 : 401));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"miguel\",\"password\":\"demo-only\"}")).andExpect(status().isLocked());
    }
}
