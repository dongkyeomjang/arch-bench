package com.example.lending.application.port.out;

import com.example.lending.domain.Loan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadLoanPort {

    Optional<Loan> loadLoan(UUID loanId);

    long countActiveLoans(UUID memberId);

    List<Loan> loadLoansOfMember(UUID memberId);

    /**
     * 대출일이 from~to(양끝 포함)인 대출.
     */
    List<Loan> loadLoansLoanedBetween(LocalDate from, LocalDate to);

    /**
     * 반납일이 from~to(양끝 포함)인 대출.
     */
    List<Loan> loadLoansReturnedBetween(LocalDate from, LocalDate to);

    /**
     * 회원의 반납되지 않은 대출 중 page 번째(0부터) size 개와 전체 건수. 정렬은 대출일 오름차순.
     */
    LoanSlice loadActiveLoans(UUID memberId, int page, int size);

    record LoanSlice(List<Loan> loans, long totalCount) {
    }
}
