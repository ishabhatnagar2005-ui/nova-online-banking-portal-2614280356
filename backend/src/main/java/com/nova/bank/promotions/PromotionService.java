package com.nova.bank.promotions;

import com.nova.bank.auth.AuthService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Enforce campaign eligibility against the persisted customer and demo date. */
@Service public class PromotionService {
    public static final String MESSAGE="Interest recorded for demonstration only. No real application or credit check was submitted.";
    private final JdbcTemplate jdbc;
    private final LocalDate demoDate;
    public PromotionService(JdbcTemplate jdbc,@Value("${nova.demo-date}") LocalDate demoDate) { this.jdbc=jdbc; this.demoDate=demoDate; }
    private static final String ELIGIBLE=" FROM promotions p JOIN bank_users u ON u.id=? AND u.role='CUSTOMER' AND NOT u.locked WHERE p.active AND p.valid_until>=? AND (p.target_segment='ALL' OR (p.target_segment='MIGUEL' AND u.username='miguel') OR (p.target_segment='ROSA' AND u.username='rosa'))";
    private UUID customer(AuthService.Principal principal) {
        if (principal==null || principal.userId()==null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        return principal.userId();
    }
    /** Return only eligible, active campaigns at the disclosed demonstration date. */
    @Transactional(readOnly=true) public Catalogue list(AuthService.Principal principal) {
        var rows=jdbc.query("SELECT p.id,p.promotion_key,p.title,p.category,p.target_segment,p.benefit,p.terms,p.eligibility,p.valid_until"+ELIGIBLE+" ORDER BY p.promotion_key",(rs,n)->new Promotion(rs.getObject("id",UUID.class),rs.getString("promotion_key"),rs.getString("title"),rs.getString("category"),!rs.getString("target_segment").equals("ALL"),rs.getString("benefit"),rs.getString("terms"),rs.getString("eligibility"),rs.getObject("valid_until",LocalDate.class)),customer(principal),demoDate);
        return new Catalogue(demoDate,rows);
    }
    /** Atomically record interest only while the campaign remains eligible. */
    @Transactional public Confirmation expressInterest(AuthService.Principal principal,UUID promotionId) {
        UUID customer=customer(principal);
        int inserted=jdbc.update("INSERT INTO promotion_interests(id,customer_id,promotion_id,registered_at) SELECT ?,u.id,p.id,?"+ELIGIBLE+" AND p.id=?",UUID.randomUUID(),java.sql.Timestamp.from(Instant.now()),customer,demoDate,promotionId);
        if (inserted!=1) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Offer not available");
        return new Confirmation(MESSAGE);
    }
    public record Promotion(UUID id,String key,String title,String category,boolean personalized,String benefit,String terms,String eligibility,LocalDate validUntil) {}
    public record Catalogue(LocalDate demoDate,List<Promotion> promotions) {}
    public record Confirmation(String message) {}
}
