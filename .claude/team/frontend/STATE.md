# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-10 17:16:20 KST

## 현재 단계

- M8 Task4 frontend 구현을 완료하고 자동 검증을 통과했다. 사용자 승인 A-v2의 §9 계약에 맞춰 notifications API/types/provider, 센터, TopBar dropdown/badge, Main Feed RightPanel preview를 연결했다.
- preview(page 0,size 3)와 center(page,size 20) cache를 분리하고, unreadCount/canManage를 공유한다. viewer account·mutation·request 세대와 account-scoped mutation dedupe/stale completion guard를 적용했다.
- ACTIVE는 read/read-all/delete 후 canonical 재조회, SUSPENDED는 관리 mutation을 막고 서버가 반환한 안전한 href 열람만 허용한다. unavailable은 고정 문구와 민감 필드 masking을 유지한다.
- fix1 review 반영: main preview consumer 재진입 effect와 in-flight 완료 시점 정리, USER_001/AUTH 최종 실패의 양쪽 cache·summary·진행 요청 무효화, 센터/TopBar/RightPanel mutation retry 및 canonical 이동, transient center error 중 기존 행·paging 보존을 추가했다.
- mutation retry callback은 state-updater 오호출 없이 동일 read/delete/read-all 작업을 재호출하며, auth 최종 실패에는 retry를 숨긴다. TopBar도 `useNotificationPreview` entry effect를 사용해 provider 유지 상태에서 재진입 preview를 갱신한다.
- fix2 review 경계: session/retry generation을 mutation snapshot과 retry callback에 함께 묶었다. PUT 후 canonical GET USER_001이면 mutation 결과/href 반환을 supersede하고, 계정·최종 auth 전환 뒤 old retry는 UI에서 제거되며 provider가 API 호출 전 거부한다. 늦은 A catch가 B UI retry를 다시 만들지 않도록 generation 검사를 적용했다.
- final review minor 반영: TopBar dropdown과 Main Feed RightPanel preview에서 `canManage=false` 응답을 받은 정지 계정에 `정지된 계정은 알림을 읽기 전용으로 확인할 수 있습니다.` 안내를 warning token으로 표시한다. 기존 safe href 열람과 관리 mutation 비활성화는 유지한다.

## 구현 범위

- `frontend/src/features/notifications/`: API client, DTO types, shared provider/store, 계약·상태·계정 전환·mutation race 테스트, 하위 경로 AGENTS 지침.
- `frontend/src/pages/NotificationsPage.tsx`: 860px 센터, 전체 이력 paging, loading/error/retry/empty/out-of-range, unread/read-all, read canonical href, delete 실패 보존, SUSPENDED read-only.
- `frontend/src/components/layout/TopBar.tsx`: 330px dropdown, shared unread badge, open/read/error/loading/guest 상태, Escape, canonical href 이동.
- `frontend/src/components/layout/RightPanel.tsx`: 최근 3건, shared unread, empty/error/loading/guest 상태, 센터 링크, safe href/read 동작.
- `frontend/src/pages/SettingsUniversePage.tsx`: block/unblock 성공 시 notification store invalidate.
- `frontend/src/App.tsx`: `NotificationProvider`를 AuthProvider 아래 앱 공통으로 배치.

## TDD·검증

- API/store/UI 초기 RED 후 GREEN: notification targeted 5 files/16 tests, post-fix shell/page targeted 2 files/8 tests. fix1 focused는 5 files/25 tests passed.
- mutation account-generation RED: guard 전 A→B read/read-all race 1 file/2 tests 실패. account-scoped key, 시작 account 세대 캡처, stale completion rejection 및 superseded UI 무시 후 1 file/2 tests 통과.
- fix1 필수 회귀: 동일계정 pre-read 지연 응답, read-all 후 신규 unread, 다른 center query 역순 summary, USER_001 조회·mutation cache clear, preview re-entry, transient center 보존, TopBar/RightPanel/center retry가 모두 GREEN이다.
- fix2 회귀: PUT 성공→후속 GET USER_001 href/이동 금지, A→B old read-all retry API 미호출, transient→USER_001 old preview retry API 미호출, NotificationsPage/TopBar retry 제거 및 늦은 실패 catch 차단이 GREEN이다.
- 전체 frontend: `npm test -- --run --maxWorkers=1` → 47 files/423 tests passed, failures 0, errors 0, skipped 0, exit 0.
- lint: `npm run lint` → oxlint exit 0.
- build: `npm run build` → `tsc -b` + Vite exit 0, 145 modules. 기존 500 kB 초과 chunk warning만 남고 build 오류는 없다.
- 정적: `git diff --check -- frontend .claude/team/frontend` exit 0. notification 구현에서 debug log, polling, 폐기 다크 토큰, radius 선언을 찾지 못했다. `지난 30일`은 부재를 검증하는 테스트 assertion에만 남아 있다.
- fix1 raw 원문: `frontend/.m8-evidence/raw/frontend-fix1-focused-green.log`(5 files/25 tests), `frontend/.m8-evidence/raw/frontend-fix1-lint.log`(oxlint), `frontend/.m8-evidence/raw/frontend-fix1-build.log`(tsc+Vite; 기존 >500 kB warning만).
- fix2 raw 원문: `frontend/.m8-evidence/raw/frontend-fix2-focused-green.log`(5 files/31 tests), `frontend/.m8-evidence/raw/frontend-fix2-lint.log`(oxlint), `frontend/.m8-evidence/raw/frontend-fix2-build.log`(tsc+Vite; 기존 >500 kB warning만).
- final review raw 원문: `frontend/.m8-evidence/raw/frontend-final-fix-readonly-focused.log`(1 file/8 tests), `frontend/.m8-evidence/raw/frontend-final-fix-readonly-lint.log`(oxlint), `frontend/.m8-evidence/raw/frontend-final-fix-readonly-build.log`(tsc+Vite; 기존 >500 kB warning만).

## 증거·변경 경계

- 명령·결과 요약: `frontend/.m8-evidence/task4-targeted.txt`, `task4-full.txt`, `task4-lint-build.txt`, `task4-static.txt`.
- 제품·테스트 변경은 `frontend/**`, 상태·작업 기록은 `.claude/team/frontend/**`에 한정했다. Git commit/의존성 추가/backend/root docs/QA/runtime 파일은 이 역할에서 수행하지 않았다.
- 부모/root의 기존 타 역할 변경과 untracked 파일은 보존했으며 되돌리지 않았다.

## 남은 게이트·우려

- backend 실제 DTO/OpenAPI/HTTP와의 연동, MySQL·권한/차단 projection, 독립 QA, 실제 1440px Chrome 동작·시각 검증은 root/QA 게이트다. 이 역할은 브라우저 runtime을 실행하지 않았다. final review의 TopBar/RightPanel 읽기 전용 안내 수정은 targeted test/lint/build로 검증했다.
- root가 `.superpowers/sdd/M8-notifications/task-4-report.md`를 보고용으로 작성하고, 독립 검토 후 통합한다.

## 다음 작업

- root/QA에 소유 경로와 `frontend/.m8-evidence/*`를 인계한다.
- root/QA의 실제 API·1440px 검증에서 발견되는 frontend 소유 결함만 재현·수정·재검증한다.
