package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.ListCurrentLoansUseCase.CurrentLoansPage;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CurrentLoansResponse(List<Item> items, int page, int size, long totalCount) {

    record Item(UUID loanId, UUID bookId, String title, LocalDate loanedAt, LocalDate dueAt) {
    }

    static CurrentLoansResponse from(CurrentLoansPage page) {
        List<Item> items = page.items().stream()
                .map(l -> new Item(l.loanId(), l.bookId(), l.title(), l.loanedAt(), l.dueAt()))
                .toList();
        return new CurrentLoansResponse(items, page.page(), page.size(), page.totalCount());
    }
}
