package com.cognix.rentalcoreapi.modules.auth.repository;

import com.cognix.rentalcoreapi.modules.auth.model.User;
import com.cognix.rentalcoreapi.modules.auth.model.UserRole;
import com.cognix.rentalcoreapi.modules.auth.model.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}