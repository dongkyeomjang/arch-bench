# TASK2 — CLAUDE.md 신설 및 TASK 로깅 워크플로 도입

- **Date**: 2026-10-14
- **Status**: completed
- **Related TASKs**: TASK1
- **Commits**: — (git 저장소 아님)

## Request
다른 프로젝트의 CLAUDE.md 에 있던 TASK 관리 방식을 그대로 차용해 arch-bench 바로 아래 CLAUDE.md 에 적용하고, 나머지 내용은 이 프로젝트 내용으로 덮어쓸 것.

## Context
arch-bench 에는 CLAUDE.md 가 없었다. 원본은 다른 Spring 프로젝트(MySQL/Mongo/Redis, JWT 보안, `ResponseDto`, `@UseCase` 등) 기준이라 해당 내용은 이 저장소에 맞지 않는다.

## Changes
- `CLAUDE.md` 신설
  - **Task Logging Workflow**: 원문 그대로 유지.
  - **Build & Run Commands**: `archTest`, `build archTest`, `:lending:test`, `:lending:bootRun`, `:violation-fixtures:r4Investigation` 와 스택 버전, `lending.json` 경로로 교체.
  - **Architecture**: 저장소의 역할(초기 저장소 + 채점기)과 모듈·패키지 구조로 교체.
  - **Rules (R1~R6)** 절 신설: ID/이름이 JSON 키라 변경 금지, 규칙별 건수 단위가 달라 합산 금지.
  - **Key Conventions**: 원본의 ResponseDto/@UseCase/BaseId 등을 제거하고 TASK1 작업 지시의 제약(픽스처 약화 금지, 규칙 완화 전 lending 의심, Lombok/MapStruct 금지, 추상화 금지, 의존성 기록)과 `@ArchTag` 필수, lending 도메인/어댑터 관례로 교체.
  - **Commit & PR Convention**: 사용자 공통 규칙이므로 표는 유지, 예시만 이 저장소 맥락으로 교체.
  - 원본의 **Domain Modules**, **Security Flow** 절은 해당 없음이라 제거.
- `docs/tasks/INDEX.md`, `TASK1.md`(이전 ArchUnit 작업 소급 기록), `TASK2.md` 생성.

## Notes
- 원본 워크플로 5번은 INDEX 의 "Last Updated" 열을 갱신하라고 하지만, 원본 행 포맷에는 그 열이 없다. 원문 그대로 차용하라는 요청이라 고치지 않았고, INDEX 는 행 포맷(5열)을 따랐다.
- 이 디렉터리는 아직 git 저장소가 아니어서 `git pull`/커밋 단계는 적용되지 않는다.
