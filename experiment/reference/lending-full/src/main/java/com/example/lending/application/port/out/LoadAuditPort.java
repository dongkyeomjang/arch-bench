package com.example.lending.application.port.out;

import com.example.lending.domain.AuditEntry;

import java.util.List;
import java.util.UUID;

public interface LoadAuditPort {

    /**
     * 대상의 감사 기록, 시간순.
     */
    List<AuditEntry> loadByTarget(UUID targetId);
}
