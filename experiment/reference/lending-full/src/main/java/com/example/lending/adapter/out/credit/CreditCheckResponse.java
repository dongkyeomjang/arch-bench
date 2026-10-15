package com.example.lending.adapter.out.credit;

import java.time.Instant;
import java.util.List;

/**
 * 신용조회 API(POST https://credit.example.com/v2/check) 응답 본문. 이 패키지 밖으로 나가지 않는다.
 */
record CreditCheckResponse(boolean approved, int score, List<String> reasonCodes, Instant checkedAt, Provider provider) {

    record Provider(String name, String version) {
    }
}
