package com.example.lending.domain;

import java.util.UUID;

public class BookAvailableException extends RuleViolationException {

    public BookAvailableException(UUID bookId) {
        super("BOOK_AVAILABLE", "book is available, borrow it instead of reserving: " + bookId);
    }
}
