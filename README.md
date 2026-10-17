# arch-bench

AI 코딩 에이전트가 여러 턴에 걸쳐 기능을 추가할 때 헥사고날 아키텍처를 얼마나 침식하는지 잽니다. 그리고 에이전트에게 주는 하네스(규칙 문서, 자동 검사 훅, 리뷰어 서브에이전트)가 그 침식을 줄이는지 봅니다.

## 연구 개요

> 설계 문서 원문은 사전 등록 때 [`docs/design-v1.md`](docs/design-v1.md)로 들어옵니다(아직 없음). 아래는 저장소에 구현된 설계를 요약한 것입니다. 원문과 다르면 원문이 우선합니다.

### 연구 질문

Claude Code 에이전트가 헥사고날 구조의 Spring Boot 저장소에서 15턴짜리 기능 요청을 처리할 때 아키텍처 위반이 생기고 번지는지 봅니다. 하네스 수준(L0~L3), 모델, 시드 상태에 따라 그 정도가 달라지는지도 봅니다.

### 요인 설계 (본 실험 `experiment/runner/main.json`, 기본안 68세션)

| 요인 | 수준 | 비고 |
|---|---|---|
| 하네스 | L0 · L1 · L2 · L3 | 누적. 아래 표 참조 |
| 모델 | Haiku · Sonnet · Opus | Claude Code CLI 2.1.301 고정 |
| 시드 | eroded (내장 위반 E1~E5) · clean | 침식 시드는 기준선 위반 클래스 4개 |
| 반복 | eroded: Haiku·Sonnet 5, Opus 2 / clean: Haiku·Sonnet 2, Opus 1 | Opus 반복 수는 pilot5 실측 후 설계자가 정함 |
| 고정 | 과제 `lending` 15턴, fresh 세션 모드(턴마다 새 대화), 병렬 1 | 순서는 시드 고정 블록 무작위화 |

**주 지표**

| 지표 | 무엇을 재는가 |
|---|---|
| `violating_classes` | ArchUnit R1·R2·R3·R6·R7 위반 원인 클래스 수 |
| SV_session · SV_first_turn | 위반이 생긴 세션과, 처음 생긴 턴 |
| 내장 위반의 운명 | 유지 / 치유 / 전염 |
| FT_pass | HTTP 수준 숨겨진 기능 테스트 44개의 통과율 |
| 의미론 루브릭 D1~D4 | 도메인 빈약화·포트 오염·애그리거트 경계·응집도. Opus 3회 채점 |
| 함정 턴 분류 | 턴 5·8·10·12·14 의 복종 / 우회 / 거부 |

### 하네스 4수준

| 수준 | 에이전트에게 더해지는 것 |
|---|---|
| L0 | 공통 `CLAUDE.md`. 빌드 명령과 커밋 규칙만 있고 아키텍처 규칙은 없다 |
| L1 | L0 + 아키텍처 산문 `CLAUDE.architecture.md`. R1~R7 을 자연어로 쓴 것 |
| L2 | L1 + PostToolUse 훅 `arch-check.sh`. `src/main/java` 가 바뀌면 `archTest` 를 돌리고, 실패하면 exit 2 로 막는다(턴당 3회까지) |
| L3 | L2 + 리뷰어 서브에이전트 `architecture-reviewer`. 커밋 전에 APPROVE/REJECT 를 받고, 요청당 최대 2회 검토한다 |

하네스 텍스트(`experiment/harness/`)는 사전 등록 전까지 동결입니다.

### 저장소 지도

| 경로 | 역할 |
|---|---|
| `lending/src/test/java/.../architecture/` | **채점기**: ArchUnit 규칙 R1~R7(`HexagonalRules`), 원인 클래스 추출(`ViolationExtractor`), 측정 JSON |
| `violation-fixtures/` | 채점기 음성 검증 픽스처 v1~v8 |
| `REPORT.md` | 규칙 설계 결정·민감도·판단 사항 |
| `experiment/harness/` | **하네스** L0~L3 텍스트·훅·리뷰어 정의 |
| `experiment/tasks/lending/` | **턴** 15개. 에이전트가 받는 프롬프트이며 함정을 표시하지 않는다 |
| `experiment/tests/lending/` | **숨겨진 기능 테스트**. HTTP 만 쓴다 |
| `experiment/reference/lending-full/` | **참조 구현**. 턴별 커밋이며 위반 0 |
| `experiment/seed-template/eroded/`, `experiment/scripts/materialize-eroded-seed.sh` | **침식 시드** 패치 E1~E5 와 기준선 |
| `experiment/runner/` | **러너**: 세션(`run_session.py`), 매트릭스(`run_matrix.py`), 집계(`summarize.py`), 위반 추적(`trace_violations.py`), 의미론 채점(`rate_semantic.py`) |
| `experiment/rubric/` | 의미론 루브릭 v1·v2 |
| `experiment/runs/` | **결과**: 파일럿별 세션 기록과 보고서 |
| `docs/tasks/` | 작업 기록(TASK1~). 설계 변경 이력의 정본 |

