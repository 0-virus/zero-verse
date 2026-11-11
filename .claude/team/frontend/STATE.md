# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-11 15:04:17 KST

## 현재 단계

- M9 Task3 frontend 관리자 guard·목록·상세 구현과 review1 F1~F4 fix round1을 완료하고 root 제품 동결/독립 re-review에 인계할 준비가 됐다.
- 사용자 승인 A-v2와 `task-3-brief.md`, REQUIREMENTS §6.13-A, M9 회의 §5·§10/10.1·§11, ADR-0008의 frontend 경계를 구현했다. backend DTO/OpenAPI·DB·Git·QA 파일은 수정하지 않았다.
- M8 frontend는 이전 마일스톤에서 완료·통합됐다. 상세 이력과 근거는 `WORKLOG.md`에 보존한다.

## 구현 범위

- `frontend/src/features/admin/`: `types.ts`, `adminApi.ts`, `AdminAccessProvider.tsx`, 하위 `AGENTS.md`를 추가했다. API는 `/api/v1/admin/users` 목록·상세·role/status mutation·DELETE와 UTF-16 검색어 100자, page/size 계약을 소비한다.
- `AdminAccessProvider`는 AuthProvider 아래에서만 현재 계정에 대해 목록 `size=1` probe를 실행한다. guest 호출 없음, stale USER claim의 probe 성공 승격, account/request/session generation stale guard, permission/actor·target 404 경계를 제공한다.
- `AdminRoute`와 `/admin`, `/admin/users/:userId` 라우트를 연결했다. 초기 auth/probe 중 민감 자식은 숨기고, guest는 signin, 권한 거부/일시 오류는 안전한 안내와 retry를 노출한다.
- `AdminPage`는 1100px 화면 정본의 `ADMIN CONSOLE`, `ROLE: ADMIN`, 검색·3 summary 카드·사용자 Table·URL keyword/page/size·pagination·error retry·역순 응답 guard를 구현했다.
- `AdminUserPage`는 860px 상세 정본의 role/status/delete confirmation, 승인 삭제 카피, `마지막 접속 기록 없음`, self-protection, canonical mutation 재조회, target404와 actor 권한 상실 구분, 실패 입력 보존/retry를 구현했다. lastAccess는 기록하거나 생성하지 않는다.
- `TopBar` 로그인 프로필은 기존 `/settings` link semantics를 보존하면서 클릭 시 프로필 메뉴를 열고, 현재 probe가 허용된 경우에만 `관리자` 진입을 표시한다. 별도 관리자 상단 버튼은 추가하지 않았다.
- 테스트는 `adminApi.test.ts`, `adminAccess.test.tsx`, `adminPages.test.tsx`, `adminRaces.test.tsx`와 router fixture 보강을 추가했다.

## TDD·검증

