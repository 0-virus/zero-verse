# ADR-0003: M1 인증 토큰·세션 및 오류 계약

- 상태: `ACCEPTED`
- 날짜: 2026-07-25
- 등급: `HIGH`
- 결정 주체: 사용자
- 관련 회의록: [`M1-20260725-auth`](../meetings/M1-20260725-auth.md)
- 관련 마일스톤: M1
- 관련 문서: `docs/REQUIREMENTS.md` §NFR-04·FR-AUTH-01~05, `docs/PRD.md` §4.3·§4.4·§9.4-AB, `RISK-0002`·`RISK-0005`·`RISK-0006`

## 맥락

M1은 ZeroVerse의 인증·인가를 처음 구현한다. 다음이 정본에 정의돼 있지 않았다.

1. **이메일 중복(409)** — `ErrorCode`에 표현할 코드가 없다. `USER_002`는 닉네임 중복 전용이다.
2. **일반 Access 인증 실패(401)** — 토큰 없음·형식 오류·서명 불일치·type 무효를 나타낼 코드가 없다. 계획 초안은 `AUTH_001`(signin 실패) 재사용을 검토했으나, 이는 "로그인 실패"와 "인증 필요"를 같은 코드로 뭉개 공개 계약의 의미를 왜곡한다.
3. 토큰 수명·저장 방식·rotation 동시성 처리·쿠키 정책이 확정되지 않았다.

또한 M0가 남긴 `SecurityConfig`의 `anyRequest().permitAll()`(`RISK-0002`)을 M1에서 교체해야 한다.

## 검토한 선택지

| 선택지 | 내용 | 판정 |
|--------|------|------|
| A | `USER_004`(이메일 중복 409)·`AUTH_004`(인증 필요 401)를 NFR-04에 신설 | **채택** |
| B | 신규 코드 없이 `AUTH_001~003`과 기존 `USER_*`만 재사용 | 기각 — 이메일 중복을 표현할 코드가 없고, `AUTH_001` 재사용은 signin 실패와 인증 필요를 구분 불가하게 만든다 |
| C | 전체 보류 | 기각 — M1의 공개 API 계약이 미완성으로 남는다 |

독립 검토 3인(Product / Architecture / Delivery & Risk)이 서로 컨텍스트를 공유하지 않은 상태에서 **전원 A를 권고**했다.

## 결정

### 1. 오류 계약

`AUTH_001~003`의 기존 의미는 **변경하지 않는다**. 아래 둘만 신설한다.

| 코드 | HTTP | 적용 범위 |
|------|------|-----------|
| `AUTH_004` | 401 | Access Token 없음 / 형식·서명·type 무효 |
| `USER_004` | 409 | register 시 이메일 중복 |

`AUTH_001`은 **signin 실패 전용**이며, 이메일 없음과 비밀번호 불일치를 구분하지 않는다(계정 존재 여부 노출 방지). 401 응답은 내부 상태를 메시지로 노출하지 않는다.

### 2. 토큰

- **JJWT 0.13.0 · HS256.** Access 1시간, Refresh 2주.
- Refresh JWT **원문을 저장하지 않는다**. `jti`와 SHA-256 해시만 `refresh_tokens`에 저장하고, 갱신 시 `jti`로 row를 조회해 해시를 대조한다.
- **rotation은 row lock으로 단일 성공을 보장**한다. 동시 갱신 시 하나만 성공하고 나머지는 `AUTH_003`을 받는다.
- Access는 프론트 **메모리**, Refresh는 **HttpOnly Secure SameSite 쿠키**(PRD §4.3).

### 3. 쿠키 정책

**운영 프론트와 API를 same-site로 배치한다**(사용자 확정 2026-07-25).

- `SameSite=Strict`, path `/api/v1/auth`, 운영 `Secure=true`(local profile만 false).
- Bearer 기반 API는 CSRF off. **쿠키 인증을 쓰는 `refresh`·`signout`에는 허용 Origin 검증**을 둔다.
- cross-site 배치로 바뀌면 이 계약은 **재심의 대상**이다(`RISK-0005`).

### 4. 가입 흐름

- 가입 시 **한 트랜잭션 안에서** 기본 블로그 1개 + 미분류 카테고리(`type=DEFAULT`)를 생성한다.
- nickname 정규화 결과가 비었거나 예약어면 `blog`, `blog-2`… fallback slug를 쓴다. **suffix 포함 3~30자**를 지키고, DB unique 동시 충돌은 유한 재시도로 처리한다.
- register 응답에 토큰을 넣지 않는다. FE가 이어서 signin을 호출하되, **자동 signin만 실패하면 "계정은 생성됨"으로 안내하고 일반 signin으로 복구**시킨다(register 반복 금지).

### 5. RISK-0002 종료 조건

다음을 **모두** 충족해야 `CLOSED`로 바꾼다.

- `anyRequest().permitAll()` 잔존 없음
- `/auth/me` 및 대표 보호 API 무토큰 → 401 + `AUTH_004`
- 일반 사용자의 `/api/v1/admin/**` → 403 + `ADMIN_001`
- 공개 조회 allowlist가 **HTTP method까지** 제한됨
- 공개 경로의 쓰기 요청(POST/PATCH/DELETE) → 401
- status뿐 아니라 **공통 응답의 code·message까지** 검증

## 결과

- `docs/REQUIREMENTS.md` NFR-04에 "M1 인증 오류 코드" 표 추가.
- `docs/PRD.md` §9.4-AB에 결정 기록.
- `RISK-0005`(cross-site 배치 시 Strict 쿠키 실패), `RISK-0006`(폐기 Refresh 재사용 시 전체 세션 미폐기) 등록.
- 보안 로그는 구조화하되 **raw token·hash·secret을 기록하지 않는다**.

## 반대 논거와 잔여 위험

- 반대 논거: 요구사항 정본 개정은 공개 계약 변경이라 되돌리기 비용이 크다. MVP에서 코드 체계를 조기 확정하면 이후 세분화 요구 시 호환성 부담이 남는다.
- 완화: 최소 2개 코드만 신설하고, 세분화는 필요 시점에 별도 심의로 확장한다. 공개 소비자가 없는 현재가 변경 비용이 가장 낮다.
- 잔여 위험: 폐기 Refresh 재사용 시 **전체 세션을 폐기하지 않는다**. 탈취자가 정상 사용자보다 먼저 rotation에 성공하면 공격자 세션이 유지될 수 있다(`RISK-0006`). MVP 범위에서는 row-lock 단일 성공·폐기 토큰 거부·보안 로그로 대응하고, 전체 세션 폐기는 후속 보안 범위에서 재검토한다.
