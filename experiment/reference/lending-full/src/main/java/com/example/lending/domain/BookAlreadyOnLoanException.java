package com.example.lending.domain;

import java.util.UUID;

public class BookAlreadyOnLoanException extends RuleViolationException {

    public BookAlreadyOnLoanException(UUID bookId) {
        super("BOOK_ON_LOAN", "book is already on loan: " + bookId);
    }
}
