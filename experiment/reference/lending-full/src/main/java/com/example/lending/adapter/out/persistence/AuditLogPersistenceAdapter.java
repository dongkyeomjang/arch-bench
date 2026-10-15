package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadAuditPort;
import com.example.lending.application.port.out.RecordAuditPort;
import com.example.lending.domain.AuditEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class AuditLogPersistenceAdapter implements RecordAuditPort, LoadAuditPort {

    private final AuditLogJpaRepository auditLogJpaRepository;

    public AuditLogPersistenceAdapter(AuditLogJpaRepository auditLogJpaRepository) {
        this.auditLogJpaRepository = auditLogJpaRepository;
    }

    @Override
    public void record(AuditEntry entry) {
        auditLogJpaRepository.save(AuditLogJpaEntity.from(entry));
    }

    @Override
    public List<AuditEntry> loadByTarget(UUID targetId) {
        return auditLogJpaRepository.findByTargetIdOrderByOccurredAtAsc(targetId).stream()
                .map(AuditLogJpaEntity::toDomain)
                .toList();
    }
}
