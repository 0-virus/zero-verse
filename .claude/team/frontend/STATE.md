# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 배포 심의·실행 기준은 `docs/governance/meetings/X-20261113-deployment.md`를 본다.

마지막 갱신: 2026-11-17 14:32:00 KST

## 현재 단계

- M10 frontend는 **DEV INTEGRATED / COMPLETE**다.
- X-deployment A-v2 사용자 승인 후 T3 HIGH 프론트 범위까지 구현했다. production same-origin API base와 S3 직접 업로드 intent→raw PUT→complete, local dev/test fallback, 계정 전환·abort·재시도 경계를 검증한다.
- 운영 후속 UI 보완으로 인증 사용자의 TopBar 프로필 메뉴에 FR-AUTH-03 로그아웃 액션을 연결했다. 기존 `AuthContext.signout()`의 서버 Refresh 폐기·로컬 세션 정리를 재사용하고 성공/실패 모두 `/signin`으로 이동한다.

## 진행한 작업

- `frontend/src/lib/apiBase.ts`: explicit API base trim/트레일링 슬래시 정규화, dev/test localhost 기본값 주입 경계, production HTTPS/loopback/userinfo/path/query/hash 검사를 추가했다.
- `frontend/src/lib/apiClient.ts`: production 상대 API base, abort signal 전달, tokenVersion(401 single-flight)과 분리된 auth session generation 조회를 유지한다. 정상 refresh는 tokenVersion만 증가하고, 명시 login/logout/account switch/expiry와 stale refresh 차단만 session generation을 변경한다.
- `frontend/src/features/upload/uploadTransport.ts`: production 기본 S3/dev·test 기본 local, 명시 `local|s3`, production local 차단 규칙을 제공한다.
- `frontend/src/features/upload/uploadApi.ts`: SHA-256 padded base64 presign 요청, HTTPS AWS S3 signed PUT(앱 Authorization/cookie·browser-controlled Host/Content-Length 제외, redirect error·no-referrer), idempotent complete, PUT 412·network ambiguity 제한 복구, complete 1회 재시도, intent UUID·요청 MIME까지 포함한 canonical 응답 검증, abort/auth session guard를 구현했다. 기존 multipart local 경로와 ManagedImage canonical consumer는 유지한다.
- `frontend/vite.config.ts`: API base와 `VITE_UPLOAD_TRANSPORT=local` production build를 command 전체(`--mode staging` 포함)에서 fail-fast 한다.
- `frontend/src/features/upload/uploadApi.test.ts`, `uploadTransport.test.ts`, `src/test/apiClient.test.ts`, 기존 API base/client 회귀: 실제 fetch 순서·서명 헤더·raw exact File body·S3에 앱 인증정보 미전송·redirect/referrer guard·412/network/complete retry·intent id/MIME mismatch·401 refresh success·stale refresh/account switch·abort를 검증한다.
- `frontend/src/AGENTS.md`: helper/transport ownership을 문서에 반영했다.
- `frontend/src/components/layout/TopBar.tsx`: `useOptionalAuth()`의 `signout`을 인증된 프로필 메뉴에 연결했다. 요청 중 disabled/`aria-busy`로 중복 클릭을 막고 완료 뒤 `/signin`으로 replace 이동한다.
- `frontend/src/test/TopBar.test.tsx`: guest 숨김, 성공 이동, 요청 중 중복 차단, 서버 오류 후 이동을 검증한다.
- `frontend/src/test/logoutBehavior.test.tsx`: 실제 `AuthProvider`·메모리 Access Token·mock fetch로 signout 204/500의 user/access clear와 `/signin` 이동을 검증한다.

## 검증

