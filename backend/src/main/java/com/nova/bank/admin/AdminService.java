package com.nova.bank.admin;

import com.nova.bank.accounts.AccountSeeder;
import com.nova.bank.auth.AuthService;
import com.nova.bank.portfolio.PortfolioSeeder;
import com.nova.bank.promotions.PromotionSeeder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Manage explicitly scoped demonstration fixtures and facilitator controls. */
@Service
public class AdminService {
    private static final UUID MIGUEL=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11");
    private static final UUID ROSA=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
    private static final UUID ADMIN=UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13");
    private final JdbcTemplate jdbc; private final PortfolioSeeder portfolio; private final AccountSeeder transactions;
    private final PromotionSeeder promotions; private final PasswordEncoder encoder; private final boolean demo; private final String password;
    public AdminService(JdbcTemplate jdbc, PortfolioSeeder portfolio, AccountSeeder transactions, PromotionSeeder promotions, PasswordEncoder encoder,
                        @Value("${nova.demo-mode}") boolean demo, @Value("${nova.demo-password}") String password) {
        this.jdbc=jdbc; this.portfolio=portfolio; this.transactions=transactions; this.promotions=promotions; this.encoder=encoder; this.demo=demo; this.password=password;
    }
    private void authorize(AuthService.Principal actor) {
        if (actor==null || !"ADMIN".equals(actor.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator required.");
        if (!demo) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Demonstration mode is disabled.");
    }
    private int count(String sql,Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    private void audit(AuthService.Principal actor,String event) {
        jdbc.update("insert into auth_audit(id,user_id,event,occurred_at) values (?,?,?,now())",UUID.randomUUID(),actor.userId(),event);
    }
    private void conflict(boolean condition) {
        if (condition) throw new ResponseStatusException(HttpStatus.CONFLICT,"Demo fixture conflict; reset was not applied.");
    }
    /** Restore only verified demo identities and their fixtures in one transaction. */
    @Transactional
    public void reset(AuthService.Principal actor) {
        authorize(actor);
        if (password.isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Demo password is not configured.");
        // Prevent new references or ownership changes between checks and restoration.
        jdbc.execute("LOCK TABLE bank_users, auth_sessions, accounts, account_transactions, promotions, promotion_interests, support_inquiries IN SHARE ROW EXCLUSIVE MODE");
        conflict(count("select count(*) from bank_users where (id=? and username='miguel' and role='CUSTOMER') or (id=? and username='rosa' and role='CUSTOMER') or (id=? and username='admin' and role='ADMIN')",MIGUEL,ROSA,ADMIN)!=3);
        String ids="('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a04','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a05','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a06','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a07','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a08','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a09','d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a10')";
        conflict(count("select count(*) from promotion_interests where promotion_id in "+ids+" and customer_id not in (?,?)",MIGUEL,ROSA)>0);
        conflict(count("select count(*) from promotions where id in "+ids+" and (id::text, promotion_key) not in (('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01','payday-ipon-boost'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02','payday-ipon-bonus'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03','peso-savings-bonus'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a04','cashback-week'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a05','dining-cashback-5'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a06','personal-loan-offer'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a07','home-loan-fee-waiver'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a08','term-deposit-promo'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a09','referral-reward'),('d1eebc99-9c0b-4ef8-bb6d-6bb9bd380a10','new-year-cashback-expired'))")>0);
        conflict(count("select count(*) from promotions where promotion_key in ('payday-ipon-boost','payday-ipon-bonus','peso-savings-bonus','cashback-week','dining-cashback-5','personal-loan-offer','home-loan-fee-waiver','term-deposit-promo','referral-reward','new-year-cashback-expired') and id not in "+ids)>0);
        conflict(count("select count(*) from accounts where id::text like 'b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a0%' and id in ('b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01','b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02','b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03','b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a04','b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a05','b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a06') and owner_id not in (?,?)",MIGUEL,ROSA)>0);
        conflict(count("select count(*) from account_transactions t join accounts a on a.id=t.account_id where (t.id in ('c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a01','c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a02','c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a03','c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a04','c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a05','c1eebc99-9c0b-4ef8-bb6d-6bb9bd380a06') or t.reference in ('REF-TX-882191','REF-TX-882192','REF-TX-882193','REF-TX-882194','REF-TX-882195','REF-TX-882196')) and a.owner_id not in (?,?)",MIGUEL,ROSA)>0);
        jdbc.update("delete from promotion_interests where customer_id in (?,?)",MIGUEL,ROSA);
        jdbc.update("delete from support_inquiries where customer_id in (?,?)",MIGUEL,ROSA);
        jdbc.update("delete from auth_sessions where user_id in (?,?) or (user_id=? and id<>?)",MIGUEL,ROSA,ADMIN,actor.sessionId());
        jdbc.update("delete from accounts where owner_id in (?,?)",MIGUEL,ROSA);
        jdbc.update("delete from promotions where id in "+ids);
        String hash=encoder.encode(password);
        jdbc.update("update bank_users set password_hash=?,failed_attempts=0,locked=false,last_login=null,full_name=case id when ? then 'Miguel De La Cruz' when ? then 'Rosa Santos' else 'Facilitator' end where id in (?,?,?)",hash,MIGUEL,ROSA,MIGUEL,ROSA,ADMIN);
        portfolio.seed(); transactions.seed(); promotions.seed();
        conflict(count("select count(*) from promotions where id in "+ids)!=10);
        conflict(count("select count(*) from accounts where owner_id in (?,?)",MIGUEL,ROSA)!=6);
        conflict(count("select count(*) from account_transactions t join accounts a on a.id=t.account_id where a.owner_id in (?,?)",MIGUEL,ROSA)!=6);
        jdbc.update("update demo_control set probe_failure=false where id=1");
        audit(actor,"DEMO_RESET");
    }
    /** Return recent event types and timestamps without identity or free text. */
    @Transactional(readOnly=true) public List<AuditEvent> auditEvents(AuthService.Principal actor) {
        authorize(actor);
        return jdbc.query("select event,occurred_at from auth_audit where event in ('LOGIN_FAILED','LOGIN_LOCKED','LOGIN_SUCCESS','LOGOUT','DEMO_RESET','SIMULATION_ENABLED','SIMULATION_DISABLED','SIMULATED_ERROR','SIMULATION_PROBE') order by occurred_at desc, id desc limit 100",(rs,n)->new AuditEvent(rs.getString(1),rs.getTimestamp(2).toInstant()));
    }
    /** Enable or disable the administrator-only simulated probe. */
    @Transactional public void setProbe(AuthService.Principal actor,boolean enabled) {
        authorize(actor); jdbc.update("update demo_control set probe_failure=? where id=1",enabled); audit(actor,enabled?"SIMULATION_ENABLED":"SIMULATION_DISABLED");
    }
    /** Test only the dedicated probe; persist the audit before returning its result. */
    @Transactional public boolean probe(AuthService.Principal actor) {
        authorize(actor); boolean fail=Boolean.TRUE.equals(jdbc.queryForObject("select probe_failure from demo_control where id=1",Boolean.class));
        audit(actor,fail?"SIMULATED_ERROR":"SIMULATION_PROBE"); return fail;
    }
    @Transactional(readOnly=true) public boolean probeEnabled(AuthService.Principal actor) {
        authorize(actor); return Boolean.TRUE.equals(jdbc.queryForObject("select probe_failure from demo_control where id=1",Boolean.class));
    }
    public record AuditEvent(String event,Instant occurredAt) {}
}
