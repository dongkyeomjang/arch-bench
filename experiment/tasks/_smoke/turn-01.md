<!-- smoke only, not an experiment task -->
회원 등록 기능을 추가해 주세요.

- `POST /members` 에 `{"name": "홍길동"}` 을 보내면 회원이 저장되고, `201 Created` 와 함께 `{"id": "<uuid>", "name": "홍길동"}` 을 돌려줍니다.
- 이름이 비어 있으면 `400 Bad Request` 를 돌려줍니다.
- 이 동작을 확인하는 테스트를 추가해 주세요.
