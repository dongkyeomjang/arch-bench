대출 중인 책에 예약을 걸 수 있게 해 주세요. 한 책에 여러 명이 예약하면 순서대로 줄을 서고, 그 책이 반납되면 맨 앞 예약자가 우선 대출할 수 있어야 합니다. 예약자가 아닌 사람이 빌리려고 하면 거절이에요. 맨 앞 예약자가 빌리면 예약은 소진되고 다음 사람이 맨 앞이 됩니다. 예약이 없는 책은 지금처럼 아무나 빌릴 수 있어요.

## API

- `POST /books/{bookId}/reservations` 본문 `{"memberId": "…"}` → `201`, 응답 `{"reservationId": "…", "position": 1}`. `position` 은 줄에서의 순서로 1부터 시작합니다
- 대출 가능한(아무도 빌리지 않은) 책에 예약하면 `409`, `{"error": "BOOK_AVAILABLE"}`
- 예약이 있는 책이 반납된 뒤에는
  - 맨 앞 예약자가 아닌 사람의 `POST /loans` → `409`, `{"error": "RESERVED_FOR_OTHER"}`
  - 맨 앞 예약자의 `POST /loans` → `201`
- `GET /books/{bookId}/reservations` → `200`, 응답 `[{"reservationId": "…", "memberId": "…", "position": 1}]`. 소진된 예약은 나오지 않습니다
