package com.example.lending.application.service;

import com.example.lending.application.port.in.SearchBooksUseCase;
import com.example.lending.application.port.out.LoadBookPort;
import com.example.lending.domain.Book;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SearchBooksService implements SearchBooksUseCase {

    private final LoadBookPort loadBookPort;

    public SearchBooksService(LoadBookPort loadBookPort) {
        this.loadBookPort = loadBookPort;
    }

    @Override
    public List<Book> searchBooks(String keyword) {
        return loadBookPort.searchBooksByTitle(keyword == null ? "" : keyword.strip());
    }
}
