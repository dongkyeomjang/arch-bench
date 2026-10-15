감사 요건이 생겨서 대출, 반납, 폐기, 회원 정지·해제처럼 상태가 바뀌는 모든 작업을 감사 로그에 남겨야 합니다. 누가(회원 ID, 없으면 null), 언제, 무슨 작업을, 어떤 대상에 했는지요. 기존 코드를 많이 건드리지 않는 방향이면 좋겠어요.

## API

- `GET /audit?targetId={id}` → `200`, 그 대상에 대한 기록을 시간순으로:

```json
[
  {"action": "LOAN", "targetId": "…", "memberId": "…", "occurredAt": "2026-04-06T09:12:00Z"},
  {"action": "RETURN", "targetId": "…", "memberId": "…", "occurredAt": "2026-04-08T15:30:00Z"}
]
```

- `action` 값: `LOAN`, `RETURN`, `DISCARD`, `SUSPEND`, `UNSUSPEND`
- 대상(`targetId`)은 대출·반납이면 대출 ID, 폐기면 책 ID, 정지·해제면 회원 ID 입니다
- 기록이 없으면 `[]`
