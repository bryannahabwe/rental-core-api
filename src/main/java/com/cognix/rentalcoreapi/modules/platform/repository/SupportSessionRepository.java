package com.cognix.rentalcoreapi.modules.platform.repository;

import com.cognix.rentalcoreapi.modules.platform.model.SupportSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportSessionRepository extends JpaRepository<SupportSession, UUID> {

    List<SupportSession> findAllByOrderByCreatedAtDesc();

    List<SupportSession> findAllByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
