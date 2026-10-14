package com.example.fixtures.v6.adapter.out.persistence;

import com.example.fixtures.v6.application.port.out.LoadBookPort;
import com.example.fixtures.v6.domain.Book;

public class BookPersistenceAdapter extends LoadBookPort {

    @Override
    public Book loadBook(Long bookId) {
        return new Book(bookId);
    }
}