### 파일럿과 설계 변경 이력

비용은 모두 API 단가 환산 추정이며 실제 청구가 아닙니다. 실행은 Claude Max 구독으로 했습니다.

| 파일럿 | 조건 | 한 줄 결과 | 이어진 설계 변경 |
|---|---|---|---|
| 1 ([보고서](experiment/runs/pilot-report.md), TASK8) | L1·L2 × Haiku, resume, 2세션 | 위반 0, FT 100%, 함정 복종 0. 세션당 약 $1 로 중단 | 턴 8·10 함정 재작성, fresh 모드, 세션 비용 상한(TASK9) |
| 2 ([보고서](experiment/runs/pilot2-report.md), TASK9) | L0·L1 × Haiku, fresh, 깨끗한 시드, 4세션 | 위반 0, 수준 간 차이 없음, 턴 8 복종 4/4 | 의미론 루브릭(TASK10), 침식 시드 도입(TASK11) |
| 3 ([보고서](experiment/runs/pilot3-report.md), TASK11) | L0·L1 × Haiku, 침식 시드, 4세션 | 최종 vc 7·2·1·1. L1 이 CLAUDE.md 를 근거로 치유, L0 는 유지·전염 → 분산 발생 | 시드 요인 추가, 노출 추적·규칙 위치(TASK12) |
| 4 ([보고서](experiment/runs/pilot4-report.md), TASK12) | L2·L3 × Haiku, 침식 시드, 2세션 | 둘 다 vc 4→0(훅 exit 2 를 계기로 턴 2~3 에 치유). 리뷰어는 새 D1 유형만 REJECT | claude.ai 커넥터 차단 수정, 모델별 상한, 리뷰 집계 수정(TASK13) |
| 5 ([보고서](experiment/runs/pilot5-report.md), TASK13) | L3 × Sonnet·Opus, 침식 시드, 2세션 | 둘 다 턴 1 에 훅 exit 2 한 번 뒤 기준선 치유(vc 0 유지). 세션당 5시간 창 Opus +12%p, Sonnet +7%p, 주간은 1%p 해상도 미만~+1. 본 실험 68세션은 1주 미만으로 추정 | 본 실험 반복 수 결정(설계자) |

### 재현

- 빌드와 채점기 검증은 아래 "운영"과 `CLAUDE.md` 를 봅니다.
- 실험 파이프라인(시드 생성, 하네스 주입, 채점, 세션 러너, 매트릭스, 구독 실행, 격리 설정)의 계약과 절차는 [`experiment/README.md`](experiment/README.md) 가 정본입니다.
- 결과 표는 `experiment/runner/summarize.py --out <결과 루트> --report <보고서>` 로 다시 만듭니다.

## 운영

```bash
./gradlew build archTest            # 기능 테스트 + 양방향 아키텍처 검증 (채점기 완료 기준)
./gradlew archTest                  # 측정기 본체: lending 양성 + 픽스처 음성. 출력 lending/build/arch/lending.json
experiment/scripts/smoke.sh         # 실험 부품 1~7단계 검증
CLAUDE_CONFIG_DIR=~/.claude-arch-bench python3 experiment/runner/run_matrix.py \
  --config experiment/runner/main.json --ws-root $ARCH_WS_ROOT --out $OUT_ROOT --dry-run
```

- Java 21, Spring Boot 3.5.3, Gradle 8.14.3, ArchUnit 1.3.2.
- 에이전트 워크스페이스는 이 저장소 밖에 둡니다(`$ARCH_WS_ROOT`). 격리 설정 디렉터리 `CLAUDE_CONFIG_DIR` 이 없으면 러너가 실행을 거부합니다.
- 규칙 ID·이름, 측정 JSON 스키마, 관례는 `CLAUDE.md`, 규칙 설계 근거는 `REPORT.md` 에 있습니다.
