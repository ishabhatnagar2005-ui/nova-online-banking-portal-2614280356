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
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@AutoConfigureMockMvc class AuthApiTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users; @Autowired SessionRepository sessions; @Autowired AuditRepository audit;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private java.util.UUID fixtureId; private String username;
    @BeforeEach void clean() { fixtureId=java.util.UUID.randomUUID(); username="api-"+fixtureId; var encoder=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(); users.save(new BankUser(fixtureId,username,encoder.encode("demo-only"),"Miguel De La Cruz","CUSTOMER")); }
    @AfterEach void removeFixture() { jdbc.update("delete from auth_audit where user_id=?",fixtureId); jdbc.update("delete from bank_users where id=?",fixtureId); }
    @Test void loginActivityLogoutAndRoleBoundary() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(post("/api/v1/auth/activity")).andExpect(status().isUnauthorized());
        String body=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("username",username,"password","demo-only"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer")).andExpect(jsonPath("$.expiresIn").value(86400)).andExpect(jsonPath("$.user.fullName").value("Miguel De La Cruz")).andReturn().getResponse().getContentAsString();
        String token=json.readTree(body).get("token").asText();
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/reset").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Logged out successfully"));
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void validationBadTokenAndLockoutAreRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/activity").header("Authorization","Bearer garbage")).andExpect(status().isUnauthorized());
        for (int i=0;i<3;i++) mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("username",username,"password","wrong"))))
            .andExpect(status().is(i==2 ? 423 : 401));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("username",username,"password","demo-only")))).andExpect(status().isLocked());
    }
    @Test void logoutWithoutValidBearerIsUnauthorized() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void loginValidatesEachFieldAndAcceptsExactMaximumLengths() throws Exception {
        int before=jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Integer.class,fixtureId);
        long auditBefore=audit.count();
        for (String field : new String[]{"username","password"}) {
            for (Object invalid : java.util.List.of(true,false,123,12.5,java.util.Map.of("nested","value"),java.util.List.of("value"),"", " ", "x".repeat(field.equals("username") ? 65 : 73))) {
                var body=new java.util.HashMap<String,Object>(); body.put("username",username); body.put("password","demo-only"); body.put(field,invalid);
                mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            }
            var body=new java.util.HashMap<String,Object>(); body.put("username",username); body.put("password","demo-only"); body.put(field,null);
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            body.remove(field);
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Integer.class,fixtureId)).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThat(users.findByUsername(username).orElseThrow().failedAttempts).isZero();
        org.assertj.core.api.Assertions.assertThat(audit.count()).isEqualTo(auditBefore);
        String maxName="u".repeat(64), maxPassword="p".repeat(72);
        jdbc.update("update bank_users set username=?, password_hash=? where id=?",maxName,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(maxPassword),fixtureId);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("username",maxName,"password",maxPassword)))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Integer.class,fixtureId)).isEqualTo(before+1);
    }
}
