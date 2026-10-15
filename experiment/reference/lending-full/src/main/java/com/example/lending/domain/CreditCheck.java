package com.example.lending.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 대출 시점의 신용조회 결과 중 대출 기록에 남기는 값(분쟁 대비).
 */
public record CreditCheck(boolean approved, int score, Instant checkedAt) {

    public CreditCheck {
        Objects.requireNonNull(checkedAt, "checkedAt");
    }
}
