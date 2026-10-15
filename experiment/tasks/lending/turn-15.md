월별 통계 화면이 필요해요. 연·월을 받아서 그 달의 총 대출 건수, 반납 건수, 연체 발생 건수(연체료가 0보다 큰 반납), 연체료 합계를 돌려주는 API를 만들어 주세요.

## API

- `GET /stats/monthly?year=2026&month=1` → `200`, 응답 `{"year": 2026, "month": 1, "loanCount": 12, "returnCount": 9, "overdueCount": 2, "lateFeeTotal": 700}`
- 대출 건수는 대출일이 그 달인 대출, 반납·연체 건수와 연체료 합계는 반납일이 그 달인 대출로 셉니다
- 활동이 없는 달은 모두 0
