package com.nova.bank.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Persist revocation and inactivity independently of a JWT. */
@Entity @Table(name="auth_sessions")
public class AuthSession {
    @Id UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="user_id", nullable=false) BankUser user;
    @Column(name="last_activity", nullable=false) Instant lastActivity;
    @Column(name="expires_at", nullable=false) Instant expiresAt;
    @Column(nullable=false) boolean revoked;
    @Column(name="previous_login") Instant previousLogin;
    protected AuthSession() {}
    AuthSession(UUID id, BankUser user, Instant now, Instant expiresAt, Instant previousLogin) { this.id=id; this.user=user; this.lastActivity=now; this.expiresAt=expiresAt; this.previousLogin=previousLogin; }
}
