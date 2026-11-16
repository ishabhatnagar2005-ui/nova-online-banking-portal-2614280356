package com.nova.bank.auth;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Authenticate identities and enforce database-backed session lifetime. */
@Service public class AuthService {
    private static final String INVALID="Invalid User ID or password. Please try again.";
    private static final String LOCKED="Account locked due to multiple failed login attempts. Use the Facilitator Reset to unlock.";
    private final UserRepository users; private final SessionRepository sessions; private final AuditRepository audit;
    private final PasswordEncoder passwords; private final JwtService jwt; private final long tokenSeconds; private final long idleSeconds;
    public AuthService(UserRepository users, SessionRepository sessions, AuditRepository audit, PasswordEncoder passwords, JwtService jwt,
                       @Value("${nova.token-seconds}") long tokenSeconds, @Value("${nova.idle-seconds}") long idleSeconds) {
        this.users=users; this.sessions=sessions; this.audit=audit; this.passwords=passwords; this.jwt=jwt; this.tokenSeconds=tokenSeconds; this.idleSeconds=idleSeconds;
        if (tokenSeconds < 1 || idleSeconds < 1) throw new IllegalArgumentException("Session durations must be positive");
    }
    /** Persist failed attempts even when the caller receives an error. */
    @Transactional(noRollbackFor=AuthFailure.class)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request) {
        Instant now=Instant.now();
        BankUser user=users.lockByUsername(request.username().trim()).orElse(null);
        if (user==null) { audit.save(new AuthAudit(null,"LOGIN_FAILED",now)); throw new AuthFailure(401,INVALID); }
        if (user.locked) { audit.save(new AuthAudit(user,"LOGIN_LOCKED",now)); throw new AuthFailure(423,LOCKED); }
        if (!passwords.matches(request.password(),user.passwordHash)) {
            user.failedAttempts++; user.locked=user.failedAttempts>=3; audit.save(new AuthAudit(user,user.locked ? "LOGIN_LOCKED" : "LOGIN_FAILED",now));
            throw new AuthFailure(user.locked ? 423 : 401,user.locked ? LOCKED : INVALID);
        }
        Instant previous=user.lastLogin; user.failedAttempts=0; user.lastLogin=now;
        UUID sessionId=UUID.randomUUID(); Instant expires=now.plusSeconds(tokenSeconds);
        sessions.save(new AuthSession(sessionId,user,now,expires,previous)); audit.save(new AuthAudit(user,"LOGIN_SUCCESS",now));
        return new AuthDtos.LoginResponse(jwt.issue(user.id,sessionId,now,expires),"Bearer",tokenSeconds,new AuthDtos.UserView(user.id,user.username,user.fullName,user.role,previous));
    }
    /** Validate and extend active sessions atomically; reject expired or revoked sessions. */
    @Transactional
    public Optional<Principal> authenticate(JwtService.TokenIds ids) {
        Instant now=Instant.now();
        var s=sessions.lockById(ids.sessionId()).orElse(null);
        if (s==null || !s.user.id.equals(ids.userId()) || s.revoked || s.user.locked) return Optional.empty();
        if (!now.isBefore(s.expiresAt) || !now.isBefore(s.lastActivity.plusSeconds(idleSeconds))) { s.revoked=true; return Optional.empty(); }
        s.lastActivity=now;
        return Optional.of(new Principal(s.user.id,s.id,s.user.username,s.user.role,s.previousLogin));
    }
    /** Revoke the current session and record the logout. */
    @Transactional public void logout(Principal principal) {
        var s=sessions.lockById(principal.sessionId()).orElseThrow(() -> new AuthFailure(401,"Authentication required."));
        s.revoked=true; audit.save(new AuthAudit(s.user,"LOGOUT",Instant.now()));
    }
    public record Principal(UUID userId, UUID sessionId, String username, String role, Instant previousLogin) {}
}
