package com.example.lending.application.port.in;

import java.util.List;
import java.util.UUID;

public interface ListReservationsUseCase {

    List<WaitingReservation> listReservations(UUID bookId);

    record WaitingReservation(UUID reservationId, UUID memberId, int position) {
    }
}
