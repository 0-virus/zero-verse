# qa 작업 기록

> append-only. 기존 항목을 수정·삭제하지 않는다.

## 2026-09-06 — 팀 상태 기준선 생성

- 한 일: 기존 M0~M2 리뷰·심의 기록으로 QA 역할 상태를 초기화했다.
- 산출물: `.claude/team/qa/CLAUDE.md`, `STATE.md`, 이 파일.
- 검증: 리더가 M2 `[머지]` 미기록과 기존 governance 정본 보존을 확인한다.
- 미해결: M2 최종 독립 검증.

## 2026-09-06 — M2 독립 QA closing pass 및 M3 Delivery & Risk 검토

- 한 일: 헌법·루트/역할 지침·REQUIREMENTS FR-SETTINGS-01~04/FR-BLOG-01/NFR-04·09·PRD §7/§9/§10~§12·디자인 정본·ADR-0004·M2 worklog와 M3 심의 문서/AGENT-BRIEFS를 실제 코드·테스트·V1 스키마와 교차 대조했다.
- 독립 검증: BE 전체 실행 보고는 53 XML, 347 tests, failures/errors/skipped 0(04:53 리더 산출물)로 확인했다. OpenAPI 교정 후 `OpenApiConfigTest` XML은 3/3 통과(0/0/0, 05:04:20)였고, FE M2 집중 Vitest 두 파일은 35 tests 통과(04:59:38)했다.
- 발견: `settingsBehavior.test.tsx:782–843` stale 응답 테스트는 응답 파싱·React state 반영 전 완료 신호를 기다려 fake-pass 가능하다(blocking 테스트 증거). BLOG_004 복구 테스트 `:140–163`은 정확한 slug path를 고정하지 않는다(non-blocking). 공개 owner는 name/email/password 없이 id/nickname/profileImageUrl/bio이며 bio는 현재 계약·테스트상 위반 확정이 아니므로 privacy 확인 위험으로 남겼다.
- 교정 확인: BlogPage 404/비404 오류 분기와 비404 재시도, settings 카드 dirty/revision/generation·로드 전 잠금, ScreenPanel 정본 라벨, 아바타 90px을 읽기 검증했다. BE OpenAPI 보안/오류 문서 교정과 회귀 테스트는 targeted 통과했으나 전체 재검증은 미해결이다.
- 산출물: `qa/AGENTS.md`, `qa/M2-review.md`.
- M3 검토: 회의 `M3-20260906-categories.md`는 공개 API·DB unique/soft delete·잠금/순서·공개 글 수·초기 설정 연계 Q1~Q4가 미확정이고 상태가 `REVIEWING`/`PROPOSED`로 불일치한다. 실제 V1 categories/posts/universes와 Category/SecurityConfig/ErrorCode를 근거로 사용자 승인·정본/ADR 반영 전 구현 보류를 권고한다.
- 미해결: 최신 수정 후 FE 전체 test/lint/build, BE 전체 test/build 및 새 JAR `/v3/api-docs`, 리더 1440px/API smoke, M2 worklog `[머지]`와 PR #8 `dev` 머지. RISK-0005 HTTPS 쿠키 smoke와 RISK-0007 slug link break는 배포 전 위험이다.

## 2026-09-06 — M2 최신 홈 셸·문서 정합성 독립 재검토

