package com.nova.bank.admin;

import com.nova.bank.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Exercise atomic fixture reset and isolation on PostgreSQL. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@Transactional class AdminServiceTest {
    @Autowired AdminService service; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords;
    final UUID miguel=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"), admin=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
    AuthService.Principal actor() { return new AuthService.Principal(admin,UUID.randomUUID(),"admin","ADMIN",null); }
    @Test void resetRestoresFixturesCredentialsAndClearsActivityWithoutTouchingOtherUsers() {
        UUID other=UUID.randomUUID(); jdbc.update("insert into bank_users(id,username,password_hash,full_name,role) values (?,?,?,'Other','CUSTOMER')",other,"other-test","unchanged");
        jdbc.update("update bank_users set locked=true,failed_attempts=3,password_hash='changed' where id=?",miguel);
        jdbc.update("update accounts set available_balance=1 where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'");
        jdbc.update("delete from account_transactions where id='c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'");
        jdbc.update("update promotions set title='Changed' where id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'");
        jdbc.update("insert into support_inquiries values (?,?, 'topic','subject','message',?,now())",UUID.randomUUID(),miguel,UUID.randomUUID().toString());
        jdbc.update("insert into promotion_interests values (?,?,?,now())",UUID.randomUUID(),miguel,UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"));
        jdbc.update("insert into auth_sessions(id,user_id,last_activity,expires_at,revoked) values (?,?,now(),now()+interval '1 hour',false)",UUID.randomUUID(),miguel);
        jdbc.update("insert into auth_audit(id,user_id,event,occurred_at) values (?,?,?,now())",UUID.randomUUID(),miguel,"PRIVATE-EXAMPLE");
        service.reset(actor());
        assertThat(jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class)).isEqualByComparingTo("116620.10");
        assertThat(jdbc.queryForObject("select count(*) from account_transactions",Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select title from promotions where id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'",String.class)).isEqualTo("Payday Ipon Boost");
        assertThat(jdbc.queryForObject("select locked from bank_users where id=?",Boolean.class,miguel)).isFalse();
        assertThat(passwords.matches("demo-only",jdbc.queryForObject("select password_hash from bank_users where id=?",String.class,miguel))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from support_inquiries where customer_id=?",Integer.class,miguel)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from promotion_interests where customer_id=?",Integer.class,miguel)).isZero();
        assertThat(jdbc.queryForObject("select password_hash from bank_users where id=?",String.class,other)).isEqualTo("unchanged");
        assertThat(jdbc.queryForObject("select count(*) from auth_sessions where user_id=?",Integer.class,miguel)).isZero();
        assertThat(service.auditEvents(actor())).extracting(AdminService.AuditEvent::event).contains("DEMO_RESET").doesNotContain("PRIVATE-EXAMPLE");
    }
    @Test void nonDemoPromotionReferencePreventsAnyChanges() {
        UUID other=UUID.randomUUID();jdbc.update("insert into bank_users(id,username,password_hash,full_name,role) values (?,?,?,'Other','CUSTOMER')",other,"external-test","unchanged");
        jdbc.update("insert into promotion_interests values (?,?,?,now())",UUID.randomUUID(),other,UUID.fromString("d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01"));
        jdbc.update("update accounts set available_balance=1 where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'");
        assertThatThrownBy(()->service.reset(actor())).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class)).isEqualByComparingTo("1");
    }
    @Test void rejectsCustomerAndAuditsBoundedProbe() {
        assertThatThrownBy(()->service.reset(new AuthService.Principal(miguel,UUID.randomUUID(),"miguel","CUSTOMER",null))).isInstanceOf(ResponseStatusException.class);
        service.setProbe(actor(),true); assertThat(service.probe(actor())).isTrue(); service.setProbe(actor(),false); assertThat(service.probe(actor())).isFalse();
        assertThat(service.auditEvents(actor())).extracting(AdminService.AuditEvent::event).contains("SIMULATED_ERROR","SIMULATION_ENABLED","SIMULATION_DISABLED");
    }
}
