package com.example.fixtures.v3.adapter.in.web;

import com.example.fixtures.v3.adapter.out.persistence.BookJpaEntity;
import com.example.fixtures.v3.adapter.out.persistence.BookJpaRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

// 위반(R3): 웹 어댑터가 JPA 엔티티를 그대로 응답으로 반환한다.
@RestController
public class LoanController {

    private final BookJpaRepository bookJpaRepository;

    public LoanController(BookJpaRepository bookJpaRepository) {
        this.bookJpaRepository = bookJpaRepository;
    }

    @GetMapping("/books/{id}")
    public BookJpaEntity getBook(@PathVariable Long id) {
        return bookJpaRepository.findById(id).orElseThrow();
    }
}
