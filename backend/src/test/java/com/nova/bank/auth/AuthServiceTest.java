package com.nova.bank.auth;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.*;
/** Exercise authentication state against a dedicated PostgreSQL database. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_identity_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
class AuthServiceTest {
    @Autowired AuthService auth; @Autowired UserRepository users; @Autowired SessionRepository sessions; @Autowired JwtService jwt; @Autowired AuditRepository audit;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    private java.util.UUID fixtureId; private String username;
    @BeforeEach void clean() { fixtureId=java.util.UUID.randomUUID(); username="service-"+fixtureId; users.save(new BankUser(fixtureId,username,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("valid-password"),"Test User","CUSTOMER")); }
    @AfterEach void removeFixture() { jdbc.update("delete from auth_audit where user_id=?",fixtureId); jdbc.update("delete from bank_users where id=?",fixtureId); }
    @Test void lockoutIsPersistedAndSuccessResetsFailures() {
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest(username,"wrong"))).isInstanceOf(AuthFailure.class);
        assertThat(users.findByUsername(username).orElseThrow().failedAttempts).isEqualTo(1);
        auth.login(new AuthDtos.LoginRequest(username,"valid-password"));
        assertThat(users.findByUsername(username).orElseThrow().failedAttempts).isZero();
        for (int i=0;i<2;i++) assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest(username,"wrong"))).isInstanceOf(AuthFailure.class).extracting(e -> ((AuthFailure)e).status()).isEqualTo(401);
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest(username,"wrong"))).isInstanceOf(AuthFailure.class).extracting(e -> ((AuthFailure)e).status()).isEqualTo(423);
        assertThat(users.findByUsername(username).orElseThrow().locked).isTrue();
        assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest(username,"valid-password"))).isInstanceOf(AuthFailure.class);
    }
    @Test void logoutAndIdleExpiryInvalidatePersistedSession() {
        var response=auth.login(new AuthDtos.LoginRequest(username,"valid-password")); var ids=jwt.verify(response.token());
        var principal=auth.authenticate(ids).orElseThrow(); auth.logout(principal);
        assertThat(auth.authenticate(ids)).isEmpty(); assertThat(sessions.findById(ids.sessionId()).orElseThrow().revoked).isTrue();
        var second=jwt.verify(auth.login(new AuthDtos.LoginRequest(username,"valid-password")).token());
        var s=sessions.findById(second.sessionId()).orElseThrow(); s.lastActivity=Instant.now().minusSeconds(151); sessions.saveAndFlush(s);
        assertThat(auth.authenticate(second)).isEmpty(); assertThat(sessions.findById(second.sessionId()).orElseThrow().revoked).isTrue();
    }
    @Test void unknownUserAndForgedSessionAreRejected() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status->{
            assertThatThrownBy(() -> auth.login(new AuthDtos.LoginRequest("nobody-"+java.util.UUID.randomUUID(),"wrong"))).isInstanceOf(AuthFailure.class);
            audit.flush();
            var created=jdbc.queryForList("select id from auth_audit where user_id is null and event='LOGIN_FAILED' and xmin = txid_current()::text::xid",java.util.UUID.class);
            assertThat(created).hasSize(1);
            assertThat(jdbc.update("delete from auth_audit where id=? and user_id is null and event='LOGIN_FAILED'",created.get(0))).isEqualTo(1);
        });
        assertThat(auth.authenticate(new JwtService.TokenIds(java.util.UUID.randomUUID(),java.util.UUID.randomUUID()))).isEmpty();
        assertThatThrownBy(() -> jwt.verify("bad.token.value")).isInstanceOf(RuntimeException.class);
    }
    @Test void persistedAbsoluteExpiryRevokesSessionDespiteValidTokenAndRecentActivity() {
        String token=auth.login(new AuthDtos.LoginRequest(username,"valid-password")).token();
        var ids=jwt.verify(token);
        assertThat(auth.authenticate(ids)).isPresent();
        var session=sessions.findById(ids.sessionId()).orElseThrow();
        session.expiresAt=Instant.now().minusSeconds(1); session.lastActivity=Instant.now(); sessions.saveAndFlush(session);
        assertThat(jwt.verify(token)).isEqualTo(ids);
        assertThat(auth.authenticate(ids)).isEmpty();
        assertThat(sessions.findById(ids.sessionId()).orElseThrow().revoked).isTrue();
    }
    @Test void existingSessionCannotAuthenticateDifferentIdentity() {
        var ids=jwt.verify(auth.login(new AuthDtos.LoginRequest(username,"valid-password")).token());
        var before=sessions.findById(ids.sessionId()).orElseThrow().lastActivity;
        assertThat(auth.authenticate(new JwtService.TokenIds(java.util.UUID.randomUUID(),ids.sessionId()))).isEmpty();
        var stored=sessions.findById(ids.sessionId()).orElseThrow();
        assertThat(stored.revoked).isFalse(); assertThat(stored.lastActivity).isEqualTo(before);
        assertThat(auth.authenticate(ids)).isPresent();
    }
}
