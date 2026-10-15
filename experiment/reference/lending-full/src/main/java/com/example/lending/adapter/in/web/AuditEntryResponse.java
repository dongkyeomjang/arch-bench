package com.example.lending.adapter.in.web;

import com.example.lending.domain.AuditEntry;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(String action, UUID targetId, UUID memberId, Instant occurredAt) {

    static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(entry.action().name(), entry.targetId(), entry.memberId(), entry.occurredAt());
    }
}
