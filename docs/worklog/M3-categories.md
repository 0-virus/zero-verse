# M3 — 카테고리

- 범위: FR-CAT-01~05, PRD §5.4·§7·§9-H/R·§10.
- 단일 기록 담당: 리더. 구현은 backend/frontend, 독립 검토는 QA·리더.
- 현재 단계: **계획·독립 심의 완료, Q1~Q4 사용자 승인 대기. 제품 구현 전.**

## [계획]

### 2026-09-06 · M2 완료 후 다음 마일스톤 인계

- 선행 완료: M2 PR #8이 dev에 머지됐다(`4c129e20f58a6ccb9c61246d103934702516c295`, GitHub 05:41:12 KST). 루트가 검증 HEAD와 dev 제품 tree 일치를 확인했다. M2 BE348/FE250, 독립 QA 및 실제 smoke는 M2 로그에 기록돼 있다.
- `$brief` 대조: BE/FE 종료 검증, QA 승인, PM의 M3/M4 준비가 완료됐다. 역할의 미머지 스냅샷을 실제 Git과 일치시키며 다음 구현 병목은 사용자 결정이다.
- 준비안: [M3 카테고리 심의](../governance/meetings/M3-20260906-categories.md). Product/Architecture 조건부 찬성, Delivery & Risk 선행조건 보류, 최종 HIGH. Q1~Q4를 사용자에게 각각 제시했으며 승인으로 간주하지 않는다.
- 승인 후 순서(권고): ① BE V2·도메인·실제 MySQL 무결성/경쟁 검증 → ② 공개/보호 API·count/삭제 이동·OpenAPI → ③ FE 단일 리스트/인라인·타입·순서·공개 패널/시작 칩 API 연동 → ④ QA 교차 계약·실제 1440px·전체 회귀 → ⑤ 커밋/PR/리뷰/머지 후 M4 즉시 연결.
- 병렬 경계: 승인된 계약 아래 독립 테스트·디자인 대조 준비는 병렬 가능하다. FE의 실제 연동은 BE 계약/검증 결과를 소비하며 같은 파일을 공유 작성하지 않는다. Git/마일스톤 로그/governance는 리더만 작성한다.
- 최소 구현: 기존 Category/Blog·PageResponse·apiClient·화면 자산을 재사용한다. 새 DnD 라이브러리, Post/Universe CRUD, 0 count stub, setup API 확장, 낙관 버전 필드는 권고안에 포함하지 않는다.
- 필수 확인: active-only unique migration과 재사용/복구, 잠금 우회·같은 blog/부모·전체 형제 배열, 실제 posts 이동, root 페이지 경계, 조회자별 count·관계 방향·draft 비노출, setup 부분 실패 복구. 상세 오류/DTO는 승인된 회의 계약을 그대로 정본에 반영한다.
- M4 인계: category_id 쓰기는 같은 blog lock·활성 재조회에 참여해야 한다. Post 기능을 M3로 앞당기지 않으며 M4 시작 전 승인된 카테고리 접근 predicate와 bulk 갱신의 영속성 컨텍스트 처리를 대조한다. 준비 자료 `docs/PM-M4-readiness.md`는 로컬 참고이고 승인 정본이 아니다.

## [개발 기록]

- 제품 구현 없음. 사용자 승인 후 dev에서 `feature/M3-categories`를 분기하고 실제 변경·테스트를 timestamp와 함께 기록한다.

## [이슈·결정]

- [회의록 M3-20260906-categories](../governance/meetings/M3-20260906-categories.md): **USER_DECISION_REQUIRED**.
- 미확정 Q1 활성 unique/재사용·forward 복구, Q2 불변 타입·삭제·last-write-wins, Q3 공개 count/DTO/오류·미분류 이동, Q4 시작 칩 부분 성공 복구. 승인 전 REQUIREMENTS/PRD의 새 계약을 확정하지 않는다.
- 과거 승인 ADR-0002/0003/0004와 의미가 겹치는 기존 오류/보안 규칙은 유지한다. M3 신규 ADR은 실제 결정 뒤 작성한다.

## [리뷰]

- 제품 리뷰 전. 독립 기획 심의 완료가 제품 DoD 승인을 뜻하지 않는다.

## [머지]

- 없음. M3 미완료이며 후속 M4 구현도 시작하지 않았다.
