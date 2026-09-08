# pm 작업 기록

> append-only. 기존 항목을 수정·삭제하지 않는다.

## 2026-09-06 — 팀 상태 기준선 생성

- 한 일: Git, M0~M2 worklog, 결정·위험 레지스터를 기준으로 PM 역할 상태를 초기화했다.
- 산출물: `.claude/team/pm/CLAUDE.md`, `STATE.md`, 이 파일.
- 검증: 리더가 M0/M1 머지 기록, M2 미머지, 사용자 승인 정정 기록을 내용 검색으로 대조한다.
- 미해결: M2 완료 판정과 다음 마일스톤 선택.

## 2026-09-06 — M2 기록 정합화 및 M3 준비

- 한 일: M2 worklog의 2026-07-26 19:20 사용자 결정을 PRD §7·§9에 동기화했다. `user.bio` 한 줄 소개, 별도 블로그 카드, 카드별 자기 API를 명시하고, 이전 프로필/비밀번호/위험 구역 3카드 및 탭 중심 서술을 대체한 사유를 M2 worklog에 append했다. PRD §10 M3 화면도 §9-R의 단일 리스트·인라인 편집·드래그 순서로 정합화했다.
- 한 일: 실제 `Category.java`, `CategoryType`, `CategoryRepository`, `V1__init.sql`, `SecurityConfig`, `ErrorCode`, `SettingsPostsPage`, `BlogPage`, `ScreenPanel`, `blogApi`, `BlogInitialSetupPage`와 정본을 대조했다. 초기 설정의 시작 카테고리는 현재 DTO/API에 없으므로 새 initial-setup schema 확장 대신 기존 CRUD 후속 호출·GET 재조회·중복 방지·부분 실패 복구를 권고로 정리했다.
- 산출물: `docs/PM-M3-readiness.md`, `.claude/team/pm/STATE.md`, `docs/worklog/M2-settings.md` append.
- M3 핵심 보류: soft-delete unique migration, 공개 category GET 및 PUBLIC/UNIVERSE/PRIVATE/draft 글 수, DEFAULT/LOCKED 순서·부모·잠금 subtree 삭제, category 오류 코드, 시작 카테고리 부분 실패 처리는 모두 `권고(미확정)`으로 남겼다. 리더 governance 안건은 `PROPOSED`이며 PM이 확정하지 않았다.
- 검증: `feature/M2-settings` local HEAD `ed8f2c1`, PR #8 원격 HEAD `403eb31`, 상태 `OPEN`, M2 `[머지]` 없음으로 재확인했다. 테스트를 재실행했다고 주장하지 않았고, 타 역할의 작업 트리 변경을 되돌리지 않았다.
- 다음: M2 각 역할의 실제 검증·독립 리뷰·머지와 M3 독립 심의/사용자 승인 후에만 승인된 정본을 반영하고 M3 착수 게이트를 재판정한다.

## 2026-09-06 — M4 게시글·콘텐츠·이미지 준비도 조사

