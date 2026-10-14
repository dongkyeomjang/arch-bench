package com.example.fixtures.v1.domain;

import com.example.fixtures.v1.adapter.out.persistence.BookJpaEntity;
import com.example.fixtures.v1.adapter.out.persistence.BookJpaRepository;

// 위반(R1): 도메인이 영속성 어댑터의 리포지토리를 import해 스스로를 저장한다.
public class Book {

    private final Long id;
    private final String title;

    public Book(Long id, String title) {
        this.id = id;
        this.title = title;
    }

    public void saveWith(BookJpaRepository repository) {
        repository.save(new BookJpaEntity(id, title));
    }
}