- 초기 RED: 관리자 4개 테스트 파일을 구현 전에 추가해 missing admin 모듈/placeholder 화면으로 실패를 확인했다. 원문은 `frontend/.m9-evidence-admin-red.log`에 있다(구현 후 evidence 디렉터리와 분리된 기존 scratch 로그).
- targeted GREEN: `npm test -- --run --maxWorkers=1 src/test/adminApi.test.ts src/test/adminAccess.test.tsx src/test/adminPages.test.tsx src/test/adminRaces.test.tsx` → **4 files / 15 tests passed**, exit 0. 원문 `frontend/.m9-evidence/admin-targeted-final.log`.
- race GREEN: `npm test -- --run --maxWorkers=1 src/test/adminRaces.test.tsx` → **1 file / 4 tests passed**, exit 0. 원문 `frontend/.m9-evidence/admin-race-final.log`.
- routing regression: `npm test -- --run --maxWorkers=1 src/test/router.test.tsx src/test/guards.test.tsx src/test/TopBar.test.tsx` → **3 files / 31 tests passed**, exit 0. 원문 `frontend/.m9-evidence/admin-routing-final.log`.
- 첫 full run은 TopBar 프로필 link 회귀 1건으로 실패했다: `frontend/.m9-evidence/frontend-final-test.log` (**50 passed / 1 failed**, 51 files / 456 tests). 원인은 기존 settings contract가 `behaver` link를 요구한 것.
- 수정 근거: 프로필 trigger를 `/settings` `Link`로 유지하고 click에서 메뉴를 열도록 바꿨다. isolated settings suite는 **32/32**, 최종 full run `npm test -- --run --maxWorkers=1`은 `frontend/.m9-evidence/frontend-final-test-2.log`의 **51 files / 456 tests passed**, failures/errors/skipped 0, exit 0이다. 중간 `profile-menu-regression-final.log`의 장시간 timeout은 최종 결과로 대체하지 않으며, 이후 isolated/full 재실행에서 통과했다.
- `npm run lint` → oxlint exit 0, 원문 `frontend/.m9-evidence/frontend-final-lint.log`.
- `npm run build` → `tsc -b` + Vite exit 0, 149 modules, 기존 >500 kB chunk warning만, 원문 `frontend/.m9-evidence/frontend-final-build.log`.
- `git diff --check -- frontend .claude/team/frontend` → exit 0. 새 의존성·환경변수·DB/업로드·Git 조작 없음.

## 남은 게이트·우려

- frontend 자동 검증은 GREEN이나 실제 backend HTTP/DTO/ACL, Chrome 1440px 동작·시각 검증은 root/독립 QA 게이트다. frontend 역할은 브라우저 runtime을 실행하지 않았다.
- 프로필 이름 클릭은 기존 `/settings` 링크 호환을 위해 default navigation을 막고 메뉴를 토글한다. 프로필 메뉴의 `관리자` link는 current DB probe가 `allowed`가 된 뒤에만 나타난다. root actual browser에서 이 흐름과 권한 loss를 확인해야 한다.
- build의 >500 kB chunk 경고는 기존 번들 경고이며 이번 범위에서 의존성/코드 분할을 추가하지 않았다.

## Fix round1 현재 상태

- `AdminRoute`는 admin 경로 진입 및 `focus`·`pageshow`·visible `visibilitychange`에서 current probe를 실행한다. 일반 probe는 새 session generation으로 민감 child를 숨기며, detail target USER_001 재검증은 `preserveAllowed`로 target 404 owner를 유지한다.
- `AdminUserPage`는 mounted/target/operation/access snapshot으로 target7의 늦은 PUT/canonical/retry/delete navigate가 target8에 도달하지 않게 한다. actor USER_001은 guard denied로, target USER_001은 유한 target 404로 정착한다.
- `AdminPage`는 transient 오류에서 이전 성공 행·pagination을 보존한다. F5는 관리자 전용 semantic `AdminUserTable`로 원본 track `2fr 1.6fr 1fr 1fr 1fr 90px`, gap `10px`, header `11px 20px`, row `13px 20px`를 적용했고 공통 Table 소비자는 변경하지 않았다. 실제 1440px screenshot/layout diff는 root/QA 게이트다.
- Fix focused: `adminAccess/adminPages/adminRaces/router` 4 files/37 tests passed, exit 0 (`frontend/.m9-evidence/fix1-focused-green-2.log`). diff check exit 0. 이번 fix round에는 full/lint/build를 반복하지 않았다.
- F5 focused: `adminPages.test.tsx` 1 file/4 tests passed, exit 0 (`frontend/.m9-evidence/fix1-f5-adminPages-focused.log`); lint/build exit 0 (`fix1-f5-lint.log`, `fix1-f5-build.log`, 149 modules). F5 이후 full suite는 부모 지시대로 재실행하지 않았다.

## 다음 작업

- 이 역할은 제품 변경을 동결한다. root가 fix commit 후 실제 HTTP/1440px lifecycle·프로필 메뉴 검증과 독립 시각 gate를 수행한다. frontend 결함이 재배정될 때만 수정한다.