- focused RED: `build/x-deployment-fe-low-20261113/focused-red.log` — 기존 hardcoded localhost API와 resolver를 기준으로 production 상대 URL·same-origin resolver 회귀가 실패했다(exit 1).
- focused GREEN: `build/x-deployment-fe-low-20261113/focused-green.log` — 4 files / 34 tests PASS, exit 0.
- full test: `build/x-deployment-fe-low-20261113/full-test.log` — 53 files / 485 tests PASS, exit 0.
- lint: `build/x-deployment-fe-low-20261113/lint.log` — `oxlint` exit 0.
- build: `build/x-deployment-fe-low-20261113/build.log` — TypeScript/Vite exit 0, Vite 150 modules. 기존 500 kB 초과 chunk warning은 비차단으로 남겼다.
- production bundle: `build/x-deployment-fe-low-20261113/production-bundle-local-api.log` — `NO_LOCAL_API_ORIGIN`, exit 0.
- invalid staging build: `build/x-deployment-fe-low-20261113/production-invalid-staging.log` — `VITE_API_BASE_URL=http://localhost:8080 npm run build -- --mode staging`가 exit 1로 차단됐다.
- T3 focused: `build/x-deployment-fe-high-20261113/focused-test.log` — **2 files / 23 tests PASS**, exit 0.
- T3 full test: `build/x-deployment-fe-high-20261113/full-test.log` — **54 files / 494 tests PASS**, exit 0.
- T3 lint/build: `build/x-deployment-fe-high-20261113/lint.log`, `build.log` — `oxlint`·TypeScript/Vite exit 0, Vite 151 modules. 기존 >500 kB chunk warning은 비차단이다.
- production transport guard: `build/x-deployment-fe-high-20261113/production-invalid-upload-transport.log` — `VITE_UPLOAD_TRANSPORT=local npm run build -- --mode staging`가 exit 1로 차단됐다.
- production bundle: `build/x-deployment-fe-high-20261113/production-bundle-local-api.log` — `NO_LOCAL_API_ORIGIN`, exit 0.
- T3 review-fix focused: `build/x-deployment-fe-high-20261113/focused-test-r2.log` — **3 files / 40 tests PASS**, exit 0. Includes local/S3 presign·complete 401 refresh success and stale refresh/account-switch guard.
- T3 review-fix full: `build/x-deployment-fe-high-20261113/full-test-r2.log` — **54 files / 499 tests PASS**, exit 0.
- T3 review-fix lint/build: `lint-r2.log`, `build-r2.log` — `oxlint`·TypeScript/Vite exit 0, Vite 151 modules. Existing >500 kB chunk warning remains non-blocking.
- T3 review-fix production checks: `production-invalid-upload-transport-r2.log` exit 1 as expected for production local transport; `production-bundle-local-api-r2.log` records `NO_LOCAL_API_ORIGIN`.
- logout focused: `npm test -- --run src/test/TopBar.test.tsx src/test/logoutBehavior.test.tsx --maxWorkers=1` → **2 files / 7 tests PASS**, exit 0.
- focused clean rerun: `build/logout-20261117/focused-tests-clean.log` — `TopBar.test.tsx` + `logoutBehavior.test.tsx`, **2 files / 7 tests PASS**, exit 0; deferred resolver wrapped in `act`, no act warning.
- logout full: `npm test -- --run --maxWorkers=1` → **55 files / 504 tests PASS**, exit 0.
- logout lint/build: `npm run lint` exit 0; `npm run build` TypeScript/Vite exit 0, **151 modules**. 기존 500 kB 초과 chunk warning은 비차단이다.
- 모든 검증은 사용자 DB·`.local-data`·AWS·실제 배포 대상에 접근하지 않았다.

## 다음 작업

- root가 logout diff를 독립 검토하고 필요한 QA 기록·커밋을 판단한다.

## 차단 요인

- 없음. 실제 브라우저 1440px 시각 검증과 운영 재배포는 root의 통합/배포 범위에서 판단한다.

## 소유권·통합

- 이번 변경은 `frontend/src/components/layout/TopBar.tsx`, `frontend/src/test/TopBar.test.tsx`, `frontend/src/test/logoutBehavior.test.tsx`, `.claude/team/frontend/STATE.md`, `.claude/team/frontend/WORKLOG.md`만 frontend 역할이 편집했다. Git stage/commit/branch/push/merge는 수행하지 않았다.
