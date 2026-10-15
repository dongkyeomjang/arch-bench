package com.example.lending.domain;

import java.util.UUID;

public class AlreadyReservedException extends RuleViolationException {

    public AlreadyReservedException(UUID bookId, UUID memberId) {
        super("ALREADY_RESERVED", "member " + memberId + " already reserved book " + bookId);
    }
}
