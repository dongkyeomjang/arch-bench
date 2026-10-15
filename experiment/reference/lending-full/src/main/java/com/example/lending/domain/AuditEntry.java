package com.example.lending.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 상태가 바뀐 작업 한 건의 감사 기록. 누가(memberId, 없으면 null), 언제, 무슨 작업을, 어떤 대상(targetId)에.
 * 대상: 대출·반납 = 대출 ID, 폐기 = 책 ID, 정지·해제 = 회원 ID.
 */
public record AuditEntry(UUID id, AuditAction action, UUID targetId, UUID memberId, Instant occurredAt) {

    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static AuditEntry of(AuditAction action, UUID targetId, UUID memberId, Instant occurredAt) {
        return new AuditEntry(UUID.randomUUID(), action, targetId, memberId, occurredAt);
    }
}
