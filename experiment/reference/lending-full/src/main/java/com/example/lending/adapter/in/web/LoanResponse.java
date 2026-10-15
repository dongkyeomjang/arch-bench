package com.example.lending.adapter.in.web;

import com.example.lending.domain.Loan;

import java.time.LocalDate;
import java.util.UUID;

public record LoanResponse(UUID loanId, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt) {

    static LoanResponse from(Loan loan) {
        return new LoanResponse(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getLoanedAt(), loan.getDueAt());
    }
}
