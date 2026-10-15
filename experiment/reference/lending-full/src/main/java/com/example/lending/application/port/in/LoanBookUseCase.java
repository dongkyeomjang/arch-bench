package com.example.lending.application.port.in;

import com.example.lending.domain.Loan;

import java.util.UUID;

public interface LoanBookUseCase {

    Loan loanBook(LoanBookCommand command);

    record LoanBookCommand(UUID bookId, UUID memberId) {

        public LoanBookCommand {
            if (bookId == null || memberId == null) {
                throw new IllegalArgumentException("bookId and memberId are required");
            }
        }
    }
}
