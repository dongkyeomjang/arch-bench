package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadReservationPort;
import com.example.lending.application.port.out.SaveReservationPort;
import com.example.lending.domain.Reservation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ReservationPersistenceAdapter implements LoadReservationPort, SaveReservationPort {

    private final ReservationJpaRepository reservationJpaRepository;
    private final ReservationMapper reservationMapper = new ReservationMapper();

    public ReservationPersistenceAdapter(ReservationJpaRepository reservationJpaRepository) {
        this.reservationJpaRepository = reservationJpaRepository;
    }

    @Override
    public List<Reservation> loadWaitingReservations(UUID bookId) {
        return reservationJpaRepository.findByBookIdAndFulfilledFalse(bookId).stream()
                .map(reservationMapper::toDomain)
                .toList();
    }

    @Override
    public void saveReservation(Reservation reservation) {
        reservationJpaRepository.save(reservationMapper.toEntity(reservation));
    }
}
