# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-12 13:09:31 KST

## 현재 단계

- M10 closeout Task3 **구현·자체 검증 완료(GREEN)**다. 이 역할의 범위는 `PostEditor` 발행/임시저장 입력 검증과 해당 회귀 테스트이며, M10 전체 PASS/complete가 아니다.
- 근거 정본은 REQUIREMENTS NFR-09, PRD §11~§12, 승인 회의 `docs/governance/meetings/M10-20261112-closeout.md`, `docs/design/AGENTS.md` 및 `DESIGN-SYSTEM.md`다. 새 dependency, API/server contract, 디자인 정본, bundle optimization은 추가하지 않았다.

## 구현·테스트

- `frontend/src/features/post/PostEditor.tsx`: 임시저장은 빈 제목/본문을 허용하고, 발행 시 `title.trim()` 길이 1~200 및 TipTap 본문 `textContent` 또는 본문 이미지 존재를 검사한다. 실패하면 visible `role="alert"` 문구를 표시하고 `onSave`를 호출하지 않는다. 이미지 판정은 thumbnail이 아닌 TipTap 본문 image node 수집 결과다.
- `frontend/src/features/post/PostEditor.test.tsx`: 빈 draft 허용, 빈/공백/201자 제목 차단, 정확히 200자 제목 허용, 빈 text+image 본문 차단, 본문 image-only 발행 허용을 실제 컴포넌트 동작으로 검증했다. 새 검증 때문에 기존 저장 실패 경로가 막히지 않도록 해당 회귀에 본문 입력을 추가했다.
- TDD RED: `cd frontend && npm test -- --run src/features/post/PostEditor.test.tsx --maxWorkers=1` → exit 1, 16 tests 중 3 failed/13 passed. 실패는 구현 전 빈 제목, 공백·201자 제목, 빈 본문 안내 부재였다.
- Focused GREEN: 같은 명령 → exit 0, 1 file / 17/17 passed(정확히 200자 positive boundary 보강 포함).
- Full GREEN: `cd frontend && npm test -- --run --maxWorkers=1` → exit 0, 51 test files / 471 tests passed.
- Lint/build: `cd frontend && npm run lint && npm run build` → exit 0. Oxlint 통과, Vite 149 modules. JS 958.38 kB(gzip 288.87 kB) 및 500 kB 초과 chunk warning은 기존 비차단 경고로 남겼고 code splitting은 구현하지 않았다.

## 인계·남은 위험

- Task4/root/독립 QA 인계: 1440px에서 13개 canonical 화면(Main, Blog, Post Detail, Write/Edit, Signin/Signup, Setup, Settings Basic/Universe/Posts, Search, Notifications, Admin List/Detail)의 실제 route/API 상태·screenshot을 최종 source 기준으로 확인한다. 이 Task3에서는 browser screenshot을 실행하지 않았다.
- 같은 인계에서 TopBar profile/notification popover, category ArrowUp/ArrowDown, editor/DraftPicker, admin role/status/delete confirmation의 기존 명시 동작을 키보드로 관찰한다. 새 focus-trap 계약을 추가하지 않았으므로 Modal/DraftPicker/TopBar/category/admin keyboard 제품 코드는 수정하지 않았다.
- 남은 concern은 browser/13-screen evidence 미실행과 958.38 kB bundle warning이며, 둘 다 이 Task3 구현의 테스트 실패는 아니다. 타 역할 dirty 변경과 사용자 파일/DB/upload는 보존했다.

## 소유권·통합

- 변경한 제품 파일은 위 frontend 두 파일뿐이고 역할 기록은 이 `STATE.md`와 `WORKLOG.md`다. SDD Task3 보고서는 부모가 지정한 `.superpowers/sdd/M10-closeout/task-3-report.md`에 기록했다.
- child는 Git stage/commit/branch/push/merge를 하지 않았다. root가 원자 커밋·독립 review package를 수행한다.
