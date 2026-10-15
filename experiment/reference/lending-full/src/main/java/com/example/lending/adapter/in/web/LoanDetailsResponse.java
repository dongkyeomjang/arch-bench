package com.example.lending.adapter.in.web;

import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Loan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LoanDetailsResponse(UUID loanId, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt,
                                  LocalDate returnedAt, CreditCheckView creditCheck) {

    record CreditCheckView(boolean approved, int score, Instant checkedAt) {
    }

    static LoanDetailsResponse from(Loan loan) {
        CreditCheck c = loan.getCreditCheck();
        return new LoanDetailsResponse(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getLoanedAt(),
                loan.getDueAt(), loan.getReturnedAt(),
                c == null ? null : new CreditCheckView(c.approved(), c.score(), c.checkedAt()));
    }
}
