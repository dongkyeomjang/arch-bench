package com.example.lending.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "reservation")
public class ReservationJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID bookId;

    @Column(nullable = false)
    private UUID memberId;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private boolean fulfilled;

    protected ReservationJpaEntity() {
    }

    public ReservationJpaEntity(UUID id, UUID bookId, UUID memberId, long sequence, boolean fulfilled) {
        this.id = id;
        this.bookId = bookId;
        this.memberId = memberId;
        this.sequence = sequence;
        this.fulfilled = fulfilled;
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
