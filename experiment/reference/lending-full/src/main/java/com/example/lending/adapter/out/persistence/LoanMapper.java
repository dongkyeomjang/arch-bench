package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Loan;

class LoanMapper {

    LoanJpaEntity toEntity(Loan loan) {
        return new LoanJpaEntity(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getLoanedAt(), loan.getDueAt());
    }
}
