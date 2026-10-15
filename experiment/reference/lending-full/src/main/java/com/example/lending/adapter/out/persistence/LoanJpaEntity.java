package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.CreditCheck;
import com.example.lending.domain.Loan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "loan")
public class LoanJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID bookId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private LocalDate loanedAt;

    @Column(nullable = false)
    private LocalDate dueAt;

    private LocalDate returnedAt;

    private Boolean creditApproved;

    private Integer creditScore;

    private Instant creditCheckedAt;

    protected LoanJpaEntity() {
    }

    static LoanJpaEntity from(Loan loan) {
        LoanJpaEntity e = new LoanJpaEntity();
        e.id = loan.getId();
        e.bookId = loan.getBookId();
        e.memberId = loan.getMemberId();
        e.loanedAt = loan.getLoanedAt();
        e.dueAt = loan.getDueAt();
        e.returnedAt = loan.getReturnedAt();
        CreditCheck c = loan.getCreditCheck();
        if (c != null) {
            e.creditApproved = c.approved();
            e.creditScore = c.score();
            e.creditCheckedAt = c.checkedAt();
        }
        return e;
    }

    Loan toDomain() {
        CreditCheck creditCheck = creditApproved == null ? null
                : new CreditCheck(creditApproved, creditScore, creditCheckedAt);
        return Loan.reconstitute(id, bookId, memberId, loanedAt, dueAt, returnedAt, creditCheck);
    }
}
