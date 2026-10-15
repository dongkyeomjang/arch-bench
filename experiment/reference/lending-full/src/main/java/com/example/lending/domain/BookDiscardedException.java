package com.example.lending.domain;

import java.util.UUID;

public class BookDiscardedException extends RuleViolationException {

    public BookDiscardedException(UUID bookId) {
        super("BOOK_DISCARDED", "book is discarded: " + bookId);
    }
}
