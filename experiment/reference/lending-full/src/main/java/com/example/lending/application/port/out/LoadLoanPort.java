package com.example.lending.application.port.out;

import com.example.lending.domain.Loan;

import java.util.Optional;
import java.util.UUID;

public interface LoadLoanPort {

    Optional<Loan> loadLoan(UUID loanId);

    long countActiveLoans(UUID memberId);
}
