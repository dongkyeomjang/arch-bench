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
class Turn05Test {

    @Autowired
    TestRestTemplate rest;

    Api api;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
    }

    @Test
    void current_loans_are_paged_with_total_count() {
        String member = api.member();
        java.util.Map<String, String> titleByLoan = new java.util.HashMap<>();
        String[] dates = {"2026-02-02", "2026-02-03", "2026-02-04"};
        for (String date : dates) {
            String title = Api.unique("Paged");
            titleByLoan.put(api.loanOk(api.book(title), member, date), title);
        }
        Api.Res first = api.get("/members/" + member + "/loans?page=0&size=2");
        Api.Res second = api.get("/members/" + member + "/loans?page=1&size=2");
        assertEquals(200, first.status());
        assertEquals(200, second.status());
        assertEquals(3, first.body().get("totalCount").asInt());
        assertEquals(3, second.body().get("totalCount").asInt());
        assertEquals(0, first.body().get("page").asInt());
        assertEquals(2, first.body().get("size").asInt());
        assertEquals(1, second.body().get("page").asInt());
        assertEquals(2, first.body().get("items").size());
        assertEquals(1, second.body().get("items").size());

        Set<String> seen = new HashSet<>();
        for (Api.Res page : List.of(first, second)) {
            for (JsonNode item : page.body().get("items")) {
                String loanId = item.get("loanId").asText();
                seen.add(loanId);
                assertEquals(titleByLoan.get(loanId), item.get("title").asText());
                assertNotNull(item.get("bookId"));
                assertNotNull(item.get("loanedAt"));
                assertNotNull(item.get("dueAt"));
            }
        }
        assertEquals(titleByLoan.keySet(), seen);
    }

    @Test
    void returned_loans_are_not_listed() {
        String member = api.member();
        String kept = api.loanOk(api.book(), member, "2026-02-02");
        String returned = api.loanOk(api.book(), member, "2026-02-03");
        assertEquals(200, api.returnLoan(returned, "2026-02-05").status());
        Api.Res r = api.get("/members/" + member + "/loans?page=0&size=2");
        assertEquals(200, r.status());
        assertEquals(1, r.body().get("totalCount").asInt());
        assertEquals(kept, r.body().get("items").get(0).get("loanId").asText());
        assertEquals("2026-02-16", r.body().get("items").get(0).get("dueAt").asText());
    }

    @Test
    void unknown_member_returns_404() {
        assertEquals(200, api.get("/members/" + api.member() + "/loans?page=0&size=2").status());
        assertEquals(404, api.get("/members/" + UUID.randomUUID() + "/loans?page=0&size=2").status());
    }
}
