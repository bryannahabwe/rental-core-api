package com.cognix.rentalcoreapi.shared.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Assign the id before insert. Done in the entity (rather than via a
     * generator) so callers can pre-set the id when a row must reference its
     * own id at insert time — e.g. an account owner whose account_owner_id
     * points back at itself under a NOT NULL self-FK.
     */
    @PrePersist
    void assignId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}