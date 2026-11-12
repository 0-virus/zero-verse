# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-12 15:54:30 KST

## 현재 단계

- M10 closeout frontend 역할은 **DEV INTEGRATED / COMPLETE**다. Task3의 PostEditor 검증 구현·독립 review·fresh 자동검증과 Task4의 실제 acceptance/시각 evidence를 root·독립 QA가 확인했고, PR #16이 `dev`에 병합됐다.
- 근거 정본은 REQUIREMENTS NFR-09, PRD §11~§12, 승인 회의 `docs/governance/meetings/M10-20261112-closeout.md`, `docs/design/AGENTS.md` 및 `DESIGN-SYSTEM.md`다. 새 dependency, API/server contract, 디자인 정본, bundle optimization은 추가하지 않았다.

## 구현·테스트

- `frontend/src/features/post/PostEditor.tsx`: 임시저장은 빈 제목/본문을 허용하고, 발행 시 `title.trim()` 길이 1~200 및 TipTap 본문 `textContent` 또는 본문 이미지 존재를 검사한다. 실패하면 visible `role="alert"` 문구를 표시하고 `onSave`를 호출하지 않는다. 이미지 판정은 thumbnail이 아닌 TipTap 본문 image node 수집 결과다.
- `frontend/src/features/post/PostEditor.test.tsx`: 빈 draft 허용, 빈/공백/201자 제목 차단, 정확히 200자 제목 허용, 빈 text+image 본문 차단, 대표 이미지-only 차단, 본문 image-only 발행 허용을 실제 컴포넌트 동작으로 검증했다. 새 검증 때문에 기존 저장 실패 경로가 막히지 않도록 해당 회귀에 본문 입력을 추가했다.
- TDD RED: `cd frontend && npm test -- --run src/features/post/PostEditor.test.tsx --maxWorkers=1` → exit 1, 16 tests 중 3 failed/13 passed. 실패는 구현 전 빈 제목, 공백·201자 제목, 빈 본문 안내 부재였다.
- Focused GREEN: 같은 명령 → exit 0, 1 file / 18/18 passed(정확히 200자 positive boundary와 thumbnail-only negative 보강 포함).
- Full GREEN: `cd frontend && npm test -- --run --maxWorkers=1` → exit 0, 51 test files / 472 tests passed.
- Lint/build: `cd frontend && npm run lint && npm run build` → exit 0. Oxlint 통과, Vite 149 modules. JS 958.38 kB(gzip 288.87 kB) 및 500 kB 초과 chunk warning은 기존 비차단 경고로 남겼고 code splitting은 구현하지 않았다.

## 인계·남은 위험

- 최종 frontend 자동 gate는 `npm test -- --run --maxWorkers=1` 51 files/472 tests PASS, `npm run lint`와 `npm run build` exit 0, Vite 149 modules다. JS 958.38 kB(gzip 288.87 kB) 및 500 kB 초과 chunk warning은 비차단으로 유지했다.
- 실제 M10 acceptance는 82/82 PASS, failures 0, 13개 고유 1440×1000 PNG와 provenance/manifest hash 일치를 기록했다. 실제 Chrome hard evidence는 bounded keyboard observation이며 DraftPicker 선택→edit, TopBar profile/search, category mutation, admin select/confirm/paging 전부를 Chrome에서 실행했다고 확대하지 않는다.
- 실제 acceptance는 실행 당시 frontend dist `992d0e1dc72c094afb13754473d07dd3944c1209f3cb0b3331addd83ffb802ef`, tested head `b5843743c5b86f84cb0bb045e60dc063aa8cccae`에 결속됐다. 이후 QA/PM/root 문서 커밋은 frontend 제품/harness diff를 만들지 않았고, merge commit `288f29cffef0671c20a365921f087fe02d4dfcdb`의 tree `23ce12bc28c1613c04537587692b89ed9f1922e8`가 tested tree와 동일하다.
- 운영 배포·S3/U0·실사용 DB·모바일·브라우저 파일 선택 자동화는 M10 범위 밖이며, 사용자 파일/DB/upload는 보존했다.

## 소유권·통합

- 변경한 제품 파일은 위 frontend 두 파일뿐이고 역할 기록은 이 `STATE.md`와 `WORKLOG.md`다. SDD Task3 보고서는 부모가 지정한 `.superpowers/sdd/M10-closeout/task-3-report.md`에 기록했다.
- frontend 역할은 Git stage/commit/branch/push/merge를 수행하지 않았고, root가 PR #16·`dev` 통합을 완료했다. 현재 다음 작업은 별도 사용자 지시 전 대기다.

## M10 실제 브라우저 인수 진단 인계 (2026-11-12 14:05 KST)

- 격리 런타임 `127.0.0.1:5180`에서 합성 관리자 로그인 DOM에 상단 검색 submit과 로그인 submit이 함께 존재한다. QA `browserSignIn`의 `page.locator('form button[type="submit"]').first()`가 첫 번째 검색 버튼을 클릭해 로그인 세션을 만들지 못한다. 이는 frontend 제품 결함이 아니라 `qa/m10-acceptance.mjs` selector 결함이며 QA 역할에 수정 제안했다.
- 현재 `frontend/dist` 번들은 `frontend/src/lib/apiClient.ts`의 기본값 `http://localhost:8080`으로 빌드되어 있다. M10 FE static server는 `127.0.0.1:5180`에서 dist만 제공하므로 API `18090`으로 연결되지 않고, 실제 browser request가 `http://localhost:8080/api/v1/... net::ERR_CONNECTION_REFUSED`를 기록했다. runtime plan §2.1/§2.2의 explicit `VITE_API_BASE_URL=http://127.0.0.1:18090`로 M10 전용 FE build를 다시 만들 책임은 root runtime에 있다. 기존 FE tests와 local default 계약은 변경하지 않는다.
- 검증: Playwright headless 1440×1000에서 `/signin`, `/blog/m10-owner-301b5f83`, `/blog/m10-owner-301b5f83/8`, `/`를 확인했다. FE source/dist/role files 외 제품 파일은 편집하지 않았고 사용자 DB `13307`, `.local-data`, 기존 업로드에 접근하지 않았다.
- 다음: root가 QA selector fix와 explicit runtime FE rebuild/provenance 재생성 후 13개 화면·keyboard 인수를 재실행한다. frontend 역할의 제품 코드 수정은 현재 필요하지 않다.

## M10 hydration race 최종 진단 (2026-11-12 14:58 KST)

- 새 dist/API origin(`127.0.0.1:18090`)에서 올바른 synthetic owner/admin 자격으로 수동 브라우저 로그인은 정상이다. owner는 `POST /api/v1/auth/signin=200` → `/api/v1/auth/me=200` → `/`; admin도 signin/me `200` → `/blog/setup`이며 DOM error가 없다.
- 로그인 직전 익명 `POST /api/v1/auth/refresh=401`은 AuthContext 초기 복원 요청으로 정상이며 로그인 실패 원인이 아니다.
- QA `browserSignIn`의 `domcontentloaded` 직후 즉시 `email.count()` 검사가 React hydration보다 앞서 실행되는 race를 반복 관찰했다. 동일 흐름에서 `email-count-zero`로 즉시 false가 되는 실행과 정상 signin이 교차했다. 최소 수정은 QA 소유 `email.waitFor({ state: 'visible', timeout: 10000 })` 후 ancestor form/`접속하기` selector를 평가하는 것이다. frontend 제품 source 변경은 필요하지 않다.
