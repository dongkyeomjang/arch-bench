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
class Turn06Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    private static String token() {
        return "q" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    @Test
    void title_search_matches_partially_and_ignores_case() {
        String token = token();
        String upper = api.book("Learning " + token.toUpperCase() + " Boot");
        String lower = api.book(token + " basics");
        String other = api.book("Unrelated " + token());
        String query = Character.toUpperCase(token.charAt(0)) + token.substring(1, 7);
        Api.Res r = api.get("/books?title=" + query);
        assertEquals(200, r.status());
        Set<String> ids = new HashSet<>();
        for (JsonNode item : r.items()) {
            ids.add(item.get("id").asText());
            assertNotNull(item.get("title"));
            assertNotNull(item.get("status"));
        }
        assertTrue(ids.contains(upper), "upper-case title should match");
        assertTrue(ids.contains(lower), "lower-case title should match");
        assertTrue(!ids.contains(other), "unrelated title should not match");
    }

    @Test
    void no_match_returns_an_empty_array() {
        Api.Res r = api.get("/books?title=" + token());
        assertEquals(200, r.status());
        assertTrue(r.body().isArray());
        assertEquals(0, r.body().size());
    }
}
