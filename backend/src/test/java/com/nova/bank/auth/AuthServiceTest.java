package com.nova.bank.auth;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
/** Exercise authentication state against a dedicated PostgreSQL database. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_identity_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
class AuthServiceTest {
    @Autowired AuthService auth; @Autowired UserRepository users; @Autowired SessionRepository sessions; @Autowired JwtService jwt; @Autowired AuditRepository audit;
    @BeforeEach void clean() { audit.deleteAll(); sessions.deleteAll(); users.deleteAll(); users.save(new BankUser(java.util.UUID.randomUUID(),"test-user",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("valid-password"),"Test User","CUSTOMER")); }
    @Test void lockoutIsPersistedAndSuccessResetsFailures() {
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("test-user","wrong"))).isInstanceOf(AuthFailure.class);
        assertThat(users.findByUsername("test-user").orElseThrow().failedAttempts).isEqualTo(1);
        auth.login(new AuthDtos.LoginRequest("test-user","valid-password"));
        assertThat(users.findByUsername("test-user").orElseThrow().failedAttempts).isZero();
        for (int i=0;i<2;i++) assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("test-user","wrong"))).isInstanceOf(AuthFailure.class).extracting(e -> ((AuthFailure)e).status()).isEqualTo(401);
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("test-user","wrong"))).isInstanceOf(AuthFailure.class).extracting(e -> ((AuthFailure)e).status()).isEqualTo(423);
        assertThat(users.findByUsername("test-user").orElseThrow().locked).isTrue();
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("test-user","valid-password"))).isInstanceOf(AuthFailure.class);
    }
    @Test void logoutAndIdleExpiryInvalidatePersistedSession() {
        var response=auth.login(new AuthDtos.LoginRequest("test-user","valid-password")); var ids=jwt.verify(response.token());
        var principal=auth.authenticate(ids).orElseThrow(); auth.logout(principal);
        assertThat(auth.authenticate(ids)).isEmpty(); assertThat(sessions.findById(ids.sessionId()).orElseThrow().revoked).isTrue();
        var second=jwt.verify(auth.login(new AuthDtos.LoginRequest("test-user","valid-password")).token());
        var s=sessions.findById(second.sessionId()).orElseThrow(); s.lastActivity=Instant.now().minusSeconds(151); sessions.saveAndFlush(s);
        assertThat(auth.authenticate(second)).isEmpty(); assertThat(sessions.findById(second.sessionId()).orElseThrow().revoked).isTrue();
    }
    @Test void unknownUserAndForgedSessionAreRejected() {
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("nobody","wrong"))).isInstanceOf(AuthFailure.class);
        assertThat(auth.authenticate(new JwtService.TokenIds(java.util.UUID.randomUUID(),java.util.UUID.randomUUID()))).isEmpty();
        assertThatThrownBy(() -> jwt.verify("bad.token.value")).isInstanceOf(RuntimeException.class);
    }
}