- 한 일: 최신 FE diff에서 `AppRoutes → AppShell(user) → TopBar/SideNav/RightPanel` props 흐름, guest 경로, stale/BLOG_004 회귀 보강을 제품 원본 읽기 전용으로 재검토했다. 부모의 1440px 실측(내 블로그 클릭→새 slug hero, 우측 패널 제목/slug·관리/작성 링크, avatar90·URL focus·공개 이미지)도 소스·테스트와 대조했다.
- 교정 확인: `/blog/me` fallback은 `/signin`, 비로그인 TopBar는 `로그인`→`/signin`, 로그인은 실제 nickname→`/settings`; `useOptionalAuth`는 제거되고 router가 AuthUser를 AppShell에 전달한다. 이전 F-M2-07은 blocking 없이 닫힌 것으로 기록하되 최종 전체 FE 출력은 별도 확인한다.
- 문서 감사: 헌법 diff 없음, 과거 worklog/governance 결론 보존, `git diff --check` clean. 사용자 승인 범위와 M3 승인 전 구현 보류가 문서에 공존하며 M2 최종완료 허위 주장은 확인하지 않았다.
- 리더 보고 finding(non-blocking): `AGENTS.md:79`·팀 README `:35`의 연속 진행 문구는 “승인 전 준비·독립 검토만”으로 좁히면 안전하고, M3 meeting `:5`/`:113`의 `REVIEWING`/`PROPOSED` 상태가 충돌하며, M2 worklog `:935` 뒤 새 `[개발 기록]` append가 고정 섹션 순서와 어긋난다. 문서 소유권 때문에 수정하지 않았다.
- 검증: FE/BE 프로세스는 부모가 전체 gate를 실행한 뒤 종료했으나 이 컨텍스트에서 실제 exit/output을 보유하지 못했고, 현재 `build/test-results/test` XML 0개·기존 JAR 시각 04:54로 성공을 추정하지 않았다.
- 미해결: 부모의 최신 전체 FE test/lint/build와 BE `cleanTest test`/build·새 JAR `/v3/api-docs` 출력, M2 worklog `[머지]` 및 PR #8 머지 상태. M3 Q1~Q4 승인·정본/ADR 반영 전 구현 보류.

## 2026-09-06 — FE 최종 게이트 및 셸 결함 종결 대조

- 한 일: FE 제품 원본 동결 후 최신 `AppRoutes`·`AppShell`·`TopBar`·`SideNav`·`RightPanel`과 해당 테스트를 재독했다. 부모 최종 보고(21 files / 250 tests / failures·skips 0 / lint·build exit 0)를 독립 소스·계약 대조 근거로 기록했다.
- 교정 판정: stale 성공·404 실패는 response body read 이후 `act`/flush와 실제 요청 진입을 기다리도록 강화되었고, BLOG_004 복구는 `/blog/already-done` pathname을 고정했다. guest `/signin`, 로그인 nickname/defaultBlog slug, 실제 AppRoutes 셸 연결 모두 정합하여 FE 관련 blocking finding은 없다.
- 검증: 부모 1440px smoke의 내 블로그 클릭→새 slug hero, 우측 패널 title/slug·관리/작성 링크, avatar90·URL focus·공개 이미지 흐름을 소스·테스트와 대조했다. `useOptionalAuth`는 제거되었다.
- 독립 mutation 대조: `build/m2-stale-mutation.mjs`가 임시 transform에서 guard 3곳을 제거하고 원본 SHA-256 불변을 확인했으며, 늦은 성공·실패 2개 모두 의도된 exit 1을 냈다. 28개 filtered skip은 전체 FE gate skip이 아니다.
- 미해결: BE 전체 `cleanTest test`/build는 53 XML·348 tests·0/0/0으로 확인했고, 새 JAR `/v3/api-docs`는 JAR가 05:27:49에 갱신된 뒤에도 PID 8264(05:04:35 기동) 구 프로세스가 살아 있어 재기동 후 확인해야 한다. 리더 최종 smoke/worklog `[머지]`·PR #8 머지도 남았다. 문서 감사 비차단 finding 3건은 별도 보고를 유지한다.

## 2026-09-06 — M2 최종 독립 QA 승인

