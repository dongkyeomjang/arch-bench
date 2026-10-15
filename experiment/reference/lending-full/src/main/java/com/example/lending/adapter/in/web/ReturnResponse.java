package com.example.lending.adapter.in.web;

import com.example.lending.domain.Loan;

import java.time.LocalDate;
import java.util.UUID;

public record ReturnResponse(UUID loanId, LocalDate returnedAt, long overdueDays, long lateFee) {

    static ReturnResponse from(Loan loan) {
        return new ReturnResponse(loan.getId(), loan.getReturnedAt(), loan.overdueDays(), loan.lateFee());
    }
}
