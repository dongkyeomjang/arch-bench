package com.example.lending.application.service;

import com.example.lending.application.port.in.DiscardBookUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.application.port.out.RecordAuditPort;
import com.example.lending.application.port.out.SaveBookPort;
import com.example.lending.domain.AuditAction;
import com.example.lending.domain.AuditEntry;
import com.example.lending.domain.Book;
import com.example.lending.domain.BookNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class DiscardBookService implements DiscardBookUseCase {

    private final LoadBookPort loadBookPort;
    private final SaveBookPort saveBookPort;

    private final RecordAuditPort recordAuditPort;

    public DiscardBookService(LoadBookPort loadBookPort, SaveBookPort saveBookPort, RecordAuditPort recordAuditPort) {
        this.loadBookPort = loadBookPort;
        this.saveBookPort = saveBookPort;
        this.recordAuditPort = recordAuditPort;
    }

    @Override
    public Book discardBook(UUID bookId) {
        Book book = loadBookPort.loadBook(bookId).orElseThrow(() -> new BookNotFoundException(bookId));
        book.discard();
        saveBookPort.saveBook(book);
        recordAuditPort.record(AuditEntry.of(AuditAction.DISCARD, book.getId(), null, Instant.now()));
        return book;
    }
}
