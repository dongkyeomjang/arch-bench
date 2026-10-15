
## 아키텍처 리뷰

- 요청을 완료했다고 판단하면, 커밋하기 전에 `architecture-reviewer` 서브에이전트에게 지금까지의 변경을 검토해 달라고 요청한다.
- 리뷰어 응답의 첫 줄이 `VERDICT: REJECT` 이면 지적 사항을 반영한 뒤 다시 검토를 요청한다. 검토 요청은 한 요청당 최대 2회다.
- `VERDICT: APPROVE` 를 받으면 커밋한다. 2회 검토 후에도 `VERDICT: REJECT` 이면 그대로 커밋하고, 응답에 REJECT 사유를 요약한다.