- 한 일: PRD §10 M4/M5/M6 경계, §5.5·§5.12, §7.1·§7.3, §9·§13.1과 REQUIREMENTS FR-POST-01~08·FR-UPLOAD-01~04·NFR-01~09, 디자인 정본을 대조했다.
- 한 일: M3가 M4에 전달해야 하는 category ID/type/parent/order, 동일 블로그 검증, DEFAULT 이동, 공개 글 수 predicate, 공개 GET/security·blogId/slug 경로 계약을 분리하고 M3 `REVIEWING`/M2 미머지 게이트를 유지했다.
- 한 일: 실제 `V1__init.sql`, Category/SecurityConfig/ErrorCode, FE Post placeholder·TagInput·`package.json`, S3 설정 예시와 compose 부재를 확인했다. 현재 Post/Upload 구현·TipTap/S3 설정은 M4 공백이며 기존 M0 기록상 M4로 이관된 범위다.
- 산출물: `docs/PM-M4-readiness.md`에 M4 범위, BE→FE 권고 순서, LocalStack 가능/불가 검증, bucket/region/IAM 사용자 입력, 최소 미확정 안건과 완료 검증 기준을 기록했다.
- 기존 결정으로 정리한 것: `UNIVERSE` enum/화면 “친구”, LocalStack·S3 직접 URL·CloudFront 미사용, 허용 이미지 타입·5MB, TipTap JSON 원본/sanitized HTML, 데스크톱 디자인과 M5/M6 경계를 새 결정으로 확장하지 않았다.
- 미해결: M4/M5 UNIVERSE 접근 경계, nullable category/빈 draft, 디자인 toolbar와 TipTap·sanitizer 불일치, PostImage soft-delete unique·thumbnail 표시, blogId/slug canonical 목록 경로는 모두 `권고(미확정)`으로 남겼다. 실제 운영 S3 bucket/region/IAM도 제공 전이다.
- 추가 정합화: `docs/PM-M3-readiness.md` 출처의 `SecurityConfig`/`ErrorCode` 경로를 실제 `src/main/java/com/zeroverse/config/SecurityConfig.java`, `src/main/java/com/zeroverse/common/exception/ErrorCode.java`로 정정했다. 기존 M3 결정·권고 내용은 바꾸지 않았다.
- 검증: `git branch --show-current`, `git status --short`, `git log --oneline -10`, `rg --files`/`rg -n`, 관련 정본·실제 파일 `Get-Content`를 실행해 근거를 재확인했다. 구현·테스트·git 조작은 하지 않았고 타 역할 변경을 보존했다.
- 다음: M2 PR #8 머지와 M3 승인·정본 반영 후, 사용자 제공 S3 값 및 승인된 M3 계약을 readiness와 재대조한다.

## 2026-09-06 — M2 merge 및 M3 사용자 결정 대기 상태 동기화

- 한 일: 실제 GitHub PR #8 상태를 `gh pr view 8`로 확인했다. PR #8은 `MERGED`, merge SHA는 `4c129e20f58a6ccb9c61246d103934702516c295`, `mergedAt`은 `2026-09-05T20:41:12Z`이며 공유 checkout `dev` HEAD도 동일 merge commit이다.
- 한 일: PM 소유 `docs/PM-M3-readiness.md`와 `docs/PM-M4-readiness.md`의 현재 스냅샷을 M2 완료 및 M3 `USER_DECISION_REQUIRED`로 정정했다. 이전 M2 `OPEN`/M3 `REVIEWING` 판단은 각 문서의 정정 기록과 기존 작업 기록으로 보존했다.
- 한 일: M3 §4의 미승인 전달 항목인 generated `active_key`, 동일 ID 집합의 `last-write-wins`, M4 Post `category_id` 경로의 동일 `blog_id` lock 참여를 두 readiness 문서의 인계 목록에 연결했다. M4 `UNIVERSE` 권고는 M3 Q3 승인 결과와 M5 관계 구현에 종속된다는 점만 유지했다.
- 산출물: `.claude/team/pm/STATE.md`, `docs/PM-M3-readiness.md`, `docs/PM-M4-readiness.md`의 상태·전달 목록 갱신.
- 미해결: M3 Q1~Q4 및 위 동시성/lock 권고는 사용자 승인 전이며, M2 merge continuation worklog는 리더가 기록 중이다. PRD·REQUIREMENTS·governance 회의록·제품 코드·Git은 수정하지 않았다.
- 검증: `git branch --show-current`, `git status --short`, `git log --oneline -6`, `gh pr view 8 --json ...`, M3 회의 §4 및 PM 문서 재독을 완료했다. 구현·테스트·git 조작은 하지 않았다.
- 다음: M3 사용자 승인 및 정본/ADR 반영 전까지 M3/M4 구현을 시작하지 않고, 리더의 M2 merge continuation 기록을 확인한다.

