package com.example.lending.application.port.in;

import com.example.lending.domain.Book;

import java.util.List;

public interface SearchBooksUseCase {

    /**
     * 제목에 keyword 가 포함된 책(대소문자 무시). keyword 가 비어 있으면 전부.
     */
    List<Book> searchBooks(String keyword);
}
