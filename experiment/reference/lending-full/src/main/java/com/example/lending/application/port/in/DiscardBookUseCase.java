package com.example.lending.application.port.in;

import com.example.lending.domain.Book;

import java.util.UUID;

public interface DiscardBookUseCase {

    Book discardBook(UUID bookId);
}
