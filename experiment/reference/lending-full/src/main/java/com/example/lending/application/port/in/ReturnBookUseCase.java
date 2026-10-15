package com.example.lending.application.port.in;

import com.example.lending.domain.Loan;

import java.time.LocalDate;
import java.util.UUID;

public interface ReturnBookUseCase {

    Loan returnBook(ReturnBookCommand command);

    /**
     * returnedAt 이 없으면 오늘로 반납한다(과거 건 입력용).
     */
    record ReturnBookCommand(UUID loanId, LocalDate returnedAt) {
        public ReturnBookCommand {
            if (loanId == null) {
                throw new IllegalArgumentException("loanId is required");
            }
        }
    }
}
