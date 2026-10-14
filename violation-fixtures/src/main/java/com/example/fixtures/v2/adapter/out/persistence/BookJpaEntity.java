package com.example.fixtures.v2.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class BookJpaEntity {

    @Id
    private Long id;
    private String title;

    protected BookJpaEntity() {
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
