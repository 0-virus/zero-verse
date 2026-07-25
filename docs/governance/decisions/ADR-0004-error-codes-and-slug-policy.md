# ADR-0004: M2 설정 오류 코드와 slug 변경 정책

- 상태: `ACCEPTED`
- 날짜: 2026-07-26
- 등급: `HIGH`
- 결정 주체: 사용자
- 관련 회의록: [`M2-20260726-settings`](../meetings/M2-20260726-settings.md)
- 관련 마일스톤: M2
- 범위: M2 사용자·블로그 설정 도메인, 초기 설정 오류 계약, slug 변경 정책
- 관련 문서: `docs/REQUIREMENTS.md` FR-SETTINGS-02·03·04, NFR-04; `docs/PRD.md` §4.4·§4.5·§5.2·§7·§9-J·§9-K

## 맥락

M2는 사용자 비밀번호 변경, 블로그 설정 변경과 초기 설정을 구현한다. 다음 공개 계약과 정본 충돌을 구현 전에 확정해야 했다.

1. **현재 비밀번호 불일치(FR-SETTINGS-02)** — 이미 인증된 사용자가 비밀번호를 변경할 때 제출한 현재 비밀번호가 일치하지 않는 실패다. signin 실패 전용 `AUTH_001`이나 Access 인증 필요 `AUTH_004`를 재사용하면 인증 흐름과 설정 검증의 복구 의미가 섞인다.
2. **initial-setup 완료 후 재호출(FR-SETTINGS-04)** — 1회성 상태 전이가 이미 끝난 Blog에 대한 재요청이며 409 상태 충돌이다. `BLOG_002`는 slug 중복, `BLOG_003`은 slug 형식 오류로 의미가 고정돼 있어 어느 코드도 이 상태를 표현하지 않는다.
3. **slug 변경 정책과 디자인 카피 충돌** — 승인 요청에서 `FR-BLOG-03`으로 지칭된 slug 변경 정책은 현 REQUIREMENTS 정본에는 그 번호가 없고, 실제 근거는 **FR-SETTINGS-03**이다. 설정 API는 사용자의 명시적인 `url_slug` 변경을 허용하지만 초기 설정 디자인은 “나중에 변경할 수 없어요”라고 안내했다. 저장·수정 허용 정책은 REQUIREMENTS가, 시각·카피는 디자인 정본이 담당하므로 사용자 결정으로 둘을 정합화해야 했다.

## 검토한 선택지

| 선택지 | 내용 | 판정 |
|--------|------|------|
| A/A/B | `USER_005`·`BLOG_004` 신설, slug 변경 허용 유지·카피 개정, M2 E2E runner 미도입 | **채택** |
| 기존 코드 재사용 | `AUTH_*` 또는 `BLOG_002/003`에 M2 실패 의미를 추가 | 기각 — 기존 계약과 FE 복구 분기의 의미를 오염시킨다 |
| slug 불변화 | 디자인 카피에 맞춰 initial-setup 이후 slug 변경을 금지 | 기각 — 공개 API와 비즈니스 규칙을 축소하고 초기 오입력 교정을 막는다 |
| M2 runner 도입 | Playwright/Cypress를 M2 완료 조건으로 추가 | 기각 — 범위를 늘리면서도 로컬 HTTP로 운영 Secure/Strict 쿠키 계약을 입증하지 못한다 |

독립 검토 3인(Product / Architecture / Delivery & Risk)이 서로 컨텍스트를 공유하지 않은 상태에서 전원 Q1=A, Q2=A, Q3=B를 권고했고, 사용자가 2026-07-26 승인했다.

## 결정

### 1. 설정 오류 계약

| 코드 | HTTP | 적용 범위 |
|------|------|-----------|
| `USER_005` | 400 | FR-SETTINGS-02 비밀번호 변경에서 현재 비밀번호 불일치 |
| `BLOG_004` | 409 | FR-SETTINGS-04 initial-setup 완료 후 재호출 |

- `AUTH_001`은 signin 실패 전용, `AUTH_004`는 Access 인증 필요 전용으로 유지한다.
- `BLOG_002`는 slug 중복, `BLOG_003`은 slug 형식 오류 전용으로 유지한다.
- 따라서 현재 비밀번호 불일치는 인증 재시도나 signin 이동이 아닌 필드 재입력으로, 완료된 initial-setup 재호출은 slug 수정 재시도가 아닌 setup 이탈로 복구할 수 있다.

### 2. slug 변경 정책

- FR-SETTINGS-03의 명시적 slug 변경 허용을 유지한다.
- nickname 변경은 기존 slug를 자동 변경하지 않는다.
- 초기 설정 힌트는 `영문 소문자·숫자·하이픈, 3~30자. 설정에서 나중에 변경할 수 있어요.`로 개정한다.
- slug 이력·alias·기존 URL redirect는 M2 범위에 추가하지 않는다. 기존 공개 URL 단절 가능성은 RISK-0007로 추적한다.

### 3. M2 검증 범위

- Playwright/Cypress 등 새 E2E runner를 M2에 도입하지 않는다.
- MockMvc·Testing Library routed integration·1440px 시각 대조를 M2 게이트로 수행한다.
- 실제 HTTPS에서의 Refresh 쿠키 smoke/E2E는 RISK-0005의 최초 배포 전 차단 게이트로 유지한다.

## 결과

- 신규 migration이나 V1 schema 변경은 필요하지 않다. 기존 User·Blog 필드와 `is_setup_completed` 상태를 사용한다.
- REQUIREMENTS NFR-04, PRD §4.4·§7·§9와 디자인 원본·요약의 카피를 동기화한다.
- initial-setup 동시 요청은 정확히 1회 성공하고 나머지는 `BLOG_004`가 되도록 구현·테스트한다.
- 서버 확인 전 정적인 `사용 가능 ✓` 표시로 unique를 확정하지 않는다.

## 반대 논거와 잔여 위험

- 반대 논거: 오류 코드 세분화는 공개 계약의 유지비를 늘리고, slug 변경 허용은 이미 공유된 URL을 끊을 수 있으며, runner 미도입은 실제 브라우저 결함 발견을 늦출 수 있다.
- 완화: 기존 코드 의미를 보존하는 최소 2개 코드만 추가한다. slug 변경 후 링크 단절은 RISK-0007로 추적하며, 운영 쿠키 검증은 제거하지 않고 RISK-0005의 최초 배포 전 차단 게이트로 유지한다.

## 관련

- [`ADR-0003: M1 인증 토큰·세션 및 오류 계약`](ADR-0003-m1-auth-token-session-contract.md) — 복구 의미별 전용 오류 코드와 기존 코드 의미 보존의 선례
- [`RISK-0007`](../RISK-REGISTER.md) — slug 변경 후 기존 공개 URL 단절 위험
- [`M2 기획 심의 회의록`](../meetings/M2-20260726-settings.md)
