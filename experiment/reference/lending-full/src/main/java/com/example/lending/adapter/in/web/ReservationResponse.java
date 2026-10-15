package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.ReserveBookUseCase.PlacedReservation;

import java.util.UUID;

public record ReservationResponse(UUID reservationId, int position) {

    static ReservationResponse from(PlacedReservation placed) {
        return new ReservationResponse(placed.reservationId(), placed.position());
    }
}