- 한 일: 새 JAR SHA-256 `5F9724EC3D38B7B3D9F41B00C6917B744937876A2423DCAA028390D76CE2A69B`와 PID 24560(05:33:05 기동)을 확인하고 `/v3/api-docs`·`/swagger-ui.html`을 직접 조회했다.
- 검증: M2 보호 6개 operation은 `bearerAuth`, public blog/auth register·signin·refresh·signout은 `security=[]`, 200 concrete DTO refs·오류 20개 application/json envelope refs·401 `AUTH_002`+`AUTH_004`가 모두 확인됐다. swagger는 302로 접근 가능했다. BE 53 XML/348 tests/0 failures/0 errors/0 skipped, bootJar/build exit 0, FE 21 files/250 tests 및 lint/build exit 0, stale mutation·1440px browser/API smoke도 통과했다.
- 판정: **APPROVE (confidence 96/100)**. F-M2-01 stale fake-pass, F-M2-02 BLOG_004 path, F-M2-03 OpenAPI, F-M2-07 guest/home shell finding을 모두 해소했다. M2 source/evidence blocking finding 없음.
- 잔여: RISK-0005 운영 HTTPS Secure/Strict 쿠키와 RISK-0007 slug 변경 후 이전 URL 단절은 배포 전 별도 gate다. 문서 감사 비차단 3건(연속 진행 오독 가능성, M3 `REVIEWING`/`PROPOSED`, worklog 고정 섹션 순서)은 리더 정정 기록 후 merge한다.
- 다음: M3 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현하지 않는다. 리더의 M2 worklog `[리뷰]`/`[머지]`, PR #8 `dev` 머지 및 문서 정정 기록을 확인한다.
- 추가 문서 확인: 최신 `docs/governance/README.md`는 Claude 전용 구현/분기 표현을 구현 역할·리더 배정으로 동기화했지만, 독립 3인 심의·`LOW` 외 사용자 명시 승인·과거 회의/ADR 보존 규칙은 그대로 유지한다.

## 2026-09-06 — QA 역할 진입점·M3 상태 혼재 종결 확인

- `.claude/team/qa/CLAUDE.md`에 `qa/AGENTS.md` 하위 지침 진입점을 추가했다. QA 소유 범위 밖 파일은 수정하지 않았다.
- 리더 최신 파일을 재확인했다. `docs/governance/meetings/M3-20260906-categories.md` 상단과 §7은 모두 `USER_DECISION_REQUIRED`이고 §10에 정정 사유가 보존되어 있다. M2 worklog `[리뷰]` continuation에는 과거 섹션 보존 및 최신 기준이 명시되어 문서 감사의 3개 finding은 해소되었다.
- `STATE.md`의 오래된 `REVIEWING`/`PROPOSED` 혼재 차단 문구를 제거했다. M3 Q1~Q4 사용자 승인·정본/ADR 반영 전 구현·승인 금지와 `docs/PM-M3-readiness.md`의 준비 문서 한정은 유지한다.

## 2026-09-06 — M2 PR #8 dev 머지 및 M3 인계 기록 독립 확인

- 리더 최신 `docs/worklog/M2-settings.md`의 `[머지]`를 읽어 PR #8이 base `dev`에 **MERGED**임을 확인했다. GitHub mergedAt는 `2026-09-05T20:41:12Z`(2026-09-06 05:41:12 KST), merge commit은 `4c129e20f58a6ccb9c61246d103934702516c295`, 검증 head는 `13d8debbd8c6a9396291bdf72b248610d62a29a8`이다. 과거 `[머지] 아직 없음`은 기록 보존 목적의 과거 항목으로 남아 있다.
- `.claude/team/JOURNAL.md` 최신 append, `docs/worklog/M3-categories.md`, `docs/governance/DECISION-REGISTER.md`를 독립 확인했다. M3는 계획·독립 심의 완료지만 Q1~Q4 사용자 승인 대기이며, 결정 레지스터도 `USER_DECISION_REQUIRED`다. 제품 구현·M3 승인·M4 착수 주장은 없다.
- `docs/PM-M3-readiness.md`는 M2 Git/PR 종료를 반영하고, worklog continuation·사용자 승인·정본/ADR 동기화를 M3 선행조건으로 유지한다. 하단 체크리스트의 미완료 표기는 PM 소유 준비 문서의 후속 갱신 범위이며 M3 승인으로 오인할 근거가 아니다.
- QA 판정은 변경되지 않는다: M2 **APPROVE (96/100)**, source/evidence blocking 없음. M3는 사용자 Q1~Q4 응답과 정본/ADR 반영 전 구현하지 않는다.
