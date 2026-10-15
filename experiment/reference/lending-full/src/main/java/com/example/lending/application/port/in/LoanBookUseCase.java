package com.example.lending.application.port.in;

import com.example.lending.domain.Loan;

import java.time.LocalDate;
import java.util.UUID;

public interface LoanBookUseCase {

    Loan loanBook(LoanBookCommand command);

    /**
     * loanedAt 이 없으면 오늘로 대출한다(과거 건 입력용).
     */
    record LoanBookCommand(UUID bookId, UUID memberId, LocalDate loanedAt) {

        public LoanBookCommand {
            if (bookId == null || memberId == null) {
                throw new IllegalArgumentException("bookId and memberId are required");
            }
        }
    }
}
