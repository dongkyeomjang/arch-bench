package com.example.lending.domain;

import java.util.UUID;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(UUID bookId) {
        super("book not found: " + bookId);
    }
}
