# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-06 KST (M2 PR #8 dev 머지 확인·M3 선행조건)

## 현재 단계

- M0와 M1은 머지 기록이 있다.
- M2 최종 QA 판정은 **APPROVE (confidence 96/100)**다. FE 21 files/250 tests·lint/build, BE 53 XML/348 tests·0/0/0·bootJar/build, stale mutation, 새 JAR OpenAPI 및 리더 browser/API smoke가 통과했다. PR #8은 `dev`에 머지되었고 merge commit은 `4c129e20f58a6ccb9c61246d103934702516c295`(GitHub 2026-09-06 05:41:12 KST)다. 배포 전 운영 위험과 문서 후속은 남지만 blocking finding은 없다.
- M3는 카테고리 API·DB·공개 글 수·오류·초기 설정 연계 계약의 독립 심의가 완료되었고 Q1~Q4 사용자 승인 전 대기 중이며 구현하지 않는다.

## 진행 중

- `qa/M2-review.md`에 FR-SETTINGS-01~04/FR-BLOG-01, NFR-04·09/PRD/ADR 근거, 최신 셸 교정 및 문서 감사 결과를 기록했다.
- FE 최신 diff는 `AppRoutes → AppShell(user) → TopBar/SideNav/RightPanel` props 흐름이며 `/blog/me`, `제로별`, `useOptionalAuth` 의존을 제거했다. 로그인 slug·우측 패널·guest `/signin` 경계를 소스/테스트와 부모 1440px smoke 보고로 대조했다.
- stale 늦은 성공·실패 테스트는 response body read 관측, `act`, 실제 요청 진입, 후속 flush로 강화되었고 BLOG_004 복구는 `/blog/already-done` path assertion을 추가했다. FE 최종 보고는 21 files/250 tests, failures·skips 0, lint/build exit 0이다.
- BE 전체 `cleanTest test`는 `build/test-results/test` 53 XML·348 tests·failures/errors/skipped 0으로 독립 재집계했다. bootJar/build exit 0·SHA-256을 부모 로그와 대조했고, 새 JAR PID 24560(05:33:05) `/v3/api-docs`에서 보호·공개 security와 오류/DTO envelope를 직접 확인했다.
- 루트/팀 지침·운영 가이드·JOURNAL·M2 worklog를 헌법/승인/소유권/과거 기록 보존 관점에서 독립 감사했다. 헌법 diff는 없고, 연속 진행 오독·worklog 섹션 순서의 비차단 위험은 리더 정정 기록으로 해소되었다. 이전 M3 상태 표기 혼재도 리더가 상단과 §7을 `USER_DECISION_REQUIRED`로 통일하고 정정 기록을 남겼다.

## 다음 작업

1. M2 최종 판정과 FE/BE/OpenAPI/stale/browser 증거를 `qa/M2-review.md`에 기록했다.
2. 리더의 M2 worklog `[리뷰]`/`[머지]`, PR #8 `dev` 머지, JOURNAL 및 M3 계획·승인대기 기록을 독립 확인했다.
3. M3 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현을 승인하지 않고, 새 M3 심의 결과는 독립적으로만 기록한다. `docs/PM-M3-readiness.md`의 준비 문서 한정과 일부 과거 `PROPOSED` 참조는 후속 정합성 점검으로 남긴다. RISK-0005 HTTPS 쿠키·RISK-0007 slug link break는 배포 전 별도 gate다.

## 차단 요인

- M2 source/evidence blocking: 없음. PR #8 `dev` 머지와 리더의 worklog/JOURNAL 기록을 확인했으며 남은 것은 배포 전 운영 위험과 문서 후속이다.
- M3 착수 차단: 공개 API/DB unique·soft delete/잠금·순서/공개 count/오류 계약과 Q1~Q4가 미확정이다. 심의 문서 현재 상태는 `USER_DECISION_REQUIRED`이며 사용자 승인 전 구현·승인은 금지된다.

## 주요 산출물

- `qa/AGENTS.md`
- `qa/M2-review.md`
- `.claude/team/qa/WORKLOG.md`
