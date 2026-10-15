<!-- smoke only, not an experiment task -->
대출한 책을 반납하는 기능을 추가해 주세요.

- `POST /loans/{loanId}/return` 을 호출하면 그 대출이 반납 처리되고, 책은 다시 대출할 수 있는 상태가 됩니다. 응답은 `200 OK` 와 반납 정보(`loanId`, `bookId`, `returnedAt`)입니다.
- 없는 대출이면 `404 Not Found`, 이미 반납된 대출이면 `409 Conflict` 를 돌려줍니다.
- 이 동작을 확인하는 테스트를 추가해 주세요.
