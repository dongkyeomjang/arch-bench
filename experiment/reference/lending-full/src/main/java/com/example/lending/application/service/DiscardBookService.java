package com.example.lending.application.service;

import com.example.lending.application.port.in.DiscardBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class DiscardBookService implements DiscardBookUseCase {

    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;

    public DiscardBookService(LoadBookPort loadBookPort, SaveBookPort saveBookPort) {
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
    }

    @Override
    public Book discardBook(UUID bookId) {
        Book book = loadBookPort.loadBook(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
        book.discard();
        saveBookPort.saveBook(book);
        return book;
    }
}
