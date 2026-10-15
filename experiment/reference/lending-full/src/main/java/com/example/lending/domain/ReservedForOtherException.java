package com.example.lending.domain;

import java.util.UUID;

public class ReservedForOtherException extends RuleViolationException {

    public ReservedForOtherException(UUID bookId, UUID memberId) {
        super("RESERVED_FOR_OTHER", "book " + bookId + " is reserved for another member than " + memberId);
    }
}