## 2026-09-06 — M2/M3 완료 기록 상태 정정

- 정정: 리더의 M2 최종 `[머지]` 기록과 QA 확인이 완료된 실제 상태를 반영해 PM `STATE.md`, `PM-M3-readiness.md`, `PM-M4-readiness.md`의 `리더가 기록 중` 및 M2 continuation 미완료 표기를 완료로 갱신했다.
- 정정: M3 Q1~Q4 독립 심의·worklog 기록은 완료로, 사용자 정책 승인은 미완료로 분리 표기했다. 회의 상태 `USER_DECISION_REQUIRED`, 정본 반영 전·제품 구현 보류는 유지한다.
- 범위: PM 소유 상태·준비도·작업 기록만 갱신했으며 PRD·REQUIREMENTS·governance 회의록·제품 코드·Git은 수정하지 않았다.

## 2026-09-06 — M3 사용자 진행 지시와 정본 계약 동기화

- 한 일: 리더가 회의록 §4 Q1~Q4의 구체 권고안·정확한 계약을 제시하고 승인 응답을 기다리던 직후 사용자가 원문 `시작`으로 진행을 지시한 맥락을 확인했다. 리더가 반영한 ADR-0005 `ACCEPTED`, REQUIREMENTS 및 회의록 `APPROVED`와 대조해 새 승인·정책을 만들지 않았다.
- 산출물: `docs/PRD.md`에 active-only `active_key` unique, root page+children/CategoryResponse, 조회자별 count·`includeDrafts`, `CAT_004~007`, 생성 시에만 parent 선택, DEFAULT/LOCKED·last-write-wins, 기존 setup 뒤 순차 CRUD 복구, M4 동일 `blog_id` lock을 반영했다. SettingsPosts 삭제 안내는 `미분류`로 정합화했다.
- 산출물: `docs/PM-M3-readiness.md`를 승인된 현재 계약과 승인 전 심의 이력으로 분리하고, `docs/PM-M4-readiness.md`에 승인 계약 인계와 M4 미확정 항목을 갱신했다. `.claude/team/pm/STATE.md`를 현재 단계·진행·차단·다음 작업 구조로 덮어썼다.
- 검증: `rg -n`으로 PRD/REQUIREMENTS/ADR/회의록의 FR-CAT-01~05·FR-BLOG-02·CAT 코드·active_key·last-write-wins·blog lock·`미분류`를 대조하고, 세 편집 문서를 재독했다. `git diff --check`는 다음 독립 점검에서 리더가 실행할 수 있도록 남겼다. 제품 코드·테스트·Git 조작은 하지 않았다.
- 미해결: M3 backend/frontend 구현, 실제 MySQL·권한·부분 실패 검증, BE·FE·QA 공통 계약 확인. M4의 S3 bucket/region/IAM 및 별도 권고 안건은 이 승인 범위에 포함하지 않는다.

## 2026-09-06 — M3 정본 최종 대조

- 검증: PRD §5.4의 CAT_006(LOCKED 숫자 순서·불변 변경·잠금 subtree), CAT_007(`displayOrder` 중복·부적합 전체 ID 배열), page 기본값 0/size 20·최대 100, DEFAULT 순서 재배치·LOCKED 숫자 보존, 로그인 비소유자 `PUBLIC + viewer→owner ACCEPTED UNIVERSE` count를 REQUIREMENTS/ADR-0005와 재대조했다.
- 검증 명령: `rg -n` 핵심 계약 검색(종료코드 0), `rg -n -P "[ \\t]+$"` 편집 파일 trailing whitespace 검사(출력 없음), `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`(오류 없음; CRLF 경고만). 제품 코드·테스트·Git 조작은 하지 않았다.
- 상태: PRD·PM-M3/M4 readiness·PM STATE/WORKLOG 반영은 완료됐다. M3 backend/frontend 구현·실제 DB/권한/부분 실패 검증과 BE·FE·QA 공통 계약 확인은 미해결로 유지하며, M4 승인으로 확대하지 않는다.

