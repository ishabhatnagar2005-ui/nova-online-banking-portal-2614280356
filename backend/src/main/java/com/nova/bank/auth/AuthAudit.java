package com.nova.bank.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
/** Record security events without credentials or account details. */
@Entity @Table(name="auth_audit")
public class AuthAudit {
    @Id UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id") BankUser user;
    @Column(nullable=false, length=32) String event;
    @Column(name="occurred_at", nullable=false) Instant occurredAt;
    protected AuthAudit() {}
    AuthAudit(BankUser user, String event, Instant now) { this.id=UUID.randomUUID(); this.user=user; this.event=event; this.occurredAt=now; }
}
