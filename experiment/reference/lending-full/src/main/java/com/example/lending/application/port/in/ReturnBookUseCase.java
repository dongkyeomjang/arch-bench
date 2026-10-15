package com.example.lending.application.port.in;

import com.example.lending.domain.Loan;

import java.util.UUID;

public interface ReturnBookUseCase {

    Loan returnBook(ReturnBookCommand command);

    record ReturnBookCommand(UUID loanId) {
        public ReturnBookCommand {
            if (loanId == null) {
                throw new IllegalArgumentException("loanId is required");
            }
        }
    }
}
