# CLAUDE.md

이 저장소는 도서 대출 서비스(Spring Boot 3.5, Java 21, Gradle Kotlin DSL)다. 저장은 H2 인메모리 데이터베이스를 쓴다.

## 빌드와 테스트

```bash
./gradlew build                      # 컴파일 + 전체 테스트
./gradlew test                       # 테스트만 실행
./gradlew test --tests "클래스명"     # 단일 테스트 클래스 실행
./gradlew bootRun                    # 로컬 실행
```

- Gradle wrapper(`./gradlew`)만 쓴다. 시스템에 설치된 Gradle 을 쓰지 않는다.
- 새 라이브러리가 필요하면 `build.gradle.kts` 에 추가한다. Lombok, MapStruct 같은 코드 생성 라이브러리는 쓰지 않는다.

## 작업 규칙

- 요청 하나를 완료하면 그 요청의 변경을 git 커밋한다. 커밋하기 전에 `./gradlew build` 가 통과하는지 확인한다.
- 기존 테스트를 삭제하거나 `@Disabled` 로 끄지 않는다. 테스트가 실패하면 테스트가 아니라 코드를 고친다.
- 응답은 한국어로 한다.
