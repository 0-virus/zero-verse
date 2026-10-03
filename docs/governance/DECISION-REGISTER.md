# 결정 레지스터

기획 심의와 ADR의 상태를 추적한다. 새로운 결정은 표의 마지막에 추가한다.

| ID | 날짜 | 제목 | 등급 | 상태 | 결정 주체 | 관련 문서 |
|----|------|------|------|------|-----------|-----------|
| ADR-0001 | 2026-07-24 | Codex 독립 기획 심의팀 운영 | HIGH | ACCEPTED | 사용자 | [ADR-0001](decisions/ADR-0001-codex-planning-council.md) |
| M0-20260725-scaffold | 2026-07-25 | M0 스캐폴딩 계획 및 비도메인 오류 코드 분류 | HIGH | APPROVED | 사용자 | [회의록](meetings/M0-20260725-scaffold.md) |
| ADR-0002 | 2026-07-25 | 비도메인 API 오류 코드 분류 | HIGH | ACCEPTED | 사용자 | [ADR-0002](decisions/ADR-0002-non-domain-error-taxonomy.md) |
| M1-20260725-auth | 2026-07-25 | M1 인증 토큰·세션 및 오류 계약 | HIGH | APPROVED | 사용자 | [회의록](meetings/M1-20260725-auth.md) |
| ADR-0003 | 2026-07-25 | M1 인증 토큰·세션 및 오류 계약 | HIGH | ACCEPTED | 사용자 | [ADR-0003](decisions/ADR-0003-m1-auth-token-session-contract.md) |
| M2-20260726-settings | 2026-07-26 | M2 설정 오류·slug 정책·E2E 게이트 | HIGH | APPROVED | **사용자**(2026-07-27 명시 승인) | [회의록](meetings/M2-20260726-settings.md) |
| ADR-0004 | 2026-07-26 | M2 설정 오류 계약(`USER_005`·`BLOG_004`)·slug 변경 정책·M2 검증 범위 | HIGH | ACCEPTED | **사용자**(2026-07-27 명시 승인) | [ADR-0004](decisions/ADR-0004-error-codes-and-slug-policy.md) |
| M3-20260906-categories | 2026-09-06 | M3 카테고리 활성 unique·잠금/순서·공개 count·시작 칩 복구 | HIGH | APPROVED | 사용자(구체 Q1~Q4 제시 후 "시작", 회의 §10) | [회의록](meetings/M3-20260906-categories.md), [M3 계획](../worklog/M3-categories.md) |
| ADR-0005 | 2026-09-06 | M3 카테고리 무결성·조회·편집·초기 설정 계약 | HIGH | ACCEPTED | 사용자(회의 §10) | [ADR-0005](decisions/ADR-0005-categories-contract.md) |
| M4-20260908-posts | 2026-09-08 | M4 게시글·에디터·이미지 업로드 준비 | BLOCKED | BLOCKED | 미결(독립 검토 완료, 사용자 결정·세부 계약 필요) | [회의록 §5~6](meetings/M4-20260908-posts.md), [M4 계획](../worklog/M4-posts.md) |
| M4-20260909-image-direction | 2026-09-09 | 비공개 S3와 글 열람권한에 따른 이미지 제공 방향·계약 보완(방향만) | HIGH | APPROVED | 사용자 `그렇게 해줘`; 상세 계약·구현은 별도 승인 | [회의록 §10](meetings/M4-20260908-posts.md), [M4 계획](../worklog/M4-posts.md) |
| M4-20260909-image-u0 | 2026-09-09 | 로컬 업로드 안전성 검증 U0만 착수 | HIGH | APPROVED | 사용자 `승인`; §12.3 로컬 harness·SDK/Tika·private bucket/CORS/브라우저 검증만, 전체 제품 계약 제외 | [회의록 §12~14](meetings/M4-20260908-posts.md), [M4 계획](../worklog/M4-posts.md) |
| M4-20260909-localstack-startup | 2026-09-09 | Windows 예약 포트 충돌을 피한 LocalStack S3 loopback 14566 기동·조회만 | LOW | APPROVED | 사용자 `응 그렇게 해줘`; U0/제품/실제 AWS/공개 게시 제외 | [회의록 §13](meetings/M4-20260908-posts.md) |
| M4-20260909-iam-check | 2026-09-09 | 비용 없는 IAM 지원 확인 및 지원 시 식별된 U0 자원 초기화·재검증 | HIGH | APPROVED(기능 미지원으로 재생성 미실행) | 사용자 `응 진행해`; bucket 2개/16개 합성 객체만, 구매·대체 서비스·제품 제외 | [회의록 §16](meetings/M4-20260908-posts.md) |
| M4-20260923-u0-alt | 2026-09-23 | SeaweedFS native 4.47을 U0 전용 무료 검증기로 설치·실측 | HIGH | APPROVED(실측 완료·전체 U0 BLOCKED) | 사용자 `승인. 내가 할 일을 알려줘.`; Java19/19·VERIFY27/28, browser18/19·VERIFY27/28, PAB501·브라우저 초과파일 TypeError 미해결 | [회의록 §17~18](meetings/M4-20260908-posts.md), [M4 기록](../worklog/M4-posts.md) |
| M4-20260923-u0-remediation | 2026-09-23 | PAB501·브라우저 초과 파일 오류 원인 조사·최소 수정·재검증 | HIGH | APPROVED(진단 수행·두 문제 미해결) | 사용자 `두 문제 해결을 먼저 수행해줄래?`; PAB handler 미구현 확인, raw HTTP403·exactACAO·close, fetch 및 XHR 모두 응답 미가독. vendor 보안 구현·지원 환경 변경은 별도 결정 | [회의록 §19](meetings/M4-20260908-posts.md), [M4 기록](../worklog/M4-posts.md) |
