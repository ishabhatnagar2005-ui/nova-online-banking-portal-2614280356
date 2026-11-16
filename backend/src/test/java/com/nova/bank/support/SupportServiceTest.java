package com.nova.bank.support;
import com.nova.bank.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
/** Exercise support against real customer and transaction records. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
class SupportServiceTest {
    @Autowired SupportService service; @Autowired JdbcTemplate jdbc;
    AuthService.Principal user(String suffix,String name) { return new AuthService.Principal(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix),UUID.randomUUID(),name,"CUSTOMER",null); }
    @Test void persistsUniqueReceiptsAndRejectsInvalidInput() {
        var miguel=user("11","rosa"); var a=service.inquire(miguel,"Account","Question","Please explain"); var b=service.inquire(miguel,"Card","Another","Example");
        assertThat(a.referenceNumber()).isNotEqualTo(b.referenceNumber()); assertThat(a.status()).isEqualTo("RECEIVED"); assertThat(a.estimatedResponse()).isEqualTo("24h");
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where customer_id=? and reference_number=? and created_at is not null and message='Please explain'",Integer.class,miguel.userId(),a.referenceNumber())).isEqualTo(1);
        assertThatThrownBy(()->service.inquire(miguel," ","subject","message")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->service.inquire(null,"topic","subject","message")).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(()->service.inquire(user("13","miguel"),"topic","subject","message")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void assistantUsesDatabaseOwnerAndHonestHandoff() {
        var miguel=user("11","rosa");var rosa=user("12","miguel");
        assertThat(service.chat(miguel,"account balance").message()).contains("142,860.25","48,500.00","13 Oct 2026").doesNotContain("54,300.00");
        assertThat(service.chat(rosa,"balance").message()).contains("54,300.00").doesNotContain("142,860.25","48,500.00");
        assertThat(service.chat(miguel,"card").message()).contains("1188","8,450.00","41,550.00");
        assertThat(service.chat(rosa,"card").message()).contains("no active card").doesNotContain("1188");
        assertThat(service.chat(miguel,"offers").message()).contains("Payday Ipon Boost","5% Dining Cashback").doesNotContain("Home Loan Fee Waiver");
        assertThat(service.chat(rosa,"offers").message()).contains("Home Loan Fee Waiver").doesNotContain("Payday Ipon Boost");
        assertThat(service.chat(rosa,"Speak to Agent").message()).contains("no live agent was contacted","no support ticket was opened");
        assertThatThrownBy(()->service.chat(rosa," ")).isInstanceOf(ResponseStatusException.class);
    }
}
