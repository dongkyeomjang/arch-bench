package com.example.lending.application.service;

import com.example.lending.application.port.in.ReserveBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.LoadMemberPort;
import com.example.lending.application.port.out.LoadReservationPort;
import com.example.lending.application.port.out.SaveReservationPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import com.example.lending.domain.MemberNotFoundException;
import com.example.lending.domain.Reservation;
import com.example.lending.domain.ReservationQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReserveBookService implements ReserveBookUseCase {

    private final LoadBookPort loadBookPort;
    private final LoadMemberPort loadMemberPort;
    private final LoadReservationPort loadReservationPort;
    private final SaveReservationPort saveReservationPort;

    public ReserveBookService(LoadBookPort loadBookPort, LoadMemberPort loadMemberPort,
                              LoadReservationPort loadReservationPort, SaveReservationPort saveReservationPort) {
        this.loadBookPort = loadBookPort;
        this.loadMemberPort = loadMemberPort;
        this.loadReservationPort = loadReservationPort;
        this.saveReservationPort = saveReservationPort;
    }

    @Override
    public PlacedReservation reserveBook(ReserveBookCommand command) {
        Book book = loadBookPort.loadBook(command.bookId())
                .orElseThrow(() -> new BookNotFoundException(command.bookId()));
        if (loadMemberPort.loadMember(command.memberId()).isEmpty()) {
            throw new MemberNotFoundException(command.memberId());
        }
        ReservationQueue queue = ReservationQueue.of(book.getId(), loadReservationPort.loadWaitingReservations(book.getId()));
        Reservation reservation = queue.reserve(book, command.memberId());
        saveReservationPort.saveReservation(reservation);
        return new PlacedReservation(reservation.getId(), queue.positionOf(reservation));
    }
}
