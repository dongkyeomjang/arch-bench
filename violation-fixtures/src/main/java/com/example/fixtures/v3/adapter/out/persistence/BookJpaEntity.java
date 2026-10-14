package com.example.fixtures.v3.adapter.out.persistence;

import com.example.fixtures.v3.adapter.in.web.BookRequest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

// 위반(R3 역방향): 영속성 어댑터가 웹 어댑터의 요청 DTO를 안다.
@Entity
public class BookJpaEntity {

    @Id
    private Long id;
    private String title;

    protected BookJpaEntity() {
    }

    public static BookJpaEntity from(BookRequest request) {
        BookJpaEntity entity = new BookJpaEntity();
        entity.id = request.id();
        entity.title = request.title();
        return entity;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
