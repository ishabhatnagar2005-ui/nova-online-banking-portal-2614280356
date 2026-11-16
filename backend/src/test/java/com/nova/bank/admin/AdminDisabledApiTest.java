package com.nova.bank.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.nova.bank.auth.AuthService;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Verify demo mode is a separate server-side gate for administrators. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=false","nova.demo-password=demo-only"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@AutoConfigureMockMvc class AdminDisabledApiTest {
    @Autowired MockMvc mvc;
    @Test void adminCannotUseAnyControlsOutsideDemoMode() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        var principal=new AuthService.Principal(UUID.randomUUID(),UUID.randomUUID(),"admin","ADMIN",null);
        var auth=new UsernamePasswordAuthenticationToken(principal,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        mvc.perform(post("/api/v1/admin/reset").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/simulation/probe").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/simulation").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/simulation").with(authentication(auth)).contentType("application/json").content("{\"enabled\":true}")).andExpect(status().isForbidden());
    }
}
