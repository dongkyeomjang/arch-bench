package com.example.lending.adapter.out.persistence;

import com.example.lending.domain.Book;
import com.example.lending.domain.BookStatus;

class BookMapper {

    Book toDomain(BookJpaEntity entity) {
        return Book.reconstitute(entity.getId(), entity.getTitle(), BookStatus.valueOf(entity.getStatus()));
    }

    BookJpaEntity toEntity(Book book) {
        return new BookJpaEntity(book.getId(), book.getTitle(), book.getStatus().name());
    }
}
