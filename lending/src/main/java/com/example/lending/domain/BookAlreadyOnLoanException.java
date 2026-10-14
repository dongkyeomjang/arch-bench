package com.example.lending.domain;

import java.util.UUID;

public class BookAlreadyOnLoanException extends RuntimeException {

    public BookAlreadyOnLoanException(UUID bookId) {
        super("book is already on loan: " + bookId);
    }
}
