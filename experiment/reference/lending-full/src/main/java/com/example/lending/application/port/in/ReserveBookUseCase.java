package com.example.lending.application.port.in;

import java.util.UUID;

public interface ReserveBookUseCase {

    PlacedReservation reserveBook(ReserveBookCommand command);

    record ReserveBookCommand(UUID bookId, UUID memberId) {
        public ReserveBookCommand {
            if (bookId == null || memberId == null) {
                throw new IllegalArgumentException("bookId and memberId are required");
            }
        }
    }

    record PlacedReservation(UUID reservationId, int position) {
    }
}
