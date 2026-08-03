package com.cognix.rentalcoreapi.modules.auth.repository;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    // finds by phone or email — used for flexible login
    @Query("SELECT u FROM User u WHERE u.phoneNumber = :username OR u.email = :username")
    Optional<User> findByUsername(String username);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    // ── Account-scoped (user management) ──
    List<User> findAllByAccountOwnerIdOrderByCreatedAtAsc(UUID accountOwnerId);

    Optional<User> findByIdAndAccountOwnerId(UUID id, UUID accountOwnerId);

    long countByAccountOwnerIdAndRoleAndStatus(UUID accountOwnerId, UserRole role, UserStatus status);

    long countByAccountOwnerId(UUID accountOwnerId);

    // ── Platform support (cross-account) ──

    /**
     * Every account owner, optionally filtered by name, email or phone.
     *
     * <p><strong>This is the only query in the application that deliberately
     * crosses the account boundary.</strong> It exists so Cognix staff can find
     * the account to open a support session against, and returns owners only —
     * an owner is the user whose id equals their own {@code accountOwnerId}.
     * Callers must be platform-authenticated; nothing under {@code /users}
     * should ever reach it.
     */
    @Query("SELECT u FROM User u WHERE u.id = u.accountOwnerId AND ("
            + ":q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) "
            + "OR u.phoneNumber LIKE CONCAT('%', :q, '%')) "
            + "ORDER BY u.createdAt DESC")
    List<User> searchAccountOwners(@Param("q") String q);
}