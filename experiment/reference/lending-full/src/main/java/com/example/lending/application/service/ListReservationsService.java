package com.example.lending.application.service;

import com.example.lending.application.port.in.ListReservationsUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadReservationPort;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.ReservationQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ListReservationsService implements ListReservationsUseCase {

    private final LoadBookPort loadBookPort;
    private final LoadReservationPort loadReservationPort;

    public ListReservationsService(LoadBookPort loadBookPort, LoadReservationPort loadReservationPort) {
        this.loadBookPort = loadBookPort;
        this.loadReservationPort = loadReservationPort;
    }

    @Override
    public List<WaitingReservation> listReservations(UUID bookId) {
        if (loadBookPort.loadBook(bookId).isEmpty()) {
            throw new BookNotFoundException(bookId);
        }
        ReservationQueue queue = ReservationQueue.of(bookId, loadReservationPort.loadWaitingReservations(bookId));
        return queue.waiting().stream()
                .map(r -> new WaitingReservation(r.getId(), r.getMemberId(), queue.positionOf(r)))
                .toList();
    }
}
