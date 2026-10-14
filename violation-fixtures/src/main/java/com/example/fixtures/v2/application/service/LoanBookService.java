package com.example.fixtures.v2.application.service;

import com.example.fixtures.v2.adapter.out.persistence.BookJpaEntity;
import com.example.fixtures.v2.adapter.out.persistence.BookJpaRepository;
import com.example.fixtures.v2.domain.Book;
import org.springframework.stereotype.Service;

// 위반(R2): 서비스가 포트 대신 영속성 어댑터의 리포지토리를 직접 주입받는다.
@Service
public class LoanBookService {

    private final BookJpaRepository bookJpaRepository;

    public LoanBookService(BookJpaRepository bookJpaRepository) {
        this.bookJpaRepository = bookJpaRepository;
    }

    public Book loadBook(Long bookId) {
        BookJpaEntity entity = bookJpaRepository.findById(bookId).orElseThrow();
        return new Book(entity.getId(), entity.getTitle());
    }
}
