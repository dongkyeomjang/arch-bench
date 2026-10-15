package com.example.lending.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Book {

    private final UUID id;
    private final String title;
    private BookStatus status;

    private Book(UUID id, String title, BookStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        this.title = title;
        this.status = Objects.requireNonNull(status, "status");
    }

    public static Book register(String title) {
        return new Book(UUID.randomUUID(), title, BookStatus.AVAILABLE);
    }

    public static Book reconstitute(UUID id, String title, BookStatus status) {
        return new Book(id, title, status);
    }

    public Loan loanTo(UUID memberId, LocalDate loanedAt) {
        if (status == BookStatus.DISCARDED) {
            throw new BookDiscardedException(id);
        }
        if (status == BookStatus.ON_LOAN) {
            throw new BookAlreadyOnLoanException(id);
        }
        Loan loan = Loan.start(id, memberId, loanedAt);
        status = BookStatus.ON_LOAN;
        return loan;
    }

    /**
     * 훼손·분실 책을 폐기한다. 대출 중이면 거절하고, 이미 폐기된 책은 그대로 둔다.
     */
    public void discard() {
        if (status == BookStatus.ON_LOAN) {
            throw new BookAlreadyOnLoanException(id);
        }
        status = BookStatus.DISCARDED;
    }

    public void receiveReturn() {
        status = BookStatus.AVAILABLE;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public BookStatus getStatus() {
        return status;
    }
}
