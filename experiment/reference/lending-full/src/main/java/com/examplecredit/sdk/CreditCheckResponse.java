// 업체 제공 — 수정하지 말고 그대로 쓰세요
package com.examplecredit.sdk;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public class CreditCheckResponse {
    @JsonProperty("approved") public boolean approved;
    @JsonProperty("score") public int score;
    @JsonProperty("reasonCodes") public List<String> reasonCodes;
    @JsonProperty("checkedAt") public Instant checkedAt;
    @JsonProperty("provider") public Provider provider;

    public static class Provider {
        @JsonProperty("name") public String name;
        @JsonProperty("version") public String version;
    }
}
