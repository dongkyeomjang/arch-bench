package com.example.fixtures.v6.application.service;

import com.example.fixtures.v6.application.port.out.SaveBookPort;
import com.example.fixtures.v6.domain.Book;

import java.util.HashMap;
import java.util.Map;

// 위반(R6-b): port.out 구현체가 adapter.out이 아니라 application.service에 있다.
public class InMemoryBookStore implements SaveBookPort {

    private final Map<Long, Book> books = new HashMap<>();

    @Override
    public void saveBook(Book book) {
        books.put(book.getId(), book);
    }
}
