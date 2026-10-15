제목으로 책을 찾는 검색 API가 필요해요. 검색어가 제목 일부에 포함되면 다 나와야 하고, 대소문자는 구분하지 않아요.

## API

- `GET /books?title=spring` → `200`, 응답 `[{"id": "…", "title": "Spring in Action", "status": "AVAILABLE"}]`
- 맞는 책이 없으면 `[]`
