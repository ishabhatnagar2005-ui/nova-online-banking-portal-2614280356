package com.nova.bank.auth;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
/** Find users and serialize login attempts per identity. */
@Repository public interface UserRepository extends JpaRepository<BankUser, UUID> {
    Optional<BankUser> findByUsername(String username);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from BankUser u where u.username = :username")
    Optional<BankUser> lockByUsername(@Param("username") String username);
}
