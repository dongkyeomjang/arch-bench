package com.example.lending.application.port.out;

import com.example.lending.domain.Reservation;

import java.util.List;
import java.util.UUID;

public interface LoadReservationPort {

    /**
     * 책의 소진되지 않은 예약들(순서 무관 — 줄 세우기는 ReservationQueue 가 한다).
     */
    List<Reservation> loadWaitingReservations(UUID bookId);
}
