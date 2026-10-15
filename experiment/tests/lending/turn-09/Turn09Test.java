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
class Turn09Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    private Api.Res reserve(String book, String member) {
        return api.post("/books/" + book + "/reservations", obj("memberId", member));
    }

    @Test
    void an_available_book_cannot_be_reserved() {
        Api.Res r = reserve(api.book(), api.member());
        assertEquals(409, r.status());
        assertEquals("BOOK_AVAILABLE", r.error());
    }

    @Test
    void reservations_line_up_in_order_and_can_be_listed() {
        String book = api.book();
        api.loanOk(book, api.member(), "2026-02-10");
        String b = api.member();
        String c = api.member();
        Api.Res first = reserve(book, b);
        Api.Res second = reserve(book, c);
        assertEquals(201, first.status());
        assertEquals(201, second.status());
        assertNotNull(first.text("reservationId"));
        assertEquals(1, first.body().get("position").asInt());
        assertEquals(2, second.body().get("position").asInt());

        Api.Res list = api.get("/books/" + book + "/reservations");
        assertEquals(200, list.status());
        List<JsonNode> items = list.items();
        assertEquals(2, items.size());
        JsonNode head = items.get(0).get("position").asInt() == 1 ? items.get(0) : items.get(1);
        JsonNode tail = head == items.get(0) ? items.get(1) : items.get(0);
        assertEquals(b, head.get("memberId").asText());
        assertEquals(first.text("reservationId"), head.get("reservationId").asText());
        assertEquals(c, tail.get("memberId").asText());
        assertEquals(2, tail.get("position").asInt());
    }

    @Test
    void after_return_only_the_first_in_line_may_borrow_and_the_line_moves_on() {
        String book = api.book();
        String a = api.member();
        String b = api.member();
        String c = api.member();
        String outsider = api.member();
        String loanA = api.loanOk(book, a, "2026-02-10");
        assertEquals(201, reserve(book, b).status());
        assertEquals(201, reserve(book, c).status());
        assertEquals(200, api.returnLoan(loanA, "2026-02-12").status());

        Api.Res byC = api.loan(book, c, "2026-02-13");
        assertEquals(409, byC.status());
        assertEquals("RESERVED_FOR_OTHER", byC.error());
        assertEquals("RESERVED_FOR_OTHER", api.loan(book, outsider, "2026-02-13").error());
        String loanB = api.loanOk(book, b, "2026-02-13");

        Api.Res list = api.get("/books/" + book + "/reservations");
        assertEquals(1, list.items().size());
        assertEquals(c, list.items().get(0).get("memberId").asText());
        assertEquals(1, list.items().get(0).get("position").asInt());

        assertEquals(200, api.returnLoan(loanB, "2026-02-14").status());
        assertEquals("RESERVED_FOR_OTHER", api.loan(book, outsider, "2026-02-15").error());
        assertEquals(201, api.loan(book, c, "2026-02-15").status());
    }

    @Test
    void reserving_an_unknown_book_returns_404() {
        String book = api.book();
        api.loanOk(book, api.member(), "2026-02-10");
        assertEquals(201, reserve(book, api.member()).status());
        assertEquals(404, reserve(UUID.randomUUID().toString(), api.member()).status());
    }
}
