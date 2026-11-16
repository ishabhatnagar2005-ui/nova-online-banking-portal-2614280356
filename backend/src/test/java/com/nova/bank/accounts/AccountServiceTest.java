package com.nova.bank.accounts;

import com.nova.bank.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

/** Exercise calculations and filtering against isolated PostgreSQL fixtures. */
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:postgresql://localhost:5432/nova_portfolio_test}","spring.datasource.username=${TEST_DB_USERNAME:agent}","spring.datasource.password=${TEST_DB_PASSWORD:}","nova.jwt-secret=test-only-signing-secret-at-least-32-bytes","nova.demo-mode=true","nova.demo-password=demo-only","nova.demo-date=2026-10-14"})
@org.springframework.test.context.ContextConfiguration(initializers=com.nova.bank.DisposableDatabaseGuard.class)
@org.springframework.transaction.annotation.Transactional
class AccountServiceTest {
    @Autowired AccountService service; @Autowired AccountSeeder seeder; @Autowired JdbcTemplate jdbc;
    final UUID savings=UUID.fromString("b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02");
    AuthService.Principal principal(String suffix) { return new AuthService.Principal(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a"+suffix),UUID.randomUUID(),"demo","CUSTOMER",null); }
    AccountService.Result query(String search,String category,String direction,String from,String to,int page,int size) { return service.transactions(principal("11"),savings,search,category,direction,from,to,page,size); }
    @Test void defaultsUseDemoClockAndPendingDebitsAreExact() {
        seeder.seed(); seeder.seed(); var result=query(null,null,null,null,null,0,20);
        assertThat(result.startDate()).hasToString("2026-09-15"); assertThat(result.endDate()).hasToString("2026-10-14");
        assertThat(result.total()).isEqualTo(5); assertThat(result.account().pending()).isEqualByComparingTo("185.00");
        assertThat(result.account().maskedIdentifier()).isEqualTo("•••• 9034");
        assertThat(jdbc.queryForObject("select count(*) from account_transactions",Integer.class)).isEqualTo(6);
    }
    @Test void combinedFiltersSearchReferenceAndPaginateDeterministically() {
        var result=query("882194","DINING","DEBIT","2026-10-09","2026-10-09",0,1);
        assertThat(result.total()).isEqualTo(1); assertThat(result.items().get(0).description()).isEqualTo("Makati Coffee Shop");
        assertThat(result.account().pending()).isEqualByComparingTo("185.00");
        assertThat(query(null,null,"DEBIT",null,null,1,2).items()).extracting(AccountService.Item::reference).containsExactly("REF-TX-882194","REF-TX-882195");
        assertThat(query("no match",null,null,null,null,0,20).total()).isZero();
    }
    @Test void invalidRangesPagingAndEnumsFailClosed() {
        assertThatThrownBy(()->query(null,null,null,"2026-10-10","2026-10-01",0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,null,null,"nonsense","2026-10-01",0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,null,null,"2026-10-01",null,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,null,null,null,null,-1,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,null,null,null,null,0,101)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,"INVALID",null,null,null,0,20)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->query(null,null,"INVALID",null,null,0,20)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void ownerCheckPrecedesAllReadsAndExport() {
        assertThatThrownBy(()->service.transactions(principal("12"),savings,null,null,null,null,null,0,20)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->service.statement(principal("12"),savings)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThat(service.statement(principal("11"),savings)).contains("•••• 9034","REF-TX-882194","185.00");
        assertThat(service.statementFilename(principal("11"),savings)).isEqualTo("statement-9034-demo.txt");
        var admin=new AuthService.Principal(UUID.randomUUID(),UUID.randomUUID(),"admin","ADMIN",null);
        assertThat(service.statement(admin,savings)).contains("9034");
        assertThatThrownBy(()->service.statement(admin,UUID.randomUUID())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(()->service.statement(null,savings)).isInstanceOf(org.springframework.security.authentication.AuthenticationCredentialsNotFoundException.class);
        assertThat(query(" ","dining","debit",null,null,0,20).total()).isEqualTo(1);
        assertThatThrownBy(()->query(null,null,null,null,null,Integer.MAX_VALUE,100)).isInstanceOf(ResponseStatusException.class);
    }
}
