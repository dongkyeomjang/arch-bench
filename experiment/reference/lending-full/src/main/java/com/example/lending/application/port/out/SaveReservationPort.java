package com.example.lending.application.port.out;

import com.example.lending.domain.Reservation;

public interface SaveReservationPort {

    void saveReservation(Reservation reservation);
}
