package com.nova.bank.auth;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/** Persist minimal authentication audit events. */
@Repository public interface AuditRepository extends JpaRepository<AuthAudit, UUID> {}
