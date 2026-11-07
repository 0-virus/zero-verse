# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M5 독립 최종 인수 완료 — 기능/API/자동검증/1440px 브라우저 시각 gate PASS, 전체 final acceptance PASS**다.
- BE-001/BE-002/QA-001/QA-002/DOC-001과 V-001, 초기 CTA 카피 순서 V-002는 모두 CLOSED다.
- 기능/API/자동검증 범위는 merge-ready YES이며, 실제 API fixture와 Playwright 브라우저에서 Settings Universe 6개 카드·Blog hero 7상태·refresh·계정 전환·guest를 확인했다.
- QA는 commit/push/PR/merge를 수행하지 않는다. root의 명시된 Git 마감만 남는다.

## 최종 실측

- Backend root 최신: 70 XML suites / 457 tests / failures-errors-skips `0/0/0`, `BUILD SUCCESSFUL`, `bootJar` 성공.
- Frontend root 최신: 31 files / 338 tests, lint exit 0, production build exit 0.
- 브라우저: Playwright + 시스템 Chrome, CSS viewport `1440×1000`, `deviceScaleFactor=1`, 실제 API `localhost:18080`, FE `localhost:5173`; 최종 QA child exit 0, evidence `failures=[]`.
- 원본: `docs/design/`의 네 파일(`DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`, `support.js`) 복구를 확인했고, Pages 원본의 Blog/Settings 화면을 실제 렌더 확인했다.
- 보조 keyboard: guest Blog Tab 순회에서 검색 input과 hero `/signin` link에 포커스 도달.

## 산출물

- `qa/M5-review.md`: 1차 finding, 수정 루프 closure, 최종 브라우저 evidence와 PASS 판정.
- `qa/m5-visual-qa.mjs`: QA 전용 재현 harness. fixture 계정/토큰을 출력·기록하지 않으며 artifact는 Git 무시 `build/m5-visual-qa/`에 둔다.
- 최종 artifact: `build/m5-visual-qa/evidence.json`, 원본/제품 Settings·Blog·7 hero 상태·두 계정·guest PNG.

## 다음 작업

1. root가 최신 제품 파일과 테스트 출력, QA 기록을 직접 확인한다.
2. root가 승인된 파일만 commit/push/PR/merge하고 M5 `[머지]`를 기록한다.
3. QA는 root가 추가로 요청할 때만 재검토한다.

## 차단 요인

- 없음. V-001 시각 원본 부재 차단은 원본 복구 및 최종 실제 브라우저 대조로 해소됐다.

## 갱신 시각

- 2026-11-07 KST — 실제 API fixture/Playwright 최종 run 및 keyboard 보조 검증 후.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했고 제품 코드·타 역할 기록·governance·마일스톤 worklog·Git을 변경하지 않았다.
