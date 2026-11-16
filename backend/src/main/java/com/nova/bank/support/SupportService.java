package com.nova.bank.support;

import com.nova.bank.auth.AuthService;
import com.nova.bank.portfolio.PortfolioService;
import com.nova.bank.promotions.PromotionService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Record simulated inquiries and answer from the signed-in customer's data. */
@Service public class SupportService {
    private static final Pattern HTML_LIKE = Pattern.compile("<\\s*(?:/?\\s*[a-zA-Z][a-zA-Z0-9:-]*(?=[\\s/>]|$)|[!?])");
    private final JdbcTemplate jdbc;
    private final PortfolioService portfolio;
    private final PromotionService promotions;
    public SupportService(JdbcTemplate jdbc, PortfolioService portfolio, PromotionService promotions) {
        this.jdbc=jdbc; this.portfolio=portfolio; this.promotions=promotions;
    }
    private UUID customer(AuthService.Principal principal) {
        if (principal==null || principal.userId()==null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        Integer count=jdbc.queryForObject("SELECT count(*) FROM bank_users WHERE id=? AND role='CUSTOMER' AND NOT locked",Integer.class,principal.userId());
        if (count==null || count!=1) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Customer access required");
        return principal.userId();
    }
    private String clean(String value,int max) {
        if (value==null || value.isBlank() || value.length()>max || HTML_LIKE.matcher(value).find()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid inquiry or message");
        return value.trim();
    }
    /** Persist an inquiry without creating a real support ticket. */
    @Transactional public Confirmation inquire(AuthService.Principal principal,String topic,String subject,String message) {
        UUID owner=customer(principal); topic=clean(topic,80); subject=clean(subject,160); message=clean(message,4000);
        UUID id=UUID.randomUUID(); String reference="NB-"+id.toString().toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO support_inquiries(id,customer_id,topic,subject,message,reference_number,created_at) VALUES (?,?,?,?,?,?,?)",id,owner,topic,subject,message,reference,Timestamp.from(Instant.now()));
        return new Confirmation(reference,"RECEIVED","24h");
    }
    private String money(BigDecimal value) { return String.format(Locale.US,"%,.2f",value); }
    /** Respond deterministically without accessing another owner's records. */
    @Transactional(readOnly=true) public Reply chat(AuthService.Principal principal,String message) {
        UUID owner=customer(principal); String text=clean(message,500).toLowerCase(Locale.ROOT);
        if (text.contains("agent") || text.contains("human") || text.contains("handoff")) return new Reply("Simulated handoff only: no live agent was contacted and no support ticket was opened.","/contact");
        if (text.contains("offer") || text.contains("promo")) {
            var catalogue=promotions.list(principal);
            String names=catalogue.promotions().stream().map(PromotionService.Promotion::title).reduce((a,b)->a+", "+b).orElse("No eligible offers on the demonstration date");
            return new Reply("Illustrative offers as of "+catalogue.demoDate()+": "+names+". These are not live offers.","/offers");
        }
        var summary=portfolio.summary(principal);
        if (text.contains("card")) {
            var card=summary.accounts().stream().filter(a->a.type().equals("CREDIT_CARD")).findFirst();
            if (card.isEmpty()) return new Reply("You have no active card. The emergency number (+63 2) 8000 0001 is a synthetic demonstration fixture, not a live service.","/support");
            var c=card.get(); return new Reply(c.name()+" "+c.maskedIdentifier()+": amount due PHP "+money(c.currentBalance())+", available credit PHP "+money(c.availableCredit())+". Example payment due 28 Oct 2026 is illustrative only. For a lost card, the synthetic emergency number (+63 2) 8000 0001 is not a live service.","/support");
        }
        if (text.contains("balance") || text.contains("account") || text.contains("transaction") || text.contains("salary")) {
            var latest=jdbc.query("SELECT t.description,t.amount,t.direction,t.posted_at FROM account_transactions t JOIN accounts a ON a.id=t.account_id WHERE a.owner_id=? AND a.status='ACTIVE' ORDER BY t.posted_at DESC,t.id LIMIT 1",(rs,n)->rs.getString("description")+" "+rs.getString("direction")+" PHP "+money(rs.getBigDecimal("amount"))+" on "+DateTimeFormatter.ofPattern("d MMM uuuu",Locale.ENGLISH).withZone(ZoneOffset.UTC).format(rs.getTimestamp("posted_at").toInstant()),owner);
            return new Reply("Your deposit relationship balance is PHP "+money(summary.relationshipBalance())+"."+(latest.isEmpty()?" No transactions are available.":" Latest transaction: "+latest.get(0)+".")+" Synthetic demonstration data.","/accounts");
        }
        return new Reply("I am a scripted demonstration assistant. Ask about account balance, transactions, card help, offers, or a simulated agent handoff. Please do not share sensitive information.","/support");
    }
    public record Confirmation(String referenceNumber,String status,String estimatedResponse) {}
    public record Reply(String message,String actionPath) {}
}
