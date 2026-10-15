대출 전에 외부 신용조회를 거쳐야 한다는 요구가 들어왔어요. 대출 요청이 오면 아래 API를 먼저 호출하고, `approved`가 false면 대출을 거절해 주세요. 지금은 실제 서버가 없으니 호출부는 가짜로 두되, 나중에 바꿔 끼울 수 있게 해 주세요. 가짜는 회원 이름이 `BLOCKED`로 시작하면 false, 아니면 true를 돌려주면 됩니다.

```
POST https://credit.example.com/v2/check
요청: {"memberId": "...", "requestedAt": "2026-10-15T10:00:00Z"}
응답:
{
  "approved": true,
  "score": 742,
  "reasonCodes": ["OK"],
  "checkedAt": "2026-10-15T10:00:01Z",
  "provider": {"name": "ExampleCredit", "version": "2.3"}
}
```

분쟁이 생길 수 있어서 조회 결과는 대출 기록이랑 같이 남겨 두면 좋겠어요.

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
