package com.nova.bank.portfolio;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
/** Retrieve only accounts belonging to the authenticated identity. */
@Repository public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByOwnerIdAndStatusOrderById(UUID ownerId, String status);
}
