package com.example.lending.adapter.out.persistence;

import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.domain.Book;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BookPersistenceAdapter implements LoadBookPort, SaveBookPort {

    private final BookJpaRepository bookJpaRepository;
    private final BookMapper bookMapper = new BookMapper();

    public BookPersistenceAdapter(BookJpaRepository bookJpaRepository) {
        this.bookJpaRepository = bookJpaRepository;
    }

    @Override
    public Optional<Book> loadBook(UUID bookId) {
        return bookJpaRepository.findById(bookId).map(bookMapper::toDomain);
    }

    @Override
    public List<Book> loadBooks(Collection<UUID> bookIds) {
        return bookJpaRepository.findAllById(bookIds).stream().map(bookMapper::toDomain).toList();
    }

    @Override
    public List<Book> searchBooksByTitle(String keyword) {
        return bookJpaRepository.findByTitleContainingIgnoreCase(keyword).stream().map(bookMapper::toDomain).toList();
    }

    @Override
    public void saveBook(Book book) {
        bookJpaRepository.save(bookMapper.toEntity(book));
    }
}
