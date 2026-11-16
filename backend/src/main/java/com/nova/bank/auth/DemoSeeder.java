package com.nova.bank.auth;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** Seed synthetic identities only when explicitly enabled. */
@Component public class DemoSeeder implements CommandLineRunner {
    private final UserRepository users; private final PasswordEncoder encoder; private final boolean enabled; private final String password; private final com.nova.bank.portfolio.PortfolioSeeder portfolio;
    public DemoSeeder(UserRepository users, PasswordEncoder encoder, com.nova.bank.portfolio.PortfolioSeeder portfolio, @Value("${nova.demo-mode}") boolean enabled, @Value("${nova.demo-password}") String password) {
        this.users=users; this.encoder=encoder; this.enabled=enabled; this.password=password; this.portfolio=portfolio;
    }
    @Override @Transactional public void run(String... args) {
        if (!enabled) return;
        if (password.isBlank()) throw new IllegalStateException("DEMO_PASSWORD is required in demo mode");
        seed("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11","miguel","Miguel De La Cruz","CUSTOMER");
        seed("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12","rosa","Rosa Santos","CUSTOMER");
        seed("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13","admin","Facilitator","ADMIN");
        users.flush(); portfolio.seed();
    }
    private void seed(String id,String name,String fullName,String role) {
        if (users.findByUsername(name).isEmpty()) users.save(new BankUser(UUID.fromString(id),name,encoder.encode(password),fullName,role));
    }
}
