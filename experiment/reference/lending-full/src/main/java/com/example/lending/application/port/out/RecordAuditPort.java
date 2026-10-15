package com.example.lending.application.port.out;

import com.example.lending.domain.AuditEntry;

public interface RecordAuditPort {

    void record(AuditEntry entry);
}
