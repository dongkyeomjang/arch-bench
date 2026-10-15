package com.example.lending.adapter.in.web;

import com.example.lending.domain.Book;

import java.util.UUID;

public record BookResponse(UUID id, String title, String status) {

    static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getStatus().name());
    }
}
