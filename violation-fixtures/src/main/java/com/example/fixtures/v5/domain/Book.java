package com.example.fixtures.v5.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;

// 위반(R5): 도메인 클래스에 @Entity, 필드에 @JsonProperty
@Entity
public class Book {

    private Long id;

    @JsonProperty("book_title")
    private String title;

    protected Book() {
    }
}
