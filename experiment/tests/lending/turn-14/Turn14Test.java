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
class Turn14Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    private Set<String> actionsFor(String... targetIds) {
        Set<String> actions = new HashSet<>();
        for (String targetId : targetIds) {
            Api.Res r = api.get("/audit?targetId=" + targetId);
            assertEquals(200, r.status());
            for (JsonNode e : r.items()) {
                assertEquals(targetId, e.get("targetId").asText());
                assertNotNull(e.get("occurredAt"));
                actions.add(e.get("action").asText());
            }
        }
        return actions;
    }

    @Test
    void loan_and_return_are_audited() {
        String book = api.book();
        String loanId = api.loanOk(book, api.member(), "2026-04-06");
        assertEquals(200, api.returnLoan(loanId, "2026-04-08").status());
        int entries = api.get("/audit?targetId=" + loanId).items().size()
                + api.get("/audit?targetId=" + book).items().size();
        assertTrue(entries >= 2, "at least two audit entries for the loan and its book");
        Set<String> actions = actionsFor(loanId, book);
        assertTrue(actions.contains("LOAN"), "LOAN audited: " + actions);
        assertTrue(actions.contains("RETURN"), "RETURN audited: " + actions);
    }

    @Test
    void discard_is_audited() {
        String book = api.book();
        assertEquals(200, api.post("/books/" + book + "/discard", null).status());
        assertTrue(actionsFor(book).contains("DISCARD"));
    }

    @Test
    void suspend_and_unsuspend_are_audited() {
        String member = api.member();
        assertEquals(200, api.post("/members/" + member + "/suspend", null).status());
        assertEquals(200, api.post("/members/" + member + "/unsuspend", null).status());
        Set<String> actions = actionsFor(member);
        assertTrue(actions.contains("SUSPEND"), "SUSPEND audited: " + actions);
        assertTrue(actions.contains("UNSUSPEND"), "UNSUSPEND audited: " + actions);
    }
}
