package com.nova.bank.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Store an identity without exposing its password hash to clients. */
@Entity @Table(name="bank_users")
public class BankUser {
    @Id UUID id;
    @Column(nullable=false, unique=true, length=64) String username;
    @Column(name="password_hash", nullable=false, length=100) String passwordHash;
    @Column(name="full_name", nullable=false, length=120) String fullName;
    @Column(nullable=false, length=16) String role;
    @Column(name="failed_attempts", nullable=false) int failedAttempts;
    @Column(nullable=false) boolean locked;
    @Column(name="last_login") Instant lastLogin;
    protected BankUser() {}
    BankUser(UUID id, String username, String hash, String fullName, String role) {
        this.id=id; this.username=username; this.passwordHash=hash; this.fullName=fullName; this.role=role;
    }
}
