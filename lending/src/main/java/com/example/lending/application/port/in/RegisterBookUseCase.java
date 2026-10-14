package com.example.lending.application.port.in;

import com.example.lending.domain.Book;

public interface RegisterBookUseCase {

    Book registerBook(RegisterBookCommand command);

    record RegisterBookCommand(String title) {
    }
}
