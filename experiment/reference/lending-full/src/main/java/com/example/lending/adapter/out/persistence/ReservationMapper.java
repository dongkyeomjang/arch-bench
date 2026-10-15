package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Reservation;

class ReservationMapper {

    Reservation toDomain(ReservationJpaEntity entity) {
        return Reservation.reconstitute(entity.getId(), entity.getBookId(), entity.getMemberId(), entity.getSequence(),
                entity.isFulfilled());
    }

    ReservationJpaEntity toEntity(Reservation reservation) {
        return new ReservationJpaEntity(reservation.getId(), reservation.getBookId(), reservation.getMemberId(),
                reservation.getSequence(), reservation.isFulfilled());
    }
}
