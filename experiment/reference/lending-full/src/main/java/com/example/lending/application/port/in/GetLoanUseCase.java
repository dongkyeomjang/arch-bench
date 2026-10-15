package com.example.lending.application.port.in;

import com.example.lending.domain.Loan;

import java.util.UUID;

public interface GetLoanUseCase {

    Loan getLoan(UUID loanId);
}
