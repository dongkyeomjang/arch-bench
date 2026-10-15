package com.example.lending.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    protected LoanJpaEntity() {
    }

    public LoanJpaEntity(UUID id, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.loanedAt = loanedAt;
        this.dueAt = dueAt;
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
}
