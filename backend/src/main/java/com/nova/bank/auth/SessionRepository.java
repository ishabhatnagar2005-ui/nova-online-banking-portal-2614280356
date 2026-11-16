package com.nova.bank.auth;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
/** Serialize session validation, activity and logout. */
@Repository public interface SessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthSession s join fetch s.user where s.id = :id")
    Optional<AuthSession> lockById(@Param("id") UUID id);
}
