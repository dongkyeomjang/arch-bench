package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.ListReservationsUseCase.WaitingReservation;

import java.util.UUID;

public record WaitingReservationResponse(UUID reservationId, UUID memberId, int position) {

    static WaitingReservationResponse from(WaitingReservation waiting) {
        return new WaitingReservationResponse(waiting.reservationId(), waiting.memberId(), waiting.position());
    }
}
