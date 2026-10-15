package com.example.lending.application.port.out;

import com.example.lending.domain.Book;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadBookPort {

    Optional<Book> loadBook(UUID bookId);

    List<Book> loadBooks(Collection<UUID> bookIds);
}
