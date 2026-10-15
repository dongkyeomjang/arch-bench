package com.example.lending.application.port.in;

import com.example.lending.domain.AuditEntry;

import java.util.List;
import java.util.UUID;

public interface ListAuditEntriesUseCase {

    List<AuditEntry> listAuditEntries(UUID targetId);
}
