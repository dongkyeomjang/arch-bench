package com.example.lending.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ListCurrentLoansUseCase {

    CurrentLoansPage listCurrentLoans(ListCurrentLoansQuery query);

    record ListCurrentLoansQuery(UUID memberId, int page, int size) {
        public ListCurrentLoansQuery {
            if (memberId == null) {
                throw new IllegalArgumentException("memberId is required");
            }
            if (page < 0) {
                throw new IllegalArgumentException("page must be >= 0");
            }
            if (size < 1 || size > 100) {
                throw new IllegalArgumentException("size must be between 1 and 100");
            }
        }
    }

    record CurrentLoansPage(List<CurrentLoan> items, int page, int size, long totalCount) {
    }

    record CurrentLoan(UUID loanId, UUID bookId, String title, LocalDate loanedAt, LocalDate dueAt) {
    }
}
