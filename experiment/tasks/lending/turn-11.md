회원이 지금까지 빌렸던 책 전체 이력을 볼 수 있게 해 주세요. 반납된 것도 포함해서, 최근 대출부터 순서대로요. 각 항목에 책 제목, 대출일, 반납일, 연체료가 나오면 됩니다.

## API

- `GET /members/{memberId}/loans/history` → `200`, 대출일 최신순:

```json
[
  {"loanId": "…", "title": "Refactoring", "loanedAt": "2026-03-10", "returnedAt": null, "lateFee": null},
  {"loanId": "…", "title": "Clean Code", "loanedAt": "2026-03-02", "returnedAt": "2026-03-21", "lateFee": 500}
]
```

- 아직 반납하지 않은 대출은 `returnedAt`·`lateFee` 가 `null` 입니다
- 대출한 적이 없으면 `[]`
