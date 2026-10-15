package com.example.lending.application.service;

import com.example.lending.application.port.in.ListAuditEntriesUseCase;
import com.example.lending.application.port.out.LoadAuditPort;
import com.example.lending.domain.AuditEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListAuditEntriesService implements ListAuditEntriesUseCase {

    private final LoadAuditPort loadAuditPort;

    public ListAuditEntriesService(LoadAuditPort loadAuditPort) {
        this.loadAuditPort = loadAuditPort;
    }

    @Override
    public List<AuditEntry> listAuditEntries(UUID targetId) {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        return loadAuditPort.loadByTarget(targetId);
    }
}
