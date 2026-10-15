package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.GetLoanHistoryUseCase.LoanHistoryEntry;

import java.time.LocalDate;
import java.util.UUID;

public record LoanHistoryResponse(UUID loanId, UUID bookId, String title, LocalDate loanedAt, LocalDate returnedAt,
                                  Long lateFee) {

    static LoanHistoryResponse from(LoanHistoryEntry e) {
        return new LoanHistoryResponse(e.loanId(), e.bookId(), e.title(), e.loanedAt(), e.returnedAt(), e.lateFee());
    }
}
