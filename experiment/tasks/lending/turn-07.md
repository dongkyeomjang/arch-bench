훼손되거나 분실된 책은 더 이상 대출되면 안 됩니다. 책을 폐기 처리하는 기능을 추가해 주세요. 폐기된 책은 대출 요청이 오면 거절하고, 이미 대출 중인 책은 폐기할 수 없어요.

## API

- `POST /books/{bookId}/discard` → `200`, 응답 `{"id": "…", "title": "…", "status": "DISCARDED"}`
- 대출 중인 책을 폐기하려 하면 `409`, `{"error": "BOOK_ON_LOAN"}`
- 폐기된 책을 `POST /loans` 로 빌리려 하면 `409`, `{"error": "BOOK_DISCARDED"}`
