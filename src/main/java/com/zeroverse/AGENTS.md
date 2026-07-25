# com.zeroverse (백엔드)

## Purpose

ZeroVerse Blog MVP 백엔드. Spring Boot 3.5 / Java 21 / MySQL 8.x / Flyway. base package는 `com.zeroverse`로 고정한다(PRD §2.2).

## 현재 구조 (M0)

```text
com/zeroverse/
├─ ZeroverseServerApplication.java   # @SpringBootApplication + @EnableJpaAuditing (NFR-06 — 여기서만 선언)
├─ common/
│  ├─ entity/    BaseEntity, BaseSoftDeleteEntity
│  ├─ exception/ ErrorCode, BusinessException, GlobalExceptionHandler
│  └─ response/  ApiResponse, ErrorResponse, PageResponse
└─ config/       SecurityConfig, CorsProperties, OpenApiConfig
```

도메인 패키지(`user`, `blog`, `post`, ...)는 각 마일스톤에서 `controller → service → repository` 레이어로 추가한다(PRD §2.2).

## 규칙

- **공통 응답**: 모든 API는 `ApiResponse`로 감싼다. `success`/`data`/`error`/`timestamp` 네 키는 성공·실패 모두 직렬화한다 — `NON_NULL` 금지(PRD §4.1).
- **페이징**: offset 페이징은 `PageResponse.from(Page<T>)`(PRD §4.2).
- **에러 코드**: `ErrorCode` enum에만 정의한다. 도메인 오류는 도메인 prefix, 비도메인은 `VALIDATION_001`(400) / `COMMON_404` / `COMMON_500`(ADR-0002). **여기에 없는 코드를 임의로 만들지 말고 REQUIREMENTS NFR-04를 먼저 개정한다.**
- **500 응답**: 고정 외부 메시지만 반환한다. stack trace·SQL·내부 경로·원본 예외 메시지를 노출하지 않는다(ADR-0002).
- **명명**: DB 컬럼 snake_case, Java 필드 camelCase(NFR-06). JPA 숫자 필드는 래퍼 타입.
- **Auditing**: 공통 엔티티는 `BaseEntity` 상속. soft delete가 필요하면 `BaseSoftDeleteEntity`. `@EnableJpaAuditing`을 다른 곳에 중복 선언하면 `jpaAuditingHandler` 빈이 충돌한다.
- **마이그레이션**: `db/migration/V1__init.sql`은 공유 환경 적용 후 수정 금지. V2 이상 forward migration만 추가한다(RISK-0003).

## 보안 설정 — M1에서 교체 완료 (RISK-0002 CLOSED)

`SecurityConfig`는 `anyRequest().authenticated()` + `/api/v1/admin/**`는 `hasRole("ADMIN")`이다. M0의 임시 `anyRequest().permitAll()`은 M1(PR #7)에서 제거했고 `RISK-0002`는 CLOSED다.

- **공개 경로는 HTTP method까지 제한**한다. 경로만 열면 나중에 같은 경로에 쓰기 API가 붙는 순간 인증 없이 노출된다.
- 새 API를 추가할 때 **기본값은 인증 필요**다. 공개가 필요하면 method까지 명시해 allowlist에 넣고, `SecurityAccessControlTest`에 401/403 회귀 케이스를 함께 추가한다.
- 필터 체인의 401/403은 `GlobalExceptionHandler`를 타지 않는다 — `SecurityErrorResponder`가 공통 응답 계약(`AUTH_004`/`ADMIN_001`)으로 직접 JSON을 쓴다.

## 테스트

- DB가 필요한 테스트는 `com.zeroverse.support.MySqlTestSupport`를 상속한다. Testcontainers `mysql:8.4`가 정본이며 **Docker 미가용을 이유로 skip하지 않는다**(PRD §9.4-Y).
- DB가 필요 없는 웹 계층 테스트는 DataSource·JPA·Flyway 자동설정을 제외한다(`GlobalExceptionHandlerTest` 참고).
- 테스트 전용 엔티티는 base package **바깥**(`zeroverse.testsupport.*`)에 두고 테이블은 `db/test-migration`에서만 만든다. 운영 스키마를 오염시키지 않는다.
- placeholder·`skip`·stub 금지(PRD §12).
