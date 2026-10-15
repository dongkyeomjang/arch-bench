package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Reservation;
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

    static ReservationJpaEntity from(Reservation reservation) {
        ReservationJpaEntity e = new ReservationJpaEntity();
        e.id = reservation.getId();
        e.bookId = reservation.getBookId();
        e.memberId = reservation.getMemberId();
        e.sequence = reservation.getSequence();
        e.fulfilled = reservation.isFulfilled();
        return e;
    }

    Reservation toDomain() {
        return Reservation.reconstitute(id, bookId, memberId, sequence, fulfilled);
    }
}
