package com.example.lending.adapter.out.persistence;

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

    public LoanJpaEntity(UUID id, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt,
                         LocalDate returnedAt, Boolean creditApproved, Integer creditScore, Instant creditCheckedAt) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.loanedAt = loanedAt;
        this.dueAt = dueAt;
        this.returnedAt = returnedAt;
        this.creditApproved = creditApproved;
        this.creditScore = creditScore;
        this.creditCheckedAt = creditCheckedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookId() {
        return bookId;
    }

    public UUID getMemberId() {
        return memberId;
    }

    public LocalDate getLoanedAt() {
        return loanedAt;
    }

    public LocalDate getDueAt() {
        return dueAt;
    }

    public LocalDate getReturnedAt() {
        return returnedAt;
    }

    public Boolean getCreditApproved() {
        return creditApproved;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public Instant getCreditCheckedAt() {
        return creditCheckedAt;
    }
}
