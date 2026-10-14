package com.example.fixtures.v6.application.port.out;

import com.example.fixtures.v6.domain.Book;

public interface SaveBookPort {

    void saveBook(Book book);
}
