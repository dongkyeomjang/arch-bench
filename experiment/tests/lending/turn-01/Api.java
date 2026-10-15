package com.example.lending.hidden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * HTTP 호출 도우미. 턴 1~15 테스트가 공유한다(러너가 turn-01 부터 누적 복사하므로 항상 함께 있다).
 * 응답 본문은 직접 만든 ObjectMapper 로 읽는다. 애플리케이션 빈은 쓰지 않는다.
 */
final class Api {

    static final ObjectMapper JSON = new ObjectMapper();

    private final TestRestTemplate rest;

    Api(TestRestTemplate rest) {
        this.rest = rest;
    }

    record Res(int status, JsonNode body) {
        String text(String field) {
            JsonNode v = body == null ? null : body.get(field);
            return v == null || v.isNull() ? null : v.asText();
        }

        String error() {
            return text("error");
        }

        List<JsonNode> items() {
            List<JsonNode> out = new ArrayList<>();
            if (body != null && body.isArray()) {
                body.forEach(out::add);
            }
            return out;
        }
    }

    static ObjectNode obj(Object... keyValues) {
        ObjectNode node = JSON.createObjectNode();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object v = keyValues[i + 1];
            if (v == null) {
                node.putNull((String) keyValues[i]);
            } else {
                node.put((String) keyValues[i], v.toString());
            }
        }
        return node;
    }

    Res post(String path, JsonNode body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = body == null ? "{}" : body.toString();
        return exchange(path, HttpMethod.POST, new HttpEntity<>(payload, headers));
    }

    Res get(String path) {
        return exchange(path, HttpMethod.GET, HttpEntity.EMPTY);
    }

    private Res exchange(String path, HttpMethod method, HttpEntity<?> entity) {
        ResponseEntity<String> r = rest.exchange(path, method, entity, String.class);
        JsonNode body = null;
        if (r.getBody() != null && !r.getBody().isBlank()) {
            try {
                body = JSON.readTree(r.getBody());
            } catch (Exception e) {
                body = JSON.getNodeFactory().textNode(r.getBody());
            }
        }
        return new Res(r.getStatusCode().value(), body);
    }

    // ------------------------------------------------------------ 준비 단계 (성공을 전제로 한다)

    static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    String book(String title) {
        Res r = post("/books", obj("title", title));
        assertEquals(201, r.status(), "POST /books");
        return r.text("id");
    }

    String book() {
        return book(unique("Book"));
    }

    String member(String name) {
        Res r = post("/members", obj("name", name));
        assertEquals(201, r.status(), "POST /members");
        return r.text("memberId");
    }

    String member() {
        return member(unique("Member"));
    }

    Res loan(String bookId, String memberId) {
        return post("/loans", obj("bookId", bookId, "memberId", memberId));
    }

    Res loan(String bookId, String memberId, String loanedAt) {
        return post("/loans", obj("bookId", bookId, "memberId", memberId, "loanedAt", loanedAt));
    }

    String loanOk(String bookId, String memberId, String loanedAt) {
        Res r = loanedAt == null ? loan(bookId, memberId) : loan(bookId, memberId, loanedAt);
        assertEquals(201, r.status(), "POST /loans " + r.body());
        return r.text("loanId");
    }

    Res returnLoan(String loanId) {
        return post("/loans/" + loanId + "/return", null);
    }

    Res returnLoan(String loanId, String returnedAt) {
        return post("/loans/" + loanId + "/return", obj("returnedAt", returnedAt));
    }
}
