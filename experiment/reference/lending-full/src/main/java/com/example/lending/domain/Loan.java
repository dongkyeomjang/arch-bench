package com.example.lending.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Loan {

    static final int LOAN_PERIOD_DAYS = 14;

    private final UUID id;
    private final UUID bookId;
    private final UUID memberId;
    private final LocalDate loanedAt;
    private final LocalDate dueAt;
    private LocalDate returnedAt;

    private Loan(UUID id, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt, LocalDate returnedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookId = Objects.requireNonNull(bookId, "bookId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.loanedAt = Objects.requireNonNull(loanedAt, "loanedAt");
        this.dueAt = Objects.requireNonNull(dueAt, "dueAt");
        if (dueAt.isBefore(loanedAt)) {
            throw new IllegalArgumentException("dueAt must not be before loanedAt");
        }
        this.returnedAt = returnedAt;
    }

    // 대출 생성은 Book.loanTo를 통해서만 (대출 가능 여부 규칙을 우회하지 않도록 package-private)
    static Loan start(UUID bookId, UUID memberId, LocalDate loanedAt) {
        return new Loan(UUID.randomUUID(), bookId, memberId, loanedAt, loanedAt.plusDays(LOAN_PERIOD_DAYS), null);
    }

    public static Loan reconstitute(UUID id, UUID bookId, UUID memberId, LocalDate loanedAt, LocalDate dueAt,
                                    LocalDate returnedAt) {
        return new Loan(id, bookId, memberId, loanedAt, dueAt, returnedAt);
    }

    public void returnOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        if (isReturned()) {
            throw new LoanAlreadyReturnedException(id);
        }
        if (date.isBefore(loanedAt)) {
            throw new IllegalArgumentException("returnedAt must not be before loanedAt");
        }
        this.returnedAt = date;
    }

    public boolean isReturned() {
        return returnedAt != null;
    }

    public boolean isOverdue(LocalDate today) {
        return today.isAfter(dueAt);
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
}
