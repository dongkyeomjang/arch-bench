package com.example.lending.domain;

import java.util.Objects;
import java.util.UUID;

public class Reservation {

    private final UUID id;
    private final UUID bookId;
    private final UUID memberId;
    private final long sequence;
    private boolean fulfilled;

    private Reservation(UUID id, UUID bookId, UUID memberId, long sequence, boolean fulfilled) {
        this.id = Objects.requireNonNull(id, "id");
        this.bookId = Objects.requireNonNull(bookId, "bookId");
        this.memberId = Objects.requireNonNull(memberId, "memberId");
        this.sequence = sequence;
        this.fulfilled = fulfilled;
    }

    // 예약 생성은 ReservationQueue 를 통해서만 (순서 번호를 대기열이 매긴다)
    static Reservation place(UUID bookId, UUID memberId, long sequence) {
        return new Reservation(UUID.randomUUID(), bookId, memberId, sequence, false);
    }

    public static Reservation reconstitute(UUID id, UUID bookId, UUID memberId, long sequence, boolean fulfilled) {
        return new Reservation(id, bookId, memberId, sequence, fulfilled);
    }

    void fulfil() {
        this.fulfilled = true;
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

    public long getSequence() {
        return sequence;
    }

    public boolean isFulfilled() {
        return fulfilled;
    }
}
