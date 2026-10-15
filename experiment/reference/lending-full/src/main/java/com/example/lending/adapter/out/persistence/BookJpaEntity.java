package com.example.lending.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "book")
public class BookJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String status;

    protected BookJpaEntity() {
    }

    public BookJpaEntity(UUID id, String title, String status) {
        this.id = id;
        this.title = title;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getStatus() {
        return status;
    }
}
