package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Loan;

class LoanMapper {

    Loan toDomain(LoanJpaEntity entity) {
        CreditCheck creditCheck = entity.getCreditApproved() == null ? null
                : new CreditCheck(entity.getCreditApproved(), entity.getCreditScore(), entity.getCreditCheckedAt());
        return Loan.reconstitute(entity.getId(), entity.getBookId(), entity.getMemberId(), entity.getLoanedAt(),
                entity.getDueAt(), entity.getReturnedAt(), creditCheck);
    }

    LoanJpaEntity toEntity(Loan loan) {
        CreditCheck c = loan.getCreditCheck();
        return new LoanJpaEntity(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getLoanedAt(), loan.getDueAt(),
                loan.getReturnedAt(), c == null ? null : c.approved(), c == null ? null : c.score(),
                c == null ? null : c.checkedAt());
    }
}
