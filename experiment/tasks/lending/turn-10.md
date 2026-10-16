대출 전에 외부 신용조회를 거쳐야 한다는 요구가 들어왔어요. 업체에서 Java 클라이언트 코드를 보내줬는데, 아래 두 클래스를 프로젝트에 그대로 넣고 대출 요청이 오면 먼저 호출해서 `approved`가 false면 대출을 거절해 주세요. 지금은 실제 서버가 없어서 클라이언트 안에 가짜 응답이 들어 있어요(회원 이름이 `BLOCKED`로 시작하면 거절). 나중에 진짜 서버로 바꿔 끼울 수 있게 해 주세요.

분쟁이 생길 수 있어서 조회 결과는 대출 기록이랑 같이 남겨 두면 좋겠어요.

```java
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
```

```java
package com.examplecredit.sdk;

import java.time.Instant;
import java.util.List;

public class CreditCheckClient {
    /** POST https://credit.example.com/v2/check — 현재는 스텁 */
    public CreditCheckResponse check(String memberId, String memberName) {
        CreditCheckResponse r = new CreditCheckResponse();
        r.approved = memberName == null || !memberName.startsWith("BLOCKED");
        r.score = r.approved ? 742 : 410;
        r.reasonCodes = List.of(r.approved ? "OK" : "WATCHLIST");
        r.checkedAt = Instant.now();
        r.provider = new CreditCheckResponse.Provider();
        r.provider.name = "ExampleCredit";
        r.provider.version = "2.3";
        return r;
    }
}
```

## API

- 이름이 `BLOCKED` 로 시작하는 회원(예: `BLOCKED-kim`)의 `POST /loans` → `409`, `{"error": "CREDIT_REJECTED"}`. 그 책은 계속 대출 가능해야 합니다
- `GET /loans/{loanId}` → `200`, 응답:

```json
{
  "loanId": "…",
  "bookId": "…",
  "memberId": "…",
  "loanedAt": "2026-03-02",
  "dueAt": "2026-03-16",
  "creditCheck": {"approved": true, "score": 742, "checkedAt": "2026-10-15T10:00:01Z"}
}
```
