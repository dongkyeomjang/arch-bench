package com.example.lending.application.port.out;

import com.example.lending.domain.Loan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadLoanPort {

    Optional<Loan> loadLoan(UUID loanId);

    long countActiveLoans(UUID memberId);

    /**
     * 회원의 반납되지 않은 대출 중 page 번째(0부터) size 개와 전체 건수. 정렬은 대출일 오름차순.
     */
    LoanSlice loadActiveLoans(UUID memberId, int page, int size);

    record LoanSlice(List<Loan> loans, long totalCount) {
    }
}
