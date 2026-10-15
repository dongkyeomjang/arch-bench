package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Book;
import com.example.lending.domain.BookStatus;
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

    static BookJpaEntity from(Book book) {
        BookJpaEntity e = new BookJpaEntity();
        e.id = book.getId();
        e.title = book.getTitle();
        e.status = book.getStatus().name();
        return e;
    }

    Book toDomain() {
        return Book.reconstitute(id, title, BookStatus.valueOf(status));
    }
}
