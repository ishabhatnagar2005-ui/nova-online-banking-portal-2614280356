package com.nova.bank.portfolio;
import com.nova.bank.auth.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Verify calculations and owner isolation with PostgreSQL fixtures. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@Transactional
class PortfolioServiceTest {
    @Autowired PortfolioService service; @Autowired PortfolioSeeder seeder; @Autowired AccountRepository accounts; @Autowired JdbcTemplate jdbc;
    private AuthService.Principal principal(String suffix) { return new AuthService.Principal(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix),UUID.randomUUID(),"test","CUSTOMER",Instant.parse("2026-10-14T08:10:00Z")); }
    @Test void fixturesAreIdempotentAndCalculationsAreExact() {
        seeder.seed(); seeder.seed();
        var m=service.summary(principal("11")); var r=service.summary(principal("12"));
        assertThat(m.customer()).isEqualTo("Miguel De La Cruz"); assertThat(m.greeting()).isEqualTo("Welcome, Miguel De La Cruz");
        assertThat(m.lastLogin()).isEqualTo(Instant.parse("2026-10-14T08:10:00Z"));
        assertThat(m.relationshipBalance()).isEqualByComparingTo("142860.25"); assertThat(m.accounts()).hasSize(4);
        assertThat(m.accounts().stream().filter(a->a.type().equals("CREDIT_CARD")).findFirst().orElseThrow().availableCredit()).isEqualByComparingTo("41550.00");
        assertThat(r.customer()).isEqualTo("Rosa Santos"); assertThat(r.relationshipBalance()).isEqualByComparingTo("54300.00"); assertThat(r.accounts()).hasSize(2);
        assertThat(r.accounts()).extracting(PortfolioService.AccountView::maskedIdentifier).containsExactly("•••• 2204","•••• 6640");
        assertThat(accounts.count()).isEqualTo(6);
    }
    @Test void missingAndUnknownIdentitiesAreRejected() {
        assertThatThrownBy(()->service.summary(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        var unknown=new AuthService.Principal(UUID.randomUUID(),UUID.randomUUID(),"unknown","CUSTOMER",null);
        assertThatThrownBy(()->service.summary(unknown)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThat(service.summary(principal("13")).accounts()).isEmpty();
        assertThat(service.summary(principal("13")).relationshipBalance()).isEqualByComparingTo("0.00");
    }
    @Test @Transactional void sumsFractionalDepositsAndExcludesInactiveAccounts() {
        jdbc.update("UPDATE accounts SET available_balance=0.10 WHERE id=?",UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"));
        jdbc.update("UPDATE accounts SET available_balance=0.20 WHERE id=?",UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02"));
        assertThat(service.summary(principal("11")).relationshipBalance()).isEqualByComparingTo("0.30");
        jdbc.update("UPDATE accounts SET status='INACTIVE' WHERE id=?",UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02"));
        assertThat(service.summary(principal("11")).relationshipBalance()).isEqualByComparingTo("0.10");
        assertThat(service.summary(principal("11")).accounts()).hasSize(3);
    }
}
