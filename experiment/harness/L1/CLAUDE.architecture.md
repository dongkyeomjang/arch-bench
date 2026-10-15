
## 아키텍처

이 서비스는 헥사고날 아키텍처(포트와 어댑터)로 구성되어 있다. 코드를 추가하거나 옮길 때 아래의 패키지 책임, 의존 방향, 금지 사항을 지킨다. 기준 패키지는 `com.example.lending` 이다.

### 레이어와 책임

- `domain` — 비즈니스 규칙과 상태. `Book`, `Loan`, `Member` 같은 모델과 도메인 예외를 둔다. 순수 Java 로만 작성한다.
- `application` — 유스케이스.
  - `application.port.in` — 바깥에서 유스케이스를 호출하는 입력 포트(interface).
  - `application.port.out` — 유스케이스가 저장소 같은 바깥 자원에 요구하는 출력 포트(interface).
  - `application.service` — 입력 포트를 구현한다. 바깥 자원에는 출력 포트를 통해서만 접근한다.
- `adapter` — 바깥 세계와의 연결.
  - `adapter.in` — 들어오는 요청을 받는 어댑터(웹 컨트롤러 등). 입력 포트를 호출한다.
  - `adapter.out` — 나가는 연결을 담당하는 어댑터(JPA 영속성 등). 출력 포트를 구현한다.

### 의존 방향

의존은 바깥에서 안쪽으로만 향한다: `adapter` → `application` → `domain`. 안쪽 레이어는 바깥 레이어의 존재를 모른다.

### 금지 사항

- `domain` 은 `application` 과 `adapter` 를 참조하지 않는다. Spring(`org.springframework`), JPA(`jakarta.persistence`), Bean Validation(`jakarta.validation`), Jackson(`com.fasterxml.jackson`) 도 참조하지 않는다.
- `application` 은 `adapter` 를 참조하지 않는다.
- `adapter.in` 과 `adapter.out` 은 서로 참조하지 않는다. 둘은 `application` 의 포트를 통해서만 이어진다.
- `domain`, `application`, `adapter` 사이에 순환 의존을 만들지 않는다.
- `domain` 클래스에 `@Entity`, `@Component`, `@Service`, `@JsonProperty` 를 붙이지 않는다. 영속성 매핑은 `adapter.out` 의 별도 JPA 엔티티가, JSON 변환은 `adapter.in` 의 요청·응답 타입이 맡는다.
- `application.port` 아래의 포트는 interface 로 정의한다. 유스케이스 입력값을 담는 record 는 interface 가 아니어도 된다. 출력 포트(`application.port.out`)의 구현체는 `adapter.out` 에만 둔다.
- `com.example.lending` 바로 아래의 패키지는 `domain`, `application`, `adapter` 셋뿐이다. `common`, `util`, `config` 같은 최상위 패키지를 새로 만들지 않고, 책임이 맞는 레이어의 하위 패키지에 둔다. `com.example.lending` 패키지에 직접 두는 클래스는 `@SpringBootApplication` 클래스(`LendingApplication`) 하나뿐이다.

### 예시

- 저장 방식이나 외부 시스템 연동이 새로 필요하면 `application.port.out` 에 interface 를 추가하고, 구현체는 `adapter.out` 아래에 둔다. 서비스는 그 interface 만 의존한다.
- 새 REST 엔드포인트는 `adapter.in.web` 에 컨트롤러와 요청·응답 record 를 두고 `application.port.in` 의 유스케이스를 호출한다. 컨트롤러는 도메인 객체를 그대로 반환하지 않고 응답 record 로 변환한다.
- 대출 가능 여부 같은 비즈니스 규칙은 서비스나 컨트롤러가 아니라 도메인 객체의 메서드(예: `Book.loanTo`)에 둔다.
