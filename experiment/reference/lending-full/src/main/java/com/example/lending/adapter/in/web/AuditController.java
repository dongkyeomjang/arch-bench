package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.ListAuditEntriesUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final ListAuditEntriesUseCase listAuditEntriesUseCase;

    public AuditController(ListAuditEntriesUseCase listAuditEntriesUseCase) {
        this.listAuditEntriesUseCase = listAuditEntriesUseCase;
    }

    @GetMapping
    public List<AuditEntryResponse> listAuditEntries(@RequestParam UUID targetId) {
        return listAuditEntriesUseCase.listAuditEntries(targetId).stream().map(AuditEntryResponse::from).toList();
    }
}
