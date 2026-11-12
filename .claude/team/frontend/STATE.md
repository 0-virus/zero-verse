# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-12 14:05:00 KST

## 현재 단계

- M10 closeout Task3 **구현·자체 검증 완료(GREEN)**다. 브라우저 인수 실패 원인을 현재 격리 런타임에서 추가 진단했으며, 제품 소스 변경 없이 root/QA에 런타임·하네스 수정 근거를 인계했다. M10 전체 PASS/complete가 아니다.
- 근거 정본은 REQUIREMENTS NFR-09, PRD §11~§12, 승인 회의 `docs/governance/meetings/M10-20261112-closeout.md`, `docs/design/AGENTS.md` 및 `DESIGN-SYSTEM.md`다. 새 dependency, API/server contract, 디자인 정본, bundle optimization은 추가하지 않았다.

## 구현·테스트

- `frontend/src/features/post/PostEditor.tsx`: 임시저장은 빈 제목/본문을 허용하고, 발행 시 `title.trim()` 길이 1~200 및 TipTap 본문 `textContent` 또는 본문 이미지 존재를 검사한다. 실패하면 visible `role="alert"` 문구를 표시하고 `onSave`를 호출하지 않는다. 이미지 판정은 thumbnail이 아닌 TipTap 본문 image node 수집 결과다.
- `frontend/src/features/post/PostEditor.test.tsx`: 빈 draft 허용, 빈/공백/201자 제목 차단, 정확히 200자 제목 허용, 빈 text+image 본문 차단, 대표 이미지-only 차단, 본문 image-only 발행 허용을 실제 컴포넌트 동작으로 검증했다. 새 검증 때문에 기존 저장 실패 경로가 막히지 않도록 해당 회귀에 본문 입력을 추가했다.
- TDD RED: `cd frontend && npm test -- --run src/features/post/PostEditor.test.tsx --maxWorkers=1` → exit 1, 16 tests 중 3 failed/13 passed. 실패는 구현 전 빈 제목, 공백·201자 제목, 빈 본문 안내 부재였다.
- Focused GREEN: 같은 명령 → exit 0, 1 file / 18/18 passed(정확히 200자 positive boundary와 thumbnail-only negative 보강 포함).
- Full GREEN: `cd frontend && npm test -- --run --maxWorkers=1` → exit 0, 51 test files / 472 tests passed.
- Lint/build: `cd frontend && npm run lint && npm run build` → exit 0. Oxlint 통과, Vite 149 modules. JS 958.38 kB(gzip 288.87 kB) 및 500 kB 초과 chunk warning은 기존 비차단 경고로 남겼고 code splitting은 구현하지 않았다.

## 인계·남은 위험

- Task4/root/독립 QA 인계: 1440px에서 13개 canonical 화면(Main, Blog, Post Detail, Write/Edit, Signin/Signup, Setup, Settings Basic/Universe/Posts, Search, Notifications, Admin List/Detail)의 실제 route/API 상태·screenshot을 최종 source 기준으로 확인한다. 이 Task3에서는 browser screenshot을 실행하지 않았다.
- 같은 인계에서 TopBar profile/notification popover, category ArrowUp/ArrowDown, editor/DraftPicker, admin role/status/delete confirmation의 기존 명시 동작을 키보드로 관찰한다. 새 focus-trap 계약을 추가하지 않았으므로 Modal/DraftPicker/TopBar/category/admin keyboard 제품 코드는 수정하지 않았다.
- 남은 concern은 browser/13-screen evidence 미실행과 958.38 kB bundle warning이며, 둘 다 이 Task3 구현의 테스트 실패는 아니다. 타 역할 dirty 변경과 사용자 파일/DB/upload는 보존했다.

## 소유권·통합

- 변경한 제품 파일은 위 frontend 두 파일뿐이고 역할 기록은 이 `STATE.md`와 `WORKLOG.md`다. SDD Task3 보고서는 부모가 지정한 `.superpowers/sdd/M10-closeout/task-3-report.md`에 기록했다.
- child는 Git stage/commit/branch/push/merge를 하지 않았다. root가 원자 커밋·독립 review package를 수행한다.

## M10 실제 브라우저 인수 진단 인계 (2026-11-12 14:05 KST)

- 격리 런타임 `127.0.0.1:5180`에서 합성 관리자 로그인 DOM에 상단 검색 submit과 로그인 submit이 함께 존재한다. QA `browserSignIn`의 `page.locator('form button[type="submit"]').first()`가 첫 번째 검색 버튼을 클릭해 로그인 세션을 만들지 못한다. 이는 frontend 제품 결함이 아니라 `qa/m10-acceptance.mjs` selector 결함이며 QA 역할에 수정 제안했다.
- 현재 `frontend/dist` 번들은 `frontend/src/lib/apiClient.ts`의 기본값 `http://localhost:8080`으로 빌드되어 있다. M10 FE static server는 `127.0.0.1:5180`에서 dist만 제공하므로 API `18090`으로 연결되지 않고, 실제 browser request가 `http://localhost:8080/api/v1/... net::ERR_CONNECTION_REFUSED`를 기록했다. runtime plan §2.1/§2.2의 explicit `VITE_API_BASE_URL=http://127.0.0.1:18090`로 M10 전용 FE build를 다시 만들 책임은 root runtime에 있다. 기존 FE tests와 local default 계약은 변경하지 않는다.
- 검증: Playwright headless 1440×1000에서 `/signin`, `/blog/m10-owner-301b5f83`, `/blog/m10-owner-301b5f83/8`, `/`를 확인했다. FE source/dist/role files 외 제품 파일은 편집하지 않았고 사용자 DB `13307`, `.local-data`, 기존 업로드에 접근하지 않았다.
- 다음: root가 QA selector fix와 explicit runtime FE rebuild/provenance 재생성 후 13개 화면·keyboard 인수를 재실행한다. frontend 역할의 제품 코드 수정은 현재 필요하지 않다.
