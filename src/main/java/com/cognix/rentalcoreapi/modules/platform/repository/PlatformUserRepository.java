package com.cognix.rentalcoreapi.modules.platform.repository;

import com.cognix.rentalcoreapi.modules.platform.model.PlatformUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {

    Optional<PlatformUser> findByEmailIgnoreCase(String email);
}
