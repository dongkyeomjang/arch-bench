package com.example.fixtures.v1.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class BookJpaEntity {

    @Id
    private Long id;
    private String title;

    protected BookJpaEntity() {
    }

    public BookJpaEntity(Long id, String title) {
        this.id = id;
        this.title = title;
    }
}
