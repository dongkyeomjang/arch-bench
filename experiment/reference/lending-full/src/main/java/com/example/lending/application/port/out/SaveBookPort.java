package com.example.lending.application.port.out;

import com.example.lending.domain.Book;

public interface SaveBookPort {

    void saveBook(Book book);
}
