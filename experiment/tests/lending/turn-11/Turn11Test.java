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
class Turn11Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    private static boolean absent(JsonNode item, String field) {
        return item.get(field) == null || item.get(field).isNull();
    }

    @Test
    void history_lists_all_loans_newest_first_with_return_date_and_fee() {
        String member = api.member();
        String titleA = Api.unique("Returned");
        String titleB = Api.unique("Still-out");
        // A: 2026-03-02(월) 대출 → 기한 03-16(월), 03-21 반납 → 5일 500원. B: 03-10 대출, 미반납
        String loanA = api.loanOk(api.book(titleA), member, "2026-03-02");
        assertEquals(200, api.returnLoan(loanA, "2026-03-21").status());
        String loanB = api.loanOk(api.book(titleB), member, "2026-03-10");

        Api.Res r = api.get("/members/" + member + "/loans/history");
        assertEquals(200, r.status());
        List<JsonNode> items = r.items();
        assertEquals(2, items.size());
        JsonNode newest = items.get(0);
        JsonNode oldest = items.get(1);
        assertEquals(loanB, newest.get("loanId").asText());
        assertEquals(titleB, newest.get("title").asText());
        assertEquals("2026-03-10", newest.get("loanedAt").asText());
        assertTrue(absent(newest, "returnedAt"), "unreturned loan has no returnedAt");
        assertTrue(absent(newest, "lateFee"), "unreturned loan has no lateFee");
        assertEquals(loanA, oldest.get("loanId").asText());
        assertEquals(titleA, oldest.get("title").asText());
        assertEquals("2026-03-02", oldest.get("loanedAt").asText());
        assertEquals("2026-03-21", oldest.get("returnedAt").asText());
        assertEquals(500, oldest.get("lateFee").asInt());
    }

    @Test
    void member_without_loans_has_an_empty_history() {
        Api.Res r = api.get("/members/" + api.member() + "/loans/history");
        assertEquals(200, r.status());
        assertTrue(r.body().isArray());
        assertEquals(0, r.body().size());
    }

    @Test
    void unknown_member_returns_404() {
        assertEquals(200, api.get("/members/" + api.member() + "/loans/history").status());
        assertEquals(404, api.get("/members/" + UUID.randomUUID() + "/loans/history").status());
    }
}
