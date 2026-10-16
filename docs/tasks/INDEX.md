# TASK INDEX

| TASK | Date | Status | Summary | Detail |
| --- | --- | --- | --- | --- |
| TASK1 | 2026-10-14 | completed | 헥사고날 ArchUnit 규칙 R1~R6 구현 + lending 참조 구현 + 위반 픽스처 v1~v6 양방향 검증, REPORT.md 작성 | [상세](./TASK1.md) |
| TASK2 | 2026-10-14 | completed | CLAUDE.md 신설 (TASK 로깅 워크플로 차용 + arch-bench 빌드·구조·규칙·관례) 및 docs/tasks 초기화 | [상세](./TASK2.md) |
| TASK3 | 2026-10-14 | completed | R1 블랙리스트 확장(validation·Jackson)·R7 최상위 패키지 화이트리스트·픽스처 v7/v8, JSON 주 지표를 위반 클래스 수로 변경(ViolationExtractor), archTest 0개 가드, git 기준 커밋 (Follow-up 2026-10-15: 주 지표 합집합을 R1·R2·R3·R6·R7 로 한정, primary_rules 필드) | [상세](./TASK3.md) |
| TASK4 | 2026-10-15 | completed | 실험 부품: 시드 저장소 생성(materialize-seed.sh), 하네스 수준 L0~L3 주입(inject-harness.sh, CLAUDE.md 산문·PostToolUse 훅·리뷰어 서브에이전트), 오버레이 채점(grade.sh), smoke.sh 1~7단계 통과 (Follow-up 2026-10-15: 에이전트 워크스페이스 루트를 ~/Project/arch-bench-ws 로 지정, ARCH_WS_ROOT·상위 CLAUDE.md 누출 검사) | [상세](./TASK4.md) |
| TASK5 | 2026-10-15 | completed | TASK4 열린 결정 반영(채점기 주석 실험 용어 제거·측정값 바이트 동일, 단일 "Initial commit", 훅 매처 Bash + src/main 내용 변경 감지) + 세션 러너 run_session.py·fake_agent·_smoke 3턴, fake/haiku 실호출 검증 | [상세](./TASK5.md) |
| TASK6 | 2026-10-15 | completed | 워크스페이스 누설 차단(무작위 토큰 경로·workspace-index.jsonl·escaped_cwd_count 측정·러너 커밋 "Save work in progress") + 매트릭스 실행기 run_matrix.py(시드 고정 셔플·병렬·--resume·비용 가드·matrix-status.json), fake 12세션·haiku 병렬 실호출 검증 (Follow-up 2026-10-15: ws 루트 비움, 잔여물 사전 검사, 실패 상태 aborted→failed) | [상세](./TASK6.md) |
| TASK7 | 2026-10-15 | completed | 도서 대출 15턴 과제 파일(API 계약 절, 함정 5개 비표시) + HTTP 수준 숨겨진 기능 테스트 44개 + 헥사고날 참조 구현(턴별 커밋 15개, 위반 0, lloc.json) + 러너 FT_pass 누적 측정(ft_*) + 파일럿 설정(pilot.json, 미실행) | [상세](./TASK7.md) |
| TASK8 | 2026-10-15 | completed | 파일럿 1차 2세션 실행(L1 $0.87 완주·L2 $1.10 에서 중단, 위반 0·FT 100%, 함정 10건 복종 0) + summarize.py + pilot-report.md(설계자 질문·300세션 예산 재산정) + matrix skip_agent_tests. 세션당 $1 초과로 2차 보류 → TASK9 설계 변경으로 대체 (Last Updated 2026-10-16) | [상세](./TASK8.md) |
| TASK9 | 2026-10-16 | completed | 턴 8(후원자 한도 면제)·10(업체 SDK) 함정 재작성·참조 구현 커밋 2개·테스트·lloc + 러너 --session-mode fresh|resume·--max-session-cost·task_refused·구독 사전 검사·한도 대기 재시도 + 매트릭스 블록 무작위화·모델 변경 종료 6 + pilot2(L0·L1 fresh 4세션, 위반 0, 턴 8 복종 4/4) 보고서 | [상세](./TASK9.md) |
