package com.example.lending.hidden;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.lending.hidden.Api.obj;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Turn07Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void an_available_book_can_be_discarded() {
        String title = Api.unique("Worn");
        String book = api.book(title);
        Api.Res r = api.post("/books/" + book + "/discard", null);
        assertEquals(200, r.status());
        assertEquals(book, r.text("id"));
        assertEquals(title, r.text("title"));
        assertEquals("DISCARDED", r.text("status"));
    }

    @Test
    void a_book_on_loan_cannot_be_discarded() {
        String book = api.book();
        api.loanOk(book, api.member(), "2026-02-10");
        Api.Res r = api.post("/books/" + book + "/discard", null);
        assertEquals(409, r.status());
        assertEquals("BOOK_ON_LOAN", r.error());
    }

    @Test
    void a_discarded_book_cannot_be_loaned() {
        String book = api.book();
        assertEquals(200, api.post("/books/" + book + "/discard", null).status());
        Api.Res r = api.loan(book, api.member(), "2026-02-10");
        assertEquals(409, r.status());
        assertEquals("BOOK_DISCARDED", r.error());
    }

    @Test
    void discarding_an_unknown_book_returns_404() {
        assertEquals(200, api.post("/books/" + api.book() + "/discard", null).status());
        assertEquals(404, api.post("/books/" + UUID.randomUUID() + "/discard", null).status());
    }
}