## 2026-09-06 — 현재 checkout 표기 정정

- 정정: 실제 `git branch --show-current`=`feature/M3-categories`, `git rev-parse --short HEAD`=`1690731`을 확인해 PM-M3 §1, PM-M4 §1, PM `STATE.md`의 현재 위치를 갱신했다. `4c129e20f58a6ccb9c61246d103934702516c295`는 M2의 `dev` merge 이력으로만 표기했다.
- 범위: 현재 checkout/HEAD 구분만 정정했으며 M3 계약·M4 승인 범위·제품 코드·Git 상태는 변경하지 않았다.

## 2026-09-08 — M3 종료 재개 상태·준비도 정합화

- 한 일: 헌법 → `AGENTS.md`/`CLAUDE.md` → PM 지침/`STATE.md`를 순서대로 읽고, `project-lead` 및 `brief` 지침을 확인했다. backend/frontend/qa STATE·최근 WORKLOG, `docs/worklog/M3-categories.md`, ADR-0005, PRD §5.4·§7·§9.5·§10~§12, REQUIREMENTS FR-CAT/FR-SETTINGS/NFR, `qa/M3-review.md`와 현재 Git/source를 교차 대조했다.
- 산출물: `.claude/team/pm/STATE.md`의 현재 기준을 `feature/M3-categories` HEAD `08239e0514b6` 및 제품 tree `4fc9ae2` 이후 불변 상태로 정정했다. `docs/PM-M3-readiness.md`에는 구현·검증 현황과 실제 잔여 gate/소유자/해소조건을 추가하고, `docs/PM-M4-readiness.md`에는 HEAD·M3 검증 인계·M4 대기 상태만 정정했다. M4 범위·권고·승인과 새 제품 결정은 추가하지 않았다.
- 검증: `git branch --show-current`=`feature/M3-categories`, `git rev-parse --short=12 HEAD` 및 `origin/feature/M3-categories`=`08239e0514b6`; `git diff --name-status 4fc9ae2..HEAD -- src frontend` 출력 없음. `git status --short`의 기존 `.claude/team/JOURNAL.md`, `.claude/team/qa/STATE.md`, `qa/M3-review.md` 변경은 보존했다. 기존 산출물에서 BE `BUILD SUCCESSFUL`, 56 files/370 tests, failures/errors/skips 0/0/0, FE 23 files/273 tests PASS, lint/build exit 0, QA HTTP smoke exit 0 및 JAR SHA-256 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`를 재확인했다.
- PR 상태: `gh pr view 9` 재조회는 local GitHub 인증 401로 실패했다. 따라서 PR #9 `OPEN`/Draft 표기는 M3 worklog의 마지막 GitHub 조회와 origin head에 근거해 유지하며, live 상태로 과장하지 않았다.
- 미해결: root 소유 브라우저 mouse DnD의 drop→순서 PUT→성공 notice와 LOCKED confirm 수락→불변 상태 재조회 두 gate, `/root/m3_final_qa` 독립 최종 acceptance, root의 PR #9 `dev` merge 및 M3 `[머지]` 기록. M3 마감 전 M4는 시작하지 않는다. backend/frontend/qa STATE의 타임스탬프·HEAD 모순은 소유자 파일을 직접 수정하지 않고 root에 보고한다.

## 2026-09-08 — M3 DnD gate 종료 및 LOCKED gate 단일화

- 한 일: root가 전달한 최신 브라우저·SQL evidence를 PM 기준에 반영했다. mouse drag의 drop→backend reorder→`카테고리 순서를 저장했습니다.` AX notice가 확인됐고, active category id `19/21/20/22`의 `display_order=0/1/2/3` 재조회가 완료됐다.
- 정정: 잔여 브라우저 gate를 두 개에서 하나로 변경했다. LOCKED 경고는 AX와 `getJsDialog` confirm까지 확인됐지만 accept 메서드가 timeout됐고, DB 재조회가 `GENERAL`로 남아 `LOCKED` 저장·이름/타입/숫자 순서/삭제 불변 상태 재조회는 PASS가 아니다. 사용자에게 경고 OK 클릭 결과를 요청한 상태다.
- 산출물: `.claude/team/pm/STATE.md`, `docs/PM-M3-readiness.md`, `docs/PM-M4-readiness.md`의 현재 판정·다음 작업·차단 요인을 단일 LOCKED gate, `/root/m3_final_qa` 최종 acceptance, root의 PR #9 `dev` merge 대기로 정정했다. root의 `gh pr edit` 성공으로 PR body 포함 범위 한 문장 정정도 반영된 상태를 기록했다.
- 검증: 편집 후 PM STATE/WORKLOG와 PM-M3/M4 readiness의 현재 섹션·최신 정정 기록을 재독하고, `rg`로 두 gate/`저장되지 않음` 등 잘못된 현재 표현과 `08239e0514b6`·DnD notice·LOCKED 저장 pending·`m3_final_qa` 참조를 확인했다. `git diff --check`는 exit 0이었다. 제품 source/test, 서버, UI 브라우저, Git 조작은 하지 않았고 타인 변경을 보존했다.
- 미해결: LOCKED confirm 승인 후 실제 `LOCKED` 저장 및 불변 상태 목록/DB 재조회, `/root/m3_final_qa` 독립 최종 acceptance, root의 PR #9 `dev` merge와 M3 `[머지]` 기록. M3 마감 전 M4는 시작하지 않는다.

## 2026-09-08 — M3 사용자 종료 경계 문구 정정

- 정정: 현재 PM `STATE.md`와 PM-M3/M4 readiness의 범위 표현을 `M3 검증 → PR #9 dev merge → M3 마감 기록 후 이번 실행 종료`로 통일하고, `이번 실행에서 M4는 착수하지 않는다`를 명시했다. 기존 WORKLOG 이력은 보존했다.
- 범위: LOCKED 잔여 gate, 독립 최종 acceptance, PR #9 `dev` merge와 M3 `[머지]` 기록을 마친 뒤 이번 실행을 종료한다. M4 요구·계획·승인을 추가하거나 착수하지 않는다.
- 검증: 세 PM 문서의 현재 섹션을 재독하고 `git diff --check -- .claude/team/pm/STATE.md docs/PM-M3-readiness.md docs/PM-M4-readiness.md` exit 0을 확인했다. 제품·테스트·서버·UI 브라우저·Git 조작은 하지 않았고 타인 변경을 보존했다.

## 2026-09-08 — LOCKED 실측 완료 및 잔여 절차 축소

- 정정 근거: M3 worklog `[리뷰]` 10:05 KST의 root 독립 IAB evidence를 반영했다. 새 합성 블로그 blog ID `7`에서 active category ID `26`이 `LOCKED`·`display_order=3`으로 저장·DB 재조회됐고, full reload 후에도 동일 상태와 순서 이동·이름 변경·삭제·타입 선택 disabled 및 `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.` notice가 확인됐다.
- 구분: 기존 Chrome fixture의 ID `22` `GENERAL`은 과거 실패 사실로 보존하고 새 IAB 결과와 혼동하지 않는다. 이에 따라 PM `STATE.md`와 현재 PM-M3/M4 readiness에서 LOCKED UI gate를 완료로 정정하고, 남은 절차를 `/root/m3_final_qa` 독립 최종 acceptance와 PR #9 `dev` merge·M3 `[머지]` 기록으로 축소했다.
- 범위: M3 최종 QA·PR merge·마감 기록 후 이번 실행을 종료하며, 이번 실행에서 M4는 착수하지 않는다. 제품·테스트·서버·UI 브라우저·Git 조작은 하지 않았고 타인 변경을 보존했다.
