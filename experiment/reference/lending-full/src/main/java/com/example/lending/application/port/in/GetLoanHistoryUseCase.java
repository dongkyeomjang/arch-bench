package com.example.lending.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface GetLoanHistoryUseCase {

    /**
     * 회원의 모든 대출(반납 포함), 대출일 최신순.
     */
    List<LoanHistoryEntry> getLoanHistory(UUID memberId);

    /**
     * returnedAt·lateFee 는 반납 전이면 null.
     */
    record LoanHistoryEntry(UUID loanId, UUID bookId, String title, LocalDate loanedAt, LocalDate returnedAt,
                            Long lateFee) {
    }
}
