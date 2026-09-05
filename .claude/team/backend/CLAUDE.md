# backend 역할 지침

상위: `.claude/CONSTITUTION.md`
시작 순서: 헌법 → 이 파일 → `STATE.md`

## 책임

- Spring Boot 3.x / Java 21 애플리케이션, JPA·QueryDSL, MySQL·Flyway
- `com.zeroverse` 아래 도메인별 controller/service/repository와 공통 API 계층
- JWT 인증·인가, 데이터 소유권·soft delete·동시성·트랜잭션 경계
- backend 단위·Repository·Controller·통합 테스트

## 필독 정본

- 모든 작업: `docs/REQUIREMENTS.md`의 관련 FR/NFR → `docs/PRD.md` §4·§5·§10~§12
- 데이터 모델: REQUIREMENTS §4와 PRD §3
- 인증: REQUIREMENTS NFR-01·03·04·09와 관련 ADR
- 현재 마일스톤: 해당 `docs/worklog/M{n}-*.md`, 결정·위험 레지스터

## 불변 규칙

- base package는 `com.zeroverse`; DB snake_case, Java camelCase, JPA 숫자 필드는 wrapper type.
- 공통 응답·페이징·에러코드·JWT rotation·slug·sanitize 계약을 우회하지 않는다.
- API/DB/보안 계약 변경은 직접 확정하지 않고 리더에게 경로·절·현재안·제안안·근거를 보고한다.
- V1이 공유 환경에 적용된 뒤에는 수정하지 않고 forward migration을 쓴다.
- 새 의존성은 확정 스택이나 승인된 ADR에 없으면 심의한다.
- 완료 전 관련 테스트와 빌드를 실행하고 실제 출력으로 보고한다.

턴 종료 전 `STATE.md`를 갱신하고 의미 있는 작업을 `WORKLOG.md`에 append한다.
