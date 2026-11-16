package com.nova.bank.admin;

import com.nova.bank.auth.AuthService;
import com.nova.bank.DisposableDatabaseGuard;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.*;

/** Exercise atomic fixture reset and isolation on PostgreSQL. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only"})
@Transactional @ContextConfiguration(initializers=DisposableDatabaseGuard.class) class AdminServiceTest {
    @Autowired AdminService service; @Autowired JdbcTemplate jdbc; @Autowired PasswordEncoder passwords; @Autowired PlatformTransactionManager transactionManager;
    final UUID miguel=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"), admin=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
    @BeforeEach void verifyDisposableTarget() { DisposableDatabaseGuard.verify(jdbc); }
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
        DisposableDatabaseGuard.verify(jdbc); service.reset(actor());
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
        DisposableDatabaseGuard.verify(jdbc); assertThatThrownBy(()->service.reset(actor())).isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class)).isEqualByComparingTo("1");
    }
    @Test void identityMismatchPreventsReset() {
        jdbc.update("update bank_users set username='changed-miguel' where id=?",miguel);
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select username from bank_users where id=?",String.class,miguel)).isEqualTo("changed-miguel");
    }
    @Test void promotionFixtureIdWithWrongKeyPreventsReset() {
        jdbc.update("update promotions set promotion_key='external-key' where id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'");
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select promotion_key from promotions where id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'",String.class)).isEqualTo("external-key");
    }
    @Test void promotionFixtureKeyAtExternalIdPreventsReset() {
        UUID external=UUID.randomUUID();
        jdbc.update("delete from promotion_interests where promotion_id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'");
        jdbc.update("update promotions set id=? where id='d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'",external);
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select promotion_key from promotions where id=?",String.class,external)).isEqualTo("payday-ipon-boost");
    }
    @Test void externallyOwnedFixtureAccountPreventsReset() {
        UUID other=externalUser();
        jdbc.update("update accounts set owner_id=? where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'",other);
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select owner_id from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'",UUID.class)).isEqualTo(other);
    }
    @Test void externalTransactionWithFixtureReferencePreventsReset() {
        UUID other=externalUser(), account=externalAccount(other), transaction=UUID.randomUUID();
        jdbc.update("update account_transactions set reference='changed-reference' where id='c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01'");
        insertExternalTransaction(transaction,account,"REF-TX-882191");
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select account_id from account_transactions where id=?",UUID.class,transaction)).isEqualTo(account);
    }
    @Test void externalTransactionWithFixtureIdPreventsReset() {
        UUID other=externalUser(), account=externalAccount(other);
        UUID transaction=UUID.fromString("c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
        jdbc.update("delete from account_transactions where id=?",transaction);
        insertExternalTransaction(transaction,account,"external-reference");
        assertResetConflictPreservesState();
        assertThat(jdbc.queryForObject("select account_id from account_transactions where id=?",UUID.class,transaction)).isEqualTo(account);
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void lateSeedConflictRollsBackDeletesAndUpdates() {
        assertPostSeedConflictRollsBack("promotions","d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void missingSeededAccountConflictsAfterPromotionsAndPreservesCommittedState() {
        // Account 01 has no seeded transactions, so the transaction count stays at six.
        assertPostSeedConflictRollsBack("accounts","b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void missingSeededTransactionConflictsAfterOtherCountsAndPreservesCommittedState() {
        assertPostSeedConflictRollsBack("account_transactions","c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01");
    }
    private void assertPostSeedConflictRollsBack(String table,String skippedId) {
        DisposableDatabaseGuard.verify(jdbc);
        var originalBalance=jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class);
        String originalHash=jdbc.queryForObject("select password_hash from bank_users where id=?",String.class,miguel);
        int originalFailures=jdbc.queryForObject("select failed_attempts from bank_users where id=?",Integer.class,miguel);
        int originalPromotions=jdbc.queryForObject("select count(*) from promotions",Integer.class);
        int originalTransactions=jdbc.queryForObject("select count(*) from account_transactions",Integer.class);
        int originalAudit=jdbc.queryForObject("select count(*) from auth_audit",Integer.class);
        assertThat(jdbc.queryForObject("select count(*) from accounts where owner_id in (?,?)",Integer.class,miguel,UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12"))).isEqualTo(6);
        assertThat(originalPromotions).isEqualTo(10);
        assertThat(originalTransactions).isEqualTo(6);
        UUID session=UUID.randomUUID(), event=UUID.randomUUID(), inquiry=UUID.randomUUID();
        String suffix=UUID.randomUUID().toString().replace("-","");
        String trigger="test_skip_"+suffix, function="test_skip_"+suffix;
        TransactionTemplate transaction=new TransactionTemplate(transactionManager);
        try {
            // Setup commits before reset; its rollback cannot erase the evidence.
            transaction.executeWithoutResult(status->{
                DisposableDatabaseGuard.verify(jdbc);
                jdbc.update("update accounts set available_balance=1 where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'");
                jdbc.update("update bank_users set password_hash='changed',failed_attempts=2 where id=?",miguel);
                jdbc.update("insert into auth_sessions(id,user_id,last_activity,expires_at,revoked) values (?,?,now(),now()+interval '1 hour',false)",session,miguel);
                jdbc.update("insert into auth_audit(id,user_id,event,occurred_at) values (?,?,?,now())",event,miguel,"PRIVATE-EXAMPLE");
                jdbc.update("insert into support_inquiries values (?,?, 'topic','subject','message',?,now())",inquiry,miguel,UUID.randomUUID().toString());
                jdbc.execute("create function "+function+"() returns trigger language plpgsql as $$ begin return null; end $$");
                jdbc.execute("create trigger "+trigger+" before insert on "+table+" for each row when (NEW.id = '"+skippedId+"'::uuid) execute function "+function+"()");
            });
            DisposableDatabaseGuard.verify(jdbc);
            // No enclosing transaction: the proxied service owns and rolls back its transaction.
            assertThatThrownBy(()->service.reset(actor())).isInstanceOfSatisfying(ResponseStatusException.class,
                ex->assertThat(ex.getStatusCode().value()).isEqualTo(409));
            assertThat(jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class)).isEqualByComparingTo("1");
            assertThat(jdbc.queryForObject("select password_hash from bank_users where id=?",String.class,miguel)).isEqualTo("changed");
            assertThat(jdbc.queryForObject("select failed_attempts from bank_users where id=?",Integer.class,miguel)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from promotions",Integer.class)).isEqualTo(originalPromotions);
            assertThat(jdbc.queryForObject("select count(*) from account_transactions",Integer.class)).isEqualTo(originalTransactions);
            assertThat(jdbc.queryForObject("select count(*) from auth_audit",Integer.class)).isEqualTo(originalAudit+1);
            assertThat(jdbc.queryForObject("select count(*) from auth_audit where id=?",Integer.class,event)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from auth_sessions where id=?",Integer.class,session)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from support_inquiries where id=?",Integer.class,inquiry)).isEqualTo(1);
        } finally {
            transaction.executeWithoutResult(status->{
                DisposableDatabaseGuard.verify(jdbc);
                jdbc.execute("drop trigger if exists "+trigger+" on "+table);
                jdbc.execute("drop function if exists "+function+"()");
                jdbc.update("delete from support_inquiries where id=?",inquiry);
                jdbc.update("delete from auth_sessions where id=?",session);
                jdbc.update("delete from auth_audit where id=?",event);
                jdbc.update("update accounts set available_balance=? where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",originalBalance);
                jdbc.update("update bank_users set password_hash=?,failed_attempts=? where id=?",originalHash,originalFailures,miguel);
            });
        }
    }
    private UUID externalUser() {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into bank_users(id,username,password_hash,full_name,role) values (?,?,?,'Other','CUSTOMER')",id,"other-"+id,"unchanged");
        return id;
    }
    private UUID externalAccount(UUID owner) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into accounts(id,owner_id,identifier_suffix,type,name,available_balance,status) values (?,?,'1234','DEPOSIT','External',10,'ACTIVE')",id,owner);
        return id;
    }
    private void insertExternalTransaction(UUID id,UUID account,String reference) {
        jdbc.update("insert into account_transactions(id,account_id,posted_at,description,category,direction,status,amount,reference) values (?,?,now(),'External','GENERAL','DEBIT','POSTED',1,?)",id,account,reference);
    }
    private void assertResetConflictPreservesState() {
        UUID session=UUID.randomUUID(), event=UUID.randomUUID();
        jdbc.update("update accounts set available_balance=1 where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'");
        jdbc.update("update bank_users set password_hash='changed',failed_attempts=2 where id=?",miguel);
        jdbc.update("insert into auth_sessions(id,user_id,last_activity,expires_at,revoked) values (?,?,now(),now()+interval '1 hour',false)",session,miguel);
        jdbc.update("insert into auth_audit(id,user_id,event,occurred_at) values (?,?,?,now())",event,miguel,"PRIVATE-EXAMPLE");
        int auditBefore=jdbc.queryForObject("select count(*) from auth_audit",Integer.class);
        DisposableDatabaseGuard.verify(jdbc);
        assertThatThrownBy(()->service.reset(actor())).isInstanceOfSatisfying(ResponseStatusException.class,
            ex->assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThat(jdbc.queryForObject("select available_balance from accounts where id='b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02'",java.math.BigDecimal.class)).isEqualByComparingTo("1");
        assertThat(jdbc.queryForObject("select password_hash from bank_users where id=?",String.class,miguel)).isEqualTo("changed");
        assertThat(jdbc.queryForObject("select failed_attempts from bank_users where id=?",Integer.class,miguel)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from auth_sessions where id=?",Integer.class,session)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auth_audit where id=?",Integer.class,event)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from auth_audit",Integer.class)).isEqualTo(auditBefore);
    }
    @Test void rejectsCustomerAndAuditsBoundedProbe() {
        DisposableDatabaseGuard.verify(jdbc); assertThatThrownBy(()->service.reset(new AuthService.Principal(miguel,UUID.randomUUID(),"miguel","CUSTOMER",null))).isInstanceOf(ResponseStatusException.class);
        service.setProbe(actor(),true); assertThat(service.probe(actor())).isTrue(); service.setProbe(actor(),false); assertThat(service.probe(actor())).isFalse();
        assertThat(service.auditEvents(actor())).extracting(AdminService.AuditEvent::event).contains("SIMULATED_ERROR","SIMULATION_ENABLED","SIMULATION_DISABLED");
    }
}
