package com.nova.bank.accounts;

import com.nova.bank.auth.AuthService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read masked, owner-scoped account transactions and statements. */
@Service public class AccountService {
    private final JdbcTemplate jdbc;
    private final LocalDate demoDate;
    public AccountService(JdbcTemplate jdbc, @Value("${nova.demo-date:2026-10-14}") String demoDate) {
        this.jdbc=jdbc; this.demoDate=LocalDate.parse(demoDate);
    }
    public record Metadata(UUID id,String name,String type,String maskedIdentifier,String status,String currency,BigDecimal availableBalance,BigDecimal currentBalance,BigDecimal pending,BigDecimal interestRate,LocalDate lastStatementDate) {}
    public record Item(Instant postedAt,String description,String category,String direction,String status,BigDecimal amount,String reference) {}
    public record Result(Metadata account,LocalDate demoDate,LocalDate startDate,LocalDate endDate,long total,int page,int size,List<Item> items) {}
    private record Filter(String search,String category,String direction,LocalDate start,LocalDate end,int page,int size) {}

    /** Check authorization before querying account metadata or transactions. */
    private void authorize(AuthService.Principal principal,UUID id) {
        if (principal==null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        Integer count=principal.role().equals("ADMIN")
            ? jdbc.queryForObject("SELECT count(*) FROM accounts WHERE id=?",Integer.class,id)
            : jdbc.queryForObject("SELECT count(*) FROM accounts WHERE id=? AND owner_id=?",Integer.class,id,principal.userId());
        if (count==null || count==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account Not Available");
    }
    private Metadata metadata(UUID id) {
        return jdbc.queryForObject("SELECT id,name,type,identifier_suffix,status,available_balance,current_balance,interest_rate,last_statement_date,(SELECT coalesce(sum(amount),0) FROM account_transactions WHERE account_id=accounts.id AND status='PENDING' AND direction='DEBIT') AS pending FROM accounts WHERE id=?",
            (rs,n)->new Metadata(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("type"),"•••• "+rs.getString("identifier_suffix"),rs.getString("status"),"PHP",rs.getBigDecimal("available_balance"),rs.getBigDecimal("current_balance"),rs.getBigDecimal("pending"),rs.getBigDecimal("interest_rate"),rs.getObject("last_statement_date",LocalDate.class)),id);
    }
    private Filter filter(String search,String category,String direction,String start,String end,int page,int size) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid pagination");
        if (search!=null && search.length()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Search is too long");
        String cat=category==null || category.isBlank()?null:category.toUpperCase(Locale.ROOT);
        String dir=direction==null || direction.isBlank()?null:direction.toUpperCase(Locale.ROOT);
        if (cat!=null && !Set.of("INCOME","GROCERIES","SAVINGS","DINING","TRANSPORT","GENERAL").contains(cat)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid category");
        if (dir!=null && !Set.of("CREDIT","DEBIT").contains(dir)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid direction");
        try {
            if ((start==null)!=(end==null)) throw new IllegalArgumentException();
            LocalDate from=start==null?demoDate.minusDays(29):LocalDate.parse(start);
            LocalDate to=end==null?demoDate:LocalDate.parse(end);
            if (from.isAfter(to) || to.equals(LocalDate.MAX)) throw new IllegalArgumentException();
            return new Filter(search==null?"":search.trim(),cat,dir,from,to,page,size);
        } catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date range"); }
    }
    /** Return a deterministic page and the pending debit sum independent of filters. */
    @Transactional(readOnly=true) public Result transactions(AuthService.Principal principal,UUID id,String search,String category,String direction,String start,String end,int page,int size) {
        authorize(principal,id);
        Filter f=filter(search,category,direction,start,end,page,size);
        String where=" FROM account_transactions WHERE account_id=? AND posted_at>=? AND posted_at<? AND (position(lower(?) in lower(description))>0 OR position(lower(?) in lower(reference))>0)";
        List<Object> args=new ArrayList<>(List.of(id,Timestamp.from(f.start().atStartOfDay(ZoneOffset.UTC).toInstant()),Timestamp.from(f.end().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()),f.search(),f.search()));
        if (f.category()!=null) { where+=" AND category=?"; args.add(f.category()); }
        if (f.direction()!=null) { where+=" AND direction=?"; args.add(f.direction()); }
        Long total=jdbc.queryForObject("SELECT count(*)"+where,Long.class,args.toArray());
        args.add(f.size()); args.add(f.page()*f.size());
        List<Item> items=jdbc.query("SELECT posted_at,description,category,direction,status,amount,reference"+where+" ORDER BY posted_at DESC,id LIMIT ? OFFSET ?",(rs,n)->new Item(rs.getTimestamp("posted_at").toInstant(),rs.getString("description"),rs.getString("category"),rs.getString("direction"),rs.getString("status"),rs.getBigDecimal("amount"),rs.getString("reference")),args.toArray());
        return new Result(metadata(id),demoDate,f.start(),f.end(),total==null?0:total,f.page(),f.size(),items);
    }
    /** Produce a structured demonstration statement after the same ownership check. */
    @Transactional(readOnly=true) public String statement(AuthService.Principal principal,UUID id) {
        authorize(principal,id); Metadata a=metadata(id);
        StringBuilder out=new StringBuilder("NOVA BANK — DEMONSTRATION STATEMENT\nAccount: ").append(a.name()).append(" ").append(a.maskedIdentifier()).append("\nCurrency: PHP\nAvailable: ").append(a.availableBalance()).append("\nCurrent: ").append(a.currentBalance()).append("\nPending debits: ").append(a.pending()).append("\n\nDate (UTC) | Description | Category | Status | Direction | Amount | Reference\n");
        jdbc.query("SELECT posted_at,description,category,status,direction,amount,reference FROM account_transactions WHERE account_id=? ORDER BY posted_at DESC,id",rs->{out.append(rs.getTimestamp(1).toInstant()).append(" | ").append(rs.getString(2)).append(" | ").append(rs.getString(3)).append(" | ").append(rs.getString(4)).append(" | ").append(rs.getString(5)).append(" | ").append(rs.getBigDecimal(6)).append(" | ").append(rs.getString(7)).append('\n');},id);
        return out.append("\nSynthetic demonstration data; not a bank statement.\n").toString();
    }
    /** Return only the masked suffix used in a safe attachment name. */
    @Transactional(readOnly=true) public String statementFilename(AuthService.Principal principal,UUID id) {
        authorize(principal,id); return "statement-"+metadata(id).maskedIdentifier().substring(5)+"-demo.txt";
    }
}
