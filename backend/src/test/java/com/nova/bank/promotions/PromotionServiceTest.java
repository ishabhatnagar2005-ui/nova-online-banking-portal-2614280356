package com.nova.bank.promotions;

import com.nova.bank.auth.AuthService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Verify eligibility, expiry and writes against PostgreSQL. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@org.springframework.transaction.annotation.Transactional
class PromotionServiceTest {
    @Autowired PromotionService service; @Autowired PromotionSeeder seeder; @Autowired JdbcTemplate jdbc;
    AuthService.Principal principal(String suffix,String claimed) { return new AuthService.Principal(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix),UUID.randomUUID(),claimed,"CUSTOMER",null); }
    UUID id(String suffix) { return UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix); }
    @Test void catalogueIsSeededIdempotentlyAndScopedByDatabaseIdentity() {
        seeder.seed(); seeder.seed(); assertThat(jdbc.queryForObject("select count(*) from promotions",Integer.class)).isEqualTo(10);
        var miguel=service.list(principal("11","rosa")); assertThat(miguel.demoDate()).isEqualTo(LocalDate.parse("2026-10-14"));
        assertThat(miguel.promotions()).extracting(PromotionService.Promotion::key).contains("payday-ipon-boost","cashback-week","dining-cashback-5","term-deposit-promo","referral-reward").doesNotContain("home-loan-fee-waiver","new-year-cashback-expired");
        assertThat(miguel.promotions()).hasSize(8);
        assertThat(service.list(principal("12","miguel")).promotions()).extracting(PromotionService.Promotion::key).containsExactlyInAnyOrder("home-loan-fee-waiver","term-deposit-promo","referral-reward");
        assertThat(service.list(principal("13","miguel")).promotions()).isEmpty();
    }
    @Test void expiryInclusiveAndInactiveOffersAreEnforcedForReadsAndWrites() {
        var lastDay=new PromotionService(jdbc,LocalDate.parse("2026-12-31")); var after=new PromotionService(jdbc,LocalDate.parse("2027-01-01"));
        assertThat(lastDay.list(principal("11","x")).promotions()).hasSize(8);
        assertThat(after.list(principal("11","x")).promotions()).isEmpty();
        assertThatThrownBy(()->after.expressInterest(principal("11","x"),id("01"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.expressInterest(principal("11","x"),id("10"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.expressInterest(principal("12","miguel"),id("01"))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.expressInterest(principal("13","miguel"),id("08"))).isInstanceOf(ResponseStatusException.class);
    }
    @Test void registrationPersistsCustomerPromotionAndTimestamp() {
        UUID customer=principal("11","x").userId(), promotion=id("02");
        int before=jdbc.queryForObject("select count(*) from promotion_interests where customer_id=? and promotion_id=?",Integer.class,customer,promotion);
        assertThat(service.expressInterest(principal("11","x"),promotion).message()).contains("No real application or credit check");
        assertThat(jdbc.queryForObject("select count(*) from promotion_interests where customer_id=? and promotion_id=? and registered_at is not null",Integer.class,customer,promotion)).isEqualTo(before+1);
        assertThatThrownBy(()->service.expressInterest(null,promotion)).isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(()->service.list(null)).isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(()->service.expressInterest(principal("11","x"),UUID.randomUUID())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
    }
}
