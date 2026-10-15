package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.AuditAction;
import com.example.lending.domain.AuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
public class AuditLogJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private UUID targetId;

    private UUID memberId;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditLogJpaEntity() {
    }

    static AuditLogJpaEntity from(AuditEntry entry) {
        AuditLogJpaEntity e = new AuditLogJpaEntity();
        e.id = entry.id();
        e.action = entry.action().name();
        e.targetId = entry.targetId();
        e.memberId = entry.memberId();
        e.occurredAt = entry.occurredAt();
        return e;
    }

    AuditEntry toDomain() {
        return new AuditEntry(id, AuditAction.valueOf(action), targetId, memberId, occurredAt);
    }
}
