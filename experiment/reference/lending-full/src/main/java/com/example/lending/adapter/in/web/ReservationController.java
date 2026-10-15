package com.example.lending.adapter.in.web;

import com.example.lending.application.port.in.ListReservationsUseCase;
import com.example.lending.application.port.in.ReserveBookUseCase;
import com.example.lending.application.port.in.ReserveBookUseCase.ReserveBookCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/books/{bookId}/reservations")
public class ReservationController {

    private final ReserveBookUseCase reserveBookUseCase;
    private final ListReservationsUseCase listReservationsUseCase;

    public ReservationController(ReserveBookUseCase reserveBookUseCase, ListReservationsUseCase listReservationsUseCase) {
        this.reserveBookUseCase = reserveBookUseCase;
        this.listReservationsUseCase = listReservationsUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserveBook(@PathVariable UUID bookId, @RequestBody ReserveRequest request) {
        return ReservationResponse.from(reserveBookUseCase.reserveBook(new ReserveBookCommand(bookId, request.memberId())));
    }

    @GetMapping
    public List<WaitingReservationResponse> listReservations(@PathVariable UUID bookId) {
        return listReservationsUseCase.listReservations(bookId).stream().map(WaitingReservationResponse::from).toList();
    }
}
