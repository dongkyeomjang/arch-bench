package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Loan;

class LoanMapper {

    Loan toDomain(LoanJpaEntity entity) {
        return Loan.reconstitute(entity.getId(), entity.getBookId(), entity.getMemberId(), entity.getLoanedAt(),
                entity.getDueAt(), entity.getReturnedAt());
    }

    LoanJpaEntity toEntity(Loan loan) {
        return new LoanJpaEntity(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getLoanedAt(), loan.getDueAt(),
                loan.getReturnedAt());
    }
}
