package com.cognix.rentalcoreapi.modules.audit.repository;

import com.cognix.rentalcoreapi.modules.audit.model.AuditAction;
import com.cognix.rentalcoreapi.modules.audit.model.AuditModule;
import com.cognix.rentalcoreapi.modules.audit.model.AuditTrail;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface AuditTrailRepository extends JpaRepository<AuditTrail, UUID> {

    @Query("SELECT a FROM AuditTrail a WHERE a.accountId = :accountId AND " +
            "(:module IS NULL OR a.module = :module) AND " +
            "(:action IS NULL OR a.action = :action) AND " +
            "(:search IS NULL OR " +
            " LOWER(a.statement) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR " +
            " LOWER(a.actingUserName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) AND " +
            "a.createdAt >= :from AND a.createdAt <= :to " +
            "ORDER BY a.createdAt DESC")
    Page<AuditTrail> findFeed(
            @Param("accountId") UUID accountId,
            @Param("module") AuditModule module,
            @Param("action") AuditAction action,
            @Param("search") String search,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
