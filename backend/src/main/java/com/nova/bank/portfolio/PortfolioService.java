package com.nova.bank.portfolio;
import com.nova.bank.auth.AuthService;
import com.nova.bank.auth.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Build a masked portfolio for the authenticated identity. */
@Service public class PortfolioService {
    private final AccountRepository accounts; private final UserRepository users;
    public PortfolioService(AccountRepository accounts, UserRepository users) { this.accounts=accounts; this.users=users; }
    /** Sum only active deposit balances and calculate available card credit. */
    @Transactional(readOnly=true) public Summary summary(AuthService.Principal principal) {
        if (principal==null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        var user=users.findById(principal.userId()).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authentication required"));
        var owned=accounts.findByOwnerIdAndStatusOrderById(principal.userId(),"ACTIVE");
        BigDecimal total=owned.stream().filter(a -> a.type.equals("DEPOSIT")).map(a -> a.availableBalance).reduce(new BigDecimal("0.00"),BigDecimal::add);
        var cards=owned.stream().map(a -> new AccountView(a.id,a.name,a.type,"•••• "+a.identifierSuffix,a.status,a.availableBalance,a.currentBalance,a.creditLimit,
            a.type.equals("CREDIT_CARD") ? a.creditLimit.subtract(a.currentBalance) : null,a.interestRate,a.lastStatementDate)).toList();
        return new Summary(user.getFullName(),"Welcome, "+user.getFullName(),principal.previousLogin(),total,cards);
    }
    public record Summary(String customer, String greeting, Instant lastLogin, BigDecimal relationshipBalance, List<AccountView> accounts) {}
    public record AccountView(UUID id, String name, String type, String maskedIdentifier, String status, BigDecimal availableBalance, BigDecimal currentBalance, BigDecimal creditLimit, BigDecimal availableCredit, BigDecimal interestRate, LocalDate lastStatementDate) {}
}
