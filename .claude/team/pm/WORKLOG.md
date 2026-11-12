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

## 2026-09-08 — M3 QA·리더 승인 완료 및 공개 게시 승인 대기

- 현재 기준: QA 최종 `APPROVE`와 리더 승인이 완료됐다. 로컬 `feature/M3-categories` HEAD는 `822b4ce1d6c78695e87ac7883e8577b066e03aea`, `origin/feature/M3-categories`는 `08239e0`(local ahead/behind `1/0`)이며 제품 `src`·`frontend`는 불변이다.
- 공개 게시 상태: 팀 STATE/QA/JOURNAL 등을 포함한 11개 문서의 공개 전송에 대한 구체적 사용자 승인 부족으로 자동승인 검토에서 `git push`가 거절됐다. 따라서 push·PR Ready·merge는 실행하지 않았고, 현재 유일한 차단은 검증이 아닌 공개 게시 승인이다.
- 사실 정정: IAB LOCKED 저장 완료 notice는 저장 직후 확인한 것이며, full reload 후 확인한 것은 `LOCKED` 상태와 순서 이동·이름 변경·삭제·타입 선택 네 조작의 disabled 상태다. 기존 Chrome fixture ID `22` `GENERAL`은 과거 실패 사실로 보존하며 새 IAB blog ID `7`/category ID `26` evidence와 구분한다. 기존 WORKLOG 본문은 수정하지 않았다.
- 범위: 공개 게시 승인 후 root가 push·PR #9 Ready·`dev` merge를 수행하고 M3 `[머지]` 기록을 남긴 뒤 이번 실행을 종료한다. 이번 실행에서 M4는 착수하지 않는다.

## 2026-09-08 — M4 준비 재개·독립 심의 REVIEWING 동기화

- 한 일: 최신 사용자 `$project-lead m4`와 재독한 헌법·루트 규칙·PM 지침·STATE를 기준으로 PM `STATE.md`와 `docs/PM-M4-readiness.md`의 현재 스냅샷을 M4 준비 상태에 맞췄다. [M4 worklog](../../docs/worklog/M4-posts.md)와 [M4 기획 심의](../../docs/governance/meetings/M4-20260908-posts.md) §1~4 v1을 현재 기준으로 연결했다.
- 현재 사실: M3 로컬 `feature/M3-categories` HEAD `822b4ce1d6c78695e87ac7883e8577b066e03aea`, 원격 `08239e0`, 제품 `src`·`frontend` 불변, PR #9 `OPEN`/Draft·base `dev`·`MERGEABLE`·`mergedAt=null`, 독립 QA `APPROVE` 완료다. 기존 11개 문서 공개 게시 승인은 여전히 없고 push가 거절된 상태다.
- 정정: `$project-lead m4`는 위 공개 게시 승인으로 간주하지 않는다. 현재 M4는 문서 준비와 독립 3인 심의 `REVIEWING`만 진행하며 Q1~Q5는 모두 `권고(미확정)`이다. 새 API·DB·보안·의존성 승인, 제품 구현, M4 branch/분기와 reviewer 연락·의견 공유는 하지 않았다.
- 사실 정정: root 안건의 TipTap v3 `StarterKit`에 `Link`/`Underline`이 내장된다는 [공식 문서](https://tiptap.dev/docs/editor/extensions/functionality/starterkit) 근거를 반영했다. 별도 중복 등록을 전제하지 않되, 표/Markdown·sanitizer allowlist·태그·이미지·보안 Q1~Q5는 기존 `권고(미확정)`으로 유지했다. 실제 AWS 값 전 LocalStack은 PRD §13.1의 기존 확정 범위만 따른다.
- 검증·범위: PM STATE/PM-M4 readiness/WORKLOG 및 신규 M4 worklog·회의록 §1~4 v1을 재독하고 `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md docs/PM-M4-readiness.md`를 확인한다. PRD·REQUIREMENTS·governance·source·제품·테스트·Git은 편집/실행하지 않았고 타인 변경을 보존한다.

## 2026-09-08 — M4 독립 심의 종합·BLOCKED 상태 동기화

- 한 일: [M4 기획 심의](../../../docs/governance/meetings/M4-20260908-posts.md) §5~6의 세 독립 검토와 root 종합을 읽고 PM `STATE.md`·`docs/PM-M4-readiness.md`의 현재 스냅샷을 `BLOCKED`로 정정했다. A 단계별 진행은 조건부 권고·미승인이고, Q1~Q5는 모두 `권고(미확정)`으로 유지했다.
- 정정: Architecture가 REQUIREMENTS FR-BLOG-02 및 PRD §5.3/§7.1의 `/blogs/slug/{urlSlug}/posts`와 `/blogs/{blogId}/posts` 두 경로를 확인해 Q1 slug-list 생략 권고를 철회했다. Q2 ledger·Q4 이미지 접근 정책과 정확한 콘텐츠/API 계약은 미결이며, 비공개 S3+권한 확인 GET 권고와 공개 URL 위험 수용 선택 요청은 무응답으로 승인되지 않았다.
- 현재 사실: M3 로컬 `822b4ce1d6c78695e87ac7883e8577b066e03aea`, 원격 `08239e0`, 제품 불변, PR #9 `OPEN`/Draft·`MERGEABLE`·`mergedAt=null`, QA `APPROVE` 및 M3 공개 게시 승인을 별도 미결로 유지했다. 이번 M4 요청을 공개 게시 승인으로 간주하지 않는다.
- 범위 정정: M4는 문서 준비·심의 결과 정리만 유지하며 새 API·DB·보안·의존성 승인, 제품 구현, M4 branch/분기, 별도 toolbar 축소·숨김·비활성 디자인 변경을 진행하지 않는다. 현재 계약에 없는 toolbar 기능은 구현을 보류하고 축소는 별도 승인 후 검토한다. LocalStack은 PRD §13.1의 기존 범위만 따른다.
- 링크 정정: 기존 WORKLOG 항목은 append-only로 보존하고, 이 항목에서 PM 폴더 기준 올바른 상대경로인 [M4 worklog](../../../docs/worklog/M4-posts.md)와 [M4 기획 심의](../../../docs/governance/meetings/M4-20260908-posts.md) §1~6을 사용했다.
- 검증·미해결: STATE·PM-M4 readiness·WORKLOG·회의록 §5~6을 재독하고 `rg` 내용 검색 및 `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md docs/PM-M4-readiness.md`를 실행해 exit 0을 확인했다. Q2/Q4·콘텐츠/API 계약, 이미지 정책 사용자 응답, M3 공개 게시 승인 및 PR #9 push/Ready/`dev` merge는 미해결이다. 제품·테스트·서버·UI·Git 작업과 reviewer 연락은 하지 않았고 타인 변경을 보존했다.

## 2026-09-09 — M3 공개·dev 머지 및 M4 이미지 접근 방향 승인 동기화

- 한 일: 헌법·루트/PM 지침·STATE를 재독하고 root `JOURNAL.md`, `docs/worklog/M3-categories.md`, `docs/governance/meetings/M4-20260908-posts.md` §10을 대조했다. 사용자 `그렇게 해줘` 승인에 따라 M3 고정 기록 11개·검증 요약 공개와 PR #9 `dev` 머지 완료 사실을 PM STATE/PM-M3·M4 readiness에 반영했다.
- 현재 사실: PR #9는 `MERGED`, `mergedAt=2026-09-09T00:08:47Z`, merge `59badfe42d539a33091a387b9ee6119d838190b8`이며, 같은 `origin/dev`에서 `feature/M4-posts`가 생성됐다. M4 문서는 M3 공개 payload에 포함되지 않았다. M3 제품·검증·게시·머지는 완료로 닫았다.
- 정본 반영: 승인된 M4 방향만 `docs/PRD.md` §9.6·§5.12·§13.1에 기록했다. 게시글 본문·대표 이미지(PostImage/thumbnail)는 비공개 S3에 두고 글 열람권한 predicate 확인 후 만료 가능한 GET 접근 표현을 제공하며 영구 public URL은 재설계 대상으로 남긴다. 정확한 endpoint·TTL/만료·DB/응답·object key·업로드 기술·검증/재사용/lifecycle은 `권고(미확정)`이다.
- 범위·미해결: M4 전체는 `BLOCKED`; Q1~Q5 세부 계약, Q2 ledger, 이미지 세부 계약과 제품 구현은 승인하지 않았다. LocalStack은 기존 PRD §13.1 범위만 따른다. 기존 WORKLOG 항목은 수정하지 않았고, M4 문서 공개·제품·테스트·서버·governance·Git 작업 및 reviewer 연락은 하지 않았으며 타인 변경을 보존했다.
- 검증: PM STATE·PM-M3/PM-M4 readiness·WORKLOG와 PRD §5.12·§9.6·§13.1을 재독하고 root 근거·검색을 대조했다. 최종 `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md docs/PM-M3-readiness.md docs/PM-M4-readiness.md docs/PRD.md`는 exit 0으로 확인했다.

## 2026-09-09 — M4 branch 및 이미지 signed GET 잔여 위험 문구 정정

- 정정: root가 `feature/M4-posts` 분기를 이미 완료했으므로 PM STATE의 차단 요인을 “추가 branch/분기 변경 및 제품 구현 미승인”으로 바로잡았다. branch 생성 자체를 미완료로 쓰지 않는다.
- 정본 보완: PRD §9.6에서 권한 확인 없이 canonical object 주소/URL을 직접 조회하는 우회를 금지한다고 구체화했다. 권한 확인 후 발급된 signed GET은 유효기간 동안 bearer처럼 전달·재사용될 수 있고 권한 철회 시 즉시 회수되지 않을 수 있다는 잔여 위험, TTL·회수 의미를 `권고(미확정)`으로 남겼다. 절대적 즉시 회수·DRM은 새 결정으로 만들지 않았다.
- 범위: PM-M4 readiness에도 같은 잔여 위험을 반영했으며, endpoint·TTL·DB/응답·업로드 기술 등 상세 계약과 제품 구현은 여전히 미승인이다. 기존 기록은 수정하지 않고 보존했다.
- 검증: STATE·PM-M3/PM-M4 readiness·WORKLOG와 PRD §5.12·§9.6·§13.1을 재독하고 `rg` 내용 검색을 완료했다. `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md docs/PM-M3-readiness.md docs/PM-M4-readiness.md docs/PRD.md`는 exit 0이다. 제품·테스트·서버·UI·Git 작업과 reviewer 연락은 하지 않았다.

## 2026-09-09 — LocalStack U0 환경 조건 사실 동기화

- 근거: root `JOURNAL.md` 09:34 KST, [M4 회의 §11.5](../../../docs/governance/meetings/M4-20260908-posts.md) 및 [RISK-0012](../../../docs/governance/RISK-REGISTER.md)를 재독하고, [LocalStack 공식 2026.03.0 발표](https://blog.localstack.cloud/localstack-for-aws-release-2026-03-0/)와 [설치 문서](https://docs.localstack.cloud/aws/getting-started/installation/)를 확인했다. 2026-03-23 이후 통합 AWS 이미지에는 auth token·사용 조건이 필요하다.
- 환경 사실: 비밀값을 출력하지 않고 Process/User/Machine `LOCALSTACK_AUTH_TOKEN` 존재만 확인해 모두 `False`였다. 별도 허용된 read-only Docker 조회에서 `localstack/*` 이미지 및 `localstack/localstack` 컨테이너가 없음을 exit 0으로 확인했지만, 이는 계정 부재의 증거가 아니다.
- 산출물·범위: PRD §13.1의 LocalStack 선택과 AWS 실서비스 비용 없이 검증하려는 기존 경계는 유지하면서, LocalStack 자체 계정/토큰·라이선스 등 사용 조건 확인이 필요하고 무조건 무계정·무료로 전제하지 않는다는 사실 노트를 PM STATE·PM-M4 readiness·PRD §13.1에 반영했다. 계정 생성·구매·임의 구버전 고정·대체 서비스는 선택·승인하지 않았다.
- 현재 상태: 이미지 v2 §11 초안은 작성 완료·독립 3인 심의 중이고 상세 계약은 미승인이다. RISK-0012의 U0 환경 gate는 허가된 계정/토큰·사용 조건 또는 별도 승인된 환경을 확보하기 전 유지한다. 제품·테스트·서버·외부 프로비저닝·Git 작업은 하지 않았고 타인 변경을 보존했다.
- 검증: 편집한 PM 문서와 root 근거를 재독하고 `rg` 내용 검색 및 지정 문서 `git diff --check`를 실행해 exit 0을 확인했다.

## 2026-09-09 12:05 KST — U0 착수 승인·LocalStack 기동 상태 정합화

- 근거: 헌법→root `AGENTS.md`/`CLAUDE.md`→PM 지침/`STATE.md`를 순서대로 재독하고, backend/frontend/qa STATE·최근 WORKLOG, PRD §9.6·§13.1, M4 회의록 §12.3·§13·§14, root `JOURNAL.md` 11:39·12:00 KST 및 M4 worklog를 직접 대조했다.
- 정본 반영: 최신 사용자 원문 `승인`을 U0 로컬 private bucket/CORS·공식 확인 후 고정한 AWS SDK Java2 BOM/S3 `2.49.6`·Tika core `3.3.2` standalone harness·실제 브라우저 raw File PUT 성공/negative 검증으로만 기록했다. 근거는 [AWS SDK for Java 2.x release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.49.6)와 [Apache Tika download](https://tika.apache.org/download.html)이며, 버전 고정은 U0 검증용이지 제품 implementation dependency 승인이 아니다. 제품 endpoint·DB/migration·도메인/제품 FE·60초 TTL·5MB UI·전체 M4/U1·실제 AWS/구매/공개 게시 승인은 제외했으며, U0 승인 자체는 PASS가 아니다.
- 환경 사실: 회의 §13/JOURNAL 11:39의 사용자 `lstk` Community 로그인·라이선스 성공, `2026.8.1` image, Compose `healthy`, `s3=running`, `127.0.0.1:14566` 단일 publish 및 독립 startup QA 좁은 범위 `PASS`(parser 오류 0, 비차단 주의만 확인)를 최신 상태로 반영했다. 09:34 token/image 부재 확인은 당시 역사로 보존하고 인증 재질문은 하지 않는다. `gradle/u0/**`와 `frontend/u0/**`는 배정·진행 중이며 U0 버전 고정은 확인됐고 bucket/CORS·실측 결과는 아직 없다.
- 산출물: `docs/PRD.md`, `docs/PM-M4-readiness.md`, `.claude/team/pm/STATE.md`를 최소 갱신하고 이 항목을 append했다. 제품 소스·테스트·타 역할 STATE/WORKLOG·governance/worklog 원본·Git은 변경하지 않았다.
- 검증/미해결: 실제 Git branch `feature/M4-posts`, HEAD/제품 불변 및 현재 U0 경로 부재를 읽기 전용 확인했다. 편집 파일 재독·정본 범위 검색·`git diff --check`는 종료 전 실행하며, U0 성공/실패 증거와 M4 세부 계약 승인은 여전히 미해결이다.

## 2026-09-09 12:13 KST — U0 검증 버전·심의 스냅샷 정정

- root가 전달한 실제 `gradle/u0/build.gradle`을 읽어 U0용 AWS SDK Java 2.x BOM/S3 `2.49.6`·Apache Tika core `3.3.2` 고정을 확인했다. [AWS 공식 release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.49.6)와 [Apache Tika 공식 download](https://tika.apache.org/download.html)의 지원 정보를 대조했으며 제품 implementation dependency 승인으로 확대하지 않는다.
- 현재 이미지 v2 §11 독립 3인 심의는 `APPROVE_WITH_CHANGES/HIGH` 조건부 권고로 완료됐고 상세 계약·사용자 승인은 미승인으로 PM STATE/PM-M4-readiness의 현재 표현을 정정했다. 과거 WORKLOG의 당시 `심의 중` 문구는 보존했다.
- U0는 harness 작성·브라우저 probe 단계이며 실제 bucket/CORS·raw File PUT 성공/negative 보안 실측 및 U0 PASS는 아직 없다. 기존 `2026.8.1` LocalStack·14566 healthy/S3 running·startup QA PASS 증거와 M4 전체/U1 `BLOCKED` 경계는 유지한다.
- 검증: `gradle/u0/build.gradle` 버전 검색, PRD §9.6·§13.1/PM readiness/STATE/WORKLOG 재독을 수행했다. 제품 소스·테스트·타 역할 파일·governance/worklog·Git은 변경하지 않았다.

## 2026-09-09 12:15 KST — U0 경로 부재 문구 시점 정정

- 12:05 항목의 `현재 U0 경로 부재`는 그 시각 read-only 점검 시점의 사실이었다. 이후 root가 생성한 현재 U0 경로를 재확인해 `gradle/u0/build.gradle`, `gradle/u0/settings.gradle`, `frontend/u0/index.html`이 존재함을 확인했다.
- 버전 고정은 AWS SDK Java 2.x BOM/S3 `2.49.6`·Tika core `3.3.2`로 유지한다. 실제 bucket/CORS·raw File PUT 성공/negative 실측과 U0 PASS는 아직 없으며, 이 정정은 승인 범위·제품 구현·Git 전송을 확대하지 않는다.
- 검증: `Get-ChildItem` 경로 목록과 `rg` 버전 검색을 재실행했다. PM 문서·상태·기록 외 파일은 변경하지 않았다.

## 2026-09-09 12:37 KST — U0 실측·보안 gate 실패 정합화

- 근거: root 최신 U0 실행 인계와 실제 `gradle/u0/build.gradle`·`U0Harness.java`·`frontend/u0/index.html`·`compose.localstack.yml`을 대조했다. backend/frontend/qa STATE에는 각각 실행 전·대기 시점의 스냅샷이 남아 있어 타 역할 파일은 수정하지 않고 root의 후속 실측으로 구분했다.
- 실측: standalone harness compile exit 0, Java self-check `19 cases / 18 PASS`, `/verify` `28 checks / 27 PASS`, process exit 1. fresh private bucket의 독립 Codex 브라우저 raw File PUT도 `19 cases / 18 PASS`, `/verify 28 checks / 27 PASS`였다. 허용 4 MIME, 빈 파일/5MiB 경계, 크기·byte·header·checksum 변조, expiry/replay, CORS, HEAD bounded GET 및 Tika 내용 판별은 성공했다.
- 판정: 유일한 실패는 `unsigned-private-get-403`에서 기대 403 대신 200을 받은 것이다. Compose에는 `S3_SKIP_SIGNATURE_VALIDATION=0`·`S3_VALIDATE_SIGNATURES=1`만 있고 `ENFORCE_IAM=1`은 비활성이다. LocalStack `healthy`/S3 running startup PASS와 U0 보안 gate를 분리하며, U0 전체는 `BLOCKED`·PASS 아님으로 갱신했다.
- 후속·경계: 현재 계정에서 강제 IAM을 사용할 수 있는지 및 테스트 데이터 초기화를 수반한 재기동·재검증 허용 여부는 별도 사용자 선택 대기다. 자동 설정 완화·재기동·데이터 초기화·자격증명 재질문은 하지 않는다. 실제 AWS·구매·제품 U1·60초 TTL·5MB UI·전체 M4 승인은 미승인으로 유지했다.
- 산출물: `docs/PRD.md`, `docs/PM-M4-readiness.md`, `.claude/team/pm/STATE.md`의 현재 스냅샷을 최소 갱신하고 이 항목을 append했다. 기존 WORKLOG와 타 역할·governance/worklog 원본·코드·Git 변경은 보존했다.
- 검증: 편집 파일 재독, U0 경로·버전·Compose 설정 검색, 역할 STATE 대조 후 whitespace scan 및 `git diff --check`를 종료 전에 수행한다. U0 재기동·데이터 초기화·추가 제품 검증은 하지 않았다.

## 2026-09-09 12:56 KST — IAM 지원 진단 결과·재생성 승인 경계 동기화

- 최신 사용자 `응 진행해`는 비용·구매 없이 IAM 지원 확인과 테스트 bucket 2개·객체 16개 초기화를 수반한 LocalStack 재생성·재검증을 승인했다. 그러나 root의 사전 read-only offline 라이선스 진단(12:55:48, exit 0)은 `cached_license_valid=true`, IAM 기본 service 허용 `true`, 정확한 `localstack.platform.plugin/iam-enforcement` 허용 `false`를 확인했고, 설치 2026.8.1 plugin metadata 및 현재 `licenseProductEntitlements`는 정상 검증됐다.
- 따라서 현재 기능 gate는 데이터/재생성으로 해소되지 않는 라이선스 지원 문제로 `BLOCKED`를 유지한다. 컨테이너·설정·기존 합성 자원 2개 bucket/16객체는 보존하고 재기동·재시험하지 않는다. 라이선스 검사 우회·구매·온라인 활성화·비밀 출력·설정 완화는 하지 않는다.
- PRD §9.6·§13.1, PM-M4 readiness, PM STATE의 현재 스냅샷을 최신 결과로 최소 갱신했다. 이전 U0 `18/19 cases·27/28 verify`는 역사 증거로 유지하고, U1·제품 전체 M4·실제 AWS·다른 emulator·구매·보안 완화 승인은 여전히 미승인이다. 대체 검증 전략·새 지원 기능 확보 방향은 `권고(미확정)`이며 추가 구현·전체 테스트는 하지 않았다.

## 2026-09-23 13:22 KST — SeaweedFS U0 대체 검증 승인·U1 의미 정정

- 근거: PRD §9.6·§13.1, [M4 기획 심의](../../../docs/governance/meetings/M4-20260908-posts.md) §11.5·§12·§14~§16, 최신 사용자 원문 `승인. 내가 할 일을 알려줘.`를 대조했다. 사용자 승인은 제품 스택이나 M4 전체가 아니라 비용·구매 없는 U0 대체 검증의 실행 범위만 추가했다.
- 승인 반영: 공식 [SeaweedFS 4.47](https://github.com/seaweedfs/seaweedfs/releases/tag/4.47) native `weed.exe`를 U0 전용으로 다운로드·기동·재검증할 수 있다. SeaweedFS S3는 `127.0.0.1:14568`, 기존 LocalStack `127.0.0.1:14566`과 컨테이너·설정·합성 자원은 보존하고, 기존 브라우저 harness `127.0.0.1:14567` 및 AWS SDK Java2 S3 `2.49.6`·Tika core `3.3.2`는 유지한다.
- U0 상태: 승인됐지만 SeaweedFS 다운로드·기동·재검증은 아직 미완료다. 기존 19 cases/18 PASS·28 checks/27 PASS·unsigned private GET 200 실패는 역사 증거이며, 대체 검증은 19/19 cases·28/28 checks 및 unsigned private GET 403을 요구한다. 미지원 API/의미 차이는 skip하지 않고 실패 또는 `BLOCKED`로 기록한다.
- 범위 정정: AWS 전용 PAB/Ownership 의미와 실제 AWS 검증은 별도다. 회의 §11.5·§12의 U1은 U0 통과와 별도 M4 소비 계약 승인 뒤 제품 도메인/FE 연결을 검증하는 최소 vertical slice이며 `실제 AWS U1`은 정본 단계명이 아니다. 제품 계약·의존성·LocalStack 선택·M4 전체 구현·실제 AWS는 변경/승인하지 않았다.
- 검증/미실행: 문서 정본·PM readiness·PM STATE·PM WORKLOG만 갱신했다. SeaweedFS 다운로드·설치·실행·컨테이너·테스트·Git·governance/worklog/JOURNAL은 건드리지 않았다. 사용자에게 추가 작업이 없음을 안내한 상태이며, 이후 실행은 승인된 U0 대체 검증 범위에서만 진행한다.

## 2026-09-23 13:38 KST — SeaweedFS U0 최종 runtime 실측·전체 BLOCKED 정합화

- 근거: root가 제공한 `build/u0-seaweedfs-core-self-check.log`, `build/u0-seaweedfs-browser-evidence.md`, `build/u0-seaweedfs-browser-v2-server.log`와 사용자 인계 결과를 재독했다. 공식 native SeaweedFS 4.47 설치·기동·실측은 완료됐고, 기존 LocalStack `127.0.0.1:14566`·브라우저 harness `127.0.0.1:14567`·AWS SDK Java2 S3 `2.49.6`·Tika core `3.3.2` 경계는 유지됐다.
- Java 결과: **19/19 cases PASS**, `/verify` **27/28**, process exit 1. `PublicAccessBlock`은 `UNSUPPORTED HTTP 501`, `BucketOwnerEnforced`/OwnershipControls는 PASS, unsigned private GET 403 및 negative object absence는 PASS였다.
- fresh CUA 브라우저 결과: **19 cases 중 18 PASS**, `/verify` **27/28**. `PublicAccessBlock`은 `UNSUPPORTED HTTP 501`, OwnershipControls는 PASS였다. `over-5mb-signed-at-max`는 fetch `TypeError/CORS`로 HTTP 상태가 노출되지 않았으나 객체 부재 검증은 PASS했다. 해당 TypeError의 CORS 대 connection reset 원인은 미확정이다.
- 판정: 기준 완화·검사 skip·PAB 501을 PASS로 치환하지 않는다. SeaweedFS 대체 실측은 완료됐지만 전체 U0는 `BLOCKED`이며, U1은 별도 미승인 제품 최소 vertical slice, 실제 AWS 검증은 별도 범위다. 사용자 추가 조치·계정·결제·키 요청은 없고, 프로세스 종료·합성 데이터 보존은 root가 처리한다.
- 산출물/범위: `docs/PRD.md`, `docs/PM-M4-readiness.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 현재 runtime 상태로 갱신했다. 코드·Git·설치·추가 실행·governance/worklog/JOURNAL은 변경하지 않았다.

## 2026-09-23 KST — SeaweedFS 잔여 문제 진단 결과 동기화

- **범위**: 사용자 요청으로 PAB 501과 oversized signed-at-max browser 오류를 재현·진단한 결과만 PM 정본에 반영했다. 추가 provider/vendor patch, 제품 계약 변경, 실제 AWS 검증 또는 U1 승인은 하지 않았다.
- **근거**: `build/u0-seaweedfs-diagnostic-evidence.md`, M4 회의 §19, 공식 SeaweedFS 4.47 route/handler. PAB route는 존재하지만 handler는 `ErrNotImplemented`를 무조건 반환한다. fresh browser는 **18/19 cases PASS**·`/verify` 27/28, same-file XHR은 `status 0/error/noheaders`였고 fetch→XHR 교체로 해결되지 않았다.
- **관찰 경계**: raw HTTP/TCP header-only 요청은 `403 SignatureDoesNotMatch`·exact ACAO·Connection close를 반환했으나 이는 browser 403 또는 reset의 직접 관찰이 아니다. CORS와 connection reset 원인은 미확정이며 기준 완화·허위 PASS는 금지한다.
- **상태**: 두 문제 모두 미해결, U0 전체 BLOCKED, U1 미승인. 임시 XHR 진단 코드는 제거 중이고 제품은 불변이다. runtime `14567/14568`은 종료됐고 LISTENING 0, 데이터는 기존 `0/8/0/8`+신규 `8/8`로 총 6 bucket 보존됐다. 독립 QA 진행 중이다.

## 2026-10-03 KST — commit-review: M4 공개 승인 경계·XHR 정합성 동기화

- **근거와 공개 범위**: 최신 사용자 원문 `변경 사항 확인하고 커밋 및 푸시`를 현재 검토된 작업 트리의 Git 공개 승인으로 반영한다. `feature/M4-posts`에 포함된 M4 문서와 U0 검증기(`docs/worklog/M4-posts.md`, M4 회의록, `gradle/u0/**`, `frontend/u0/**`, `qa/M4-u0-review.md` 및 검토된 팀 기록)는 root가 commit/push할 수 있는 범위다. 기존 기록의 당시 M3 payload 제외 사실은 과거 사실로 보존한다.
- **승인 경계**: 이 Git 공개 승인은 M4 제품 endpoint·DB/migration·도메인/제품 FE 구현, U0 `PASS`, 세부 Q1~Q5 계약 확정, 실제 AWS·구매, PR Ready/merge를 승인하지 않는다. M4 전체와 U0는 PAB HTTP 501 및 브라우저 oversized status 미노출로 계속 `BLOCKED`다.
- **XHR 정정**: PM STATE의 `임시 XHR 진단 코드는 제거 중` 현재형 문구를 제거 완료로 정정했다. `frontend/u0/index.html` 현재 SHA-256은 `FEBE910EA47F526AF901C86502EE774E51874E417FC51D914EC8353E23691DDE`, XHR 진단 마커는 없고, `build/u0-seaweedfs-diagnostic-evidence.md`·M4 worklog의 `U0_CLEAN_PAGE_SELF_CHECK=PASS`(jsdom, exit 0)와 일치한다. 기존 WORKLOG의 역사 항목은 append-only로 수정하지 않았다.
- **검증**: backend/frontend/qa STATE·WORKLOG, M4 worklog·회의록·JOURNAL·진단 evidence와 실제 Git을 재대조했다. branch `feature/M4-posts`, HEAD `59badfe`, `docs/PRD.md` 변경 없음, PM 문서 `git diff --check` exit 0, PM STATE stale phrase 0건을 확인했다. PM은 stage/commit/push를 수행하지 않았고 타 경로는 수정하지 않았다.

## 2026-10-03 14:24 KST — 로컬 M4 실행 지시·U0 선행 gate 분리 정합화

- **한 일**: 헌법→`AGENTS.md`/`CLAUDE.md`→PM 지침→PM `STATE.md`를 순서대로 재독하고 `$brief` 절차에 따라 backend/frontend/qa/pm STATE·최근 WORKLOG, 실제 Git, PRD §9·§10, PM-M4 readiness, M4 회의록 §20.5~§20.6을 직접 대조했다. 실제 checkout은 `feature/M4-posts`, HEAD `97567d9`이며 PM 외 변경은 보존했다.
- **사용자 지시 반영**: 최신 원문 **`$brief 로컬에서 검증할 수 있는 수준이면 되니까 localstack 등은 제쳐 두고 m4 완료까지 달려`**를 M4 로컬 구현·검증 실행 지시로 기록했다. LocalStack·SeaweedFS·AWS U0 호환성 검증을 M4 구현 선행 gate에서 제외하되 U0 과거 실패·미실측·SeaweedFS 잔여 문제와 `BLOCKED` 판정은 유지했다.
- **승인 경계**: PRD §9.7·PM-M4 readiness·PM STATE에 기존 S3 presigned upload API/비공개 S3 접근 방향은 유지하고, 로컬 파일 저장·multipart/content API·새 endpoint/schema/migration/의존성은 이 지시로 확정하지 않는다고 기록했다. §20.6의 Product/Delivery `APPROVE_WITH_CHANGES/HIGH`, Architecture 전체 계약 `BLOCKED`, 리더 종합 `USER_DECISION_REQUIRED`와 새 저장 방식 사용자 선택 대기를 반영했다.
- **수치·출처 재확인**: SeaweedFS Java `19/19 cases PASS`, `/verify 27/28`, process exit 1; fresh browser `18/19 cases PASS`, `/verify 27/28`; PAB `HTTP 501`, oversized signed-at-max `status 0/error/noheaders`를 backend/frontend/qa STATE와 evidence에서 재확인했다. 과거 LocalStack `18/19`, `/verify 27/28`, unsigned private GET `expected 403/actual 200`도 역사로 유지했다.
- **산출물**: `docs/PRD.md` §9.7, `docs/PM-M4-readiness.md` 현재 판정·실행 gate·검증 경계·출처, `.claude/team/pm/STATE.md` 최신 스냅샷을 갱신했다. 회의록·root worklog·JOURNAL·제품 소스·테스트·Git stage/commit/push는 수행하지 않았다.
- **검증**: 편집 파일 4개를 재독하고 SHA-256/내용 검색으로 PRD §9.7·PM-M4 최신 gate·§20.6 상태를 확인했다. `git diff --check -- docs/PRD.md docs/PM-M4-readiness.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`는 `PM_DIFF_CHECK_EXIT=0`, `rg -n -P "[ \\t]+$" ...`는 일치 없음(`PM_TRAILING_WHITESPACE_EXIT=1`, rg no-match)으로 확인했다. 미해결은 새 저장 방식·multipart/content API 및 M4 세부 계약의 사용자 결정이다.

## 2026-10-03 15:08 KST — 로컬 파일 저장 M4 결정 반영

- **사용자 결정**: 사용자의 질문(“M4 이미지는 S3 연동을 후속으로 남기고, 서버의 로컬 폴더에 실제 저장하는 방식으로 완성해도 될까요? 글·이미지 접근권한과 5MB 제한은 유지하고, 로컬 업로드 API를 사용합니다.”)에 대한 최신 응답 **`로컬 파일 저장으로 M4 완성`**을 반영했다.
- **정본 반영**: `docs/PRD.md` §9.7과 `docs/PM-M4-readiness.md`에 M4 이미지의 서버 로컬 폴더 실제 저장·로컬 업로드 API·글/이미지 접근권한·최대 5MB 제한을 확정된 사용자 결정으로 기록하고, S3 presigned/비공개 S3 연동은 후속 범위로 분리했다. 기존 LocalStack/SeaweedFS/AWS U0 선행 gate 제외와 과거 U0 `BLOCKED` 수치는 유지했다.
- **승인 경계**: 회의록 §20.5~§20.6의 세부 endpoint/schema/migration/파일 lifecycle·실패 원자성·이미지 소비 계약은 root 작성 구현계획과 계획 검토 전 확정하지 않는다. 계획 검토 전 제품 코드 착수 없음도 PM STATE/readiness에 현재화했다. governance/worklog/JOURNAL은 편집하지 않았다.
- **검증**: 변경 파일 4개를 재독하고 SHA-256/`rg` 내용 검색으로 로컬 폴더 저장·로컬 업로드 API·접근권한·5MB·S3 후속·계획 검토 전 코드 미착수 문구를 확인했다. `git diff --check -- docs/PRD.md docs/PM-M4-readiness.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`는 `PM_DIFF_CHECK_EXIT=0`, trailing whitespace 검색은 일치 없음(`PM_TRAILING_WHITESPACE_EXIT=1`, rg no-match)이다. 미해결은 계획 검토 전 대기하는 §20 세부 구현계약이다.

## 2026-10-03 15:20 KST — M4 실행계획·공유 계약 사용자 승인 동기화

- **사용자 승인**: 최신 사용자 `yes`를 M4 worklog `Task1~7` 실행계획과 `§20.1~§20.5` 공유 HTTP/DB/콘텐츠/UI 계약의 구현·실행 승인으로 반영했다. 로컬 폴더 저장·로컬 업로드 API·글/이미지 접근권한·5MB 제한은 유지하고, S3 연동·U0 provider 검증은 후속 범위로 분리한다.
- **실행 상태**: `m4_backend_impl`·`m4_frontend_impl`의 병렬 구현과 `m4_qa_acceptance`의 독립 QA가 실행 중임을 PRD §9.7·PM-M4 readiness·PM STATE에 반영했다. M4 완료는 실제 구현·테스트·독립 QA 증거 전까지 선언하지 않는다.
- **예외 경계**: PRD §§4.6·5.5·5.12·7·10·13.1의 S3/LocalStack 전제는 §9.7 로컬 M4 예외에 따라 이 마일스톤 구현·검증에서 후속으로 분리했다. S3 후속·M5 이후 제외·기존 데이터 보존·§9.6 U0 실패 이력은 유지한다. 제품 소스·REQUIREMENTS·governance/worklog/JOURNAL은 편집하지 않았다.
- **검증**: 변경 파일 4개를 재독하고 SHA-256/`rg` 내용 검색으로 Task1~7, §20.1~§20.5, BE/FE 병렬 구현, 독립 QA, S3 후속, M5 제외, 기존 데이터 보존 및 §9.6 U0 역사 보존을 확인했다. `git diff --check -- docs/PRD.md docs/PM-M4-readiness.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`는 `PM_DIFF_CHECK_EXIT=0`, trailing whitespace 검색은 일치 없음(`PM_TRAILING_WHITESPACE_EXIT=1`, rg no-match)이다. 추가 제품 구현·테스트·Git 조작은 하지 않았다.

## 2026-10-03 17:27 KST — 브라우저 파일 선택 검증 범위·최신 M4 인수 상태 동기화

- **사용자 결정**: 최신 사용자 응답 **`브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해`**를 반영했다. Chrome 확장의 파일 URL 접근 권한은 확대하지 않으며, 제외는 실제 브라우저 파일 선택·전송 자동화뿐이다. multipart API·FE 파일 입력/업로드 자동 테스트, 실제 파일 저장, 5MiB 바이트 제한, 글·이미지 접근권한, 브라우저 이미지 표시, 재시작 보존은 M4 인수 조건으로 유지하고 미실행 파일 선택을 브라우저 PASS로 기록하지 않는다.
- **정본 반영**: `docs/PRD.md` §9.7과 PM `STATE.md`에 위 범위와 최신 로컬 인수 상태를 기록했다. S3/LocalStack 후속, U0 과거 `BLOCKED`, M5 제외·기존 데이터 보존, M4 미완료·커밋/푸시 금지 경계는 유지했다.
- **실제 진행 근거**: `docs/worklog/M4-posts.md` 2026-10-03 17:20 기록의 root runtime JAR `3890657091DDDD7927DC53CBB4E14891125F568F51739B69F32793053723B36A`에서 HTTP smoke exit 0, 실제 PNG의 비로그인 브라우저 managed Blob 표시(`complete=true`, `96×64`) 및 로컬 파일·응답 SHA-256 일치를 확인했다. 같은 시점 FE 스냅샷은 28 files/302 tests, lint/build exit 0이었다. 이 실측은 전체 리뷰 수정 전 스냅샷으로만 기록한다.
- **현재 미해결**: 독립 전체 리뷰는 Critical 0·Important 7·Minor 1, `Ready to merge: No`로 R1~R8 수정을 요구했고, backend full tests·수정 후 전체 재검증·독립 재리뷰가 진행 중이다. 새 `PostV3MigrationTest`와 성공 로그는 reviewer 추가 확인으로 migration 증거 부족 지적을 철회했지만 M4 완료로 선언하지 않는다.
- **범위·검증**: 이번 턴은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했으며 제품 소스·타 역할 파일·M4 원 worklog·governance·Git은 수정하지 않았다. 세 파일 재독·SHA-256 확인과 내용 검색은 `PM_CONTENT_SEARCH_EXIT=0`, `git diff --check`는 `PM_DIFF_CHECK_EXIT=0`, trailing whitespace 검색은 일치 없음(`PM_TRAILING_WHITESPACE_EXIT=1`, rg no-match)이다.

## 2026-10-03 18:16 KST — M4 로컬 제품 검증·최종 artifact 정본 동기화

- **근거 대조**: backend STATE와 실제 로그를 재독했다. `build/m4-final-backend-full-20261003-1900.log`는 `BUILD SUCCESSFUL`(13m49s, exit 0)이고, `build/m4-final-junit-20261003-1915/test-results` 66개 XML을 직접 집계해 426 tests, failures/errors/skips `0/0/0`을 확인했다. `build/m4-final-bootjar-20261003-1920.log`도 `BUILD SUCCESSFUL`이며 artifact `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`는 66,354,802 bytes, SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`로 재확인했다.
- **FE·리뷰 대조**: frontend STATE 및 `build/m4-frontend-final-results.json`을 재독해 `success=true`, 28 files/311 passed/0 failed/0 pending을 확인했고 lint/build exit 0 기록과 일치시켰다. R1~R8 독립 소스 재검토는 추가 Critical/Important 회귀 없이 종료 가능으로 정리됐다. R7 후속 영향 범위도 2 suites/40 passed로 기록되어 있다.
- **실제 인수 근거**: M4 worklog의 최종 artifact 7차 HTTP smoke, malformed content `400/VALIDATION_001`, ordered-list `start/type` 보존, `page=2147483647&size=20` 정상 empty 응답, 재시작 후 기존 PNG URL·메타데이터·395 bytes·Chrome 표시 및 SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67` 일치를 대조했다. 사용자가 제외한 브라우저 파일 선택·전송 자동화는 실행하지 않았고 PASS로 올리지 않았다.
- **정본 변경**: `docs/PRD.md` §9.7에 최종 로컬 제품 검증 수치와 남은 closeout 경계를 append하고, §10 M4 설명을 승인된 로컬 multipart/파일 저장 범위와 S3 후속 범위에 맞췄다. PM `STATE.md`를 현재 단계/진행 중/다음 작업/차단 요인 구조로 갱신했다. LocalStack·SeaweedFS/U0 `BLOCKED`, S3 후속, M5 제외 및 기존 데이터 보존 경계는 유지했다.
- **남은 사항**: 최종 QA 기록·`POST /uploads` OpenAPI 201 표기 확인 및 root의 명시 파일 stage/commit/push/PR/merge는 아직 완료로 기록하지 않는다. PM은 제품 코드·QA 산출물·M4 원 worklog·Git을 편집하지 않았다.
- **검증**: 편집 파일을 재독하고, backend JUnit/FE JSON/bootJar hash를 위 명령으로 직접 확인했다. 후속으로 PM 소유 파일 `git diff --check`와 핵심 결정·수치 검색을 수행해 closeout 기록의 오탈자·trailing whitespace를 확인한다.

## 2026-10-03 18:20 KST — 최종 runtime/OpenAPI·독립 리뷰 보완 동기화

- **추가 근거**: `docs/worklog/M4-posts.md`의 18:15 최종 항목을 대조했다. 최종 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF` runtime에서 OpenAPI `POST /posts` 201, `POST /uploads` 201, content GET operation과 실제 HTTP 응답이 PASS였다.
- **이미지 응답 근거**: 기존 PNG는 HTTP 200/image/png/395 bytes였고 로컬 파일·HTTP 응답·기존 fixture의 SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`가 동일했다. `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`도 확인됐다.
- **독립 리뷰 상태**: `/root/m4_whole_review` 최종 판정은 `Ready to merge: Yes`, 잔여 Critical/Important/Minor `0`으로 보완됐다. 이전의 OpenAPI 대기 문구를 현재 정본에서 해소하고, 최종 QA 인수 기록 문서화와 Git closeout만 잔여로 분리했다.
- **정본 변경**: `docs/PRD.md` §9.7, `.claude/team/pm/STATE.md`의 OpenAPI 대기 문구를 제거하고 최종 runtime·헤더·독립 리뷰 수치를 반영했다. 브라우저 파일 선택/전송 자동화 제외, S3/U0 후속, M5 제외 경계는 유지했다.
- **남은 사항**: 최종 QA 기록과 root의 명시 파일 stage/commit/push/PR/merge는 아직 완료로 기록하지 않는다. PM은 제품·테스트·M4 원 worklog·Git을 수정하지 않았다.
- **검증**: 편집한 PRD/STATE/WORKLOG를 재독하고 `git diff --check -- docs/PRD.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md` 및 trailing whitespace 검색을 재실행한다.

## 2026-11-07 10:51 KST — M5 독립 심의 결과·A-v2 사용자 결정 대기 상태 동기화

- **근거 대조**: 최신 `docs/worklog/M5-universe.md` [계획]/[이슈·결정], `docs/governance/meetings/M5-20261107-universe.md` §1~§8, `DECISION-REGISTER.md`의 `M5-20261107-universe`, `RISK-REGISTER.md`의 `RISK-0014`를 재독했다. M5 착수 승인, `feature/M5-universe` 시작 HEAD `7b03de5`, M4 `dev` 통합 및 M5 구현 전 상태를 확인했다.
- **독립 심의 결과**: Product `APPROVE_WITH_CHANGES/88/HIGH`, Architecture `APPROVE_WITH_CHANGES/93/HIGH`, Delivery & Risk `APPROVE_WITH_CHANGES/90/HIGH`로 세 검토자 모두 A 방향을 조건부 권고했다. 공통 보완은 방향별 snapshot과 각 PENDING `requestId`, 단일 pair-lock 경계, `UNI_004/005` 404/409 선형화, 개인정보 최소 DTO, MySQL 경쟁·OpenAPI·회귀 검증, 디자인 원본 복구다.
- **진행자 종합 반영**: 최종 권고 A-v2는 V1/directional unique 유지, 모든 mutation의 ID 오름차순 pair lock 및 lock 후 양방향 재조회, 보호 `FR-UNI-12` snapshot(`availability`, `outgoing`, `incoming`, requestId), `UNI_004` 동일 404·`UNI_005` 상태 전이 409, SUSPENDED/deleted 경계, Blog hero 우선순위, M8 알림 row 제외를 포함한다. 이는 사용자 승인 전 제안이며 PM이 새 제품 결정으로 확정하지 않았다.
- **상태 판정**: 회의·결정 레지스터는 `USER_DECISION_REQUIRED`, 위험 레지스터의 `RISK-0014`는 `OPEN(USER_DECISION_REQUIRED)`다. A-v2 사용자 승인 전 M5 제품 구현·정본 확정·Git 작업은 보류한다.
- **디자인 gate**: `docs/design/AGENTS.md`가 명시한 `DESIGN-SYSTEM.md`, 두 `.dc.html`, `support.js`가 현재 clone에 없고 `AGENTS.md`만 존재함을 재확인했다. 원본 복구 전 최종 FE 시각 PASS와 M5 완료 판정을 하지 않는다.
- **산출물**: `.claude/team/pm/STATE.md`를 현재 단계/진행 중/다음 작업/차단 요인 구조로 덮어썼고, 이 항목을 append했다. `docs/worklog/M5-universe.md`, 회의록, 결정/위험 레지스터, PRD, REQUIREMENTS, 제품 소스, Git은 수정하지 않았다.
- **검증**: PM STATE·WORKLOG 재독, M5 worklog/회의록/레지스터 내용 검색, `git status --short --branch`로 기존 변경 보존을 확인했다. 종료 전에 PM 두 파일의 `git diff --check`와 핵심 상태 검색을 수행한다.
- **미해결**: 사용자 A-v2 결정, 디자인 원본 복구, 승인 후 backend/frontend 구현·독립 QA·실제 MySQL 경쟁 및 1440px 시각 검증.

## 2026-11-07 11:59 KST — 사용자 A-v2 승인·M5 정본 반영

- **사용자 결정**: 사용자가 `M5-20261107-universe` §6의 A-v2를 승인했다. 승인 범위는 V1·directional unique 유지, ID 오름차순 pair lock과 lock 후 양방향 재조회, 방향별 `FR-UNI-12` snapshot·PENDING `requestId`, `UNI_004/005`, SUSPENDED/deleted 경계, Blog hero 우선순위, M8 알림 row 및 RSS·신고 제외다.
- **PRD 반영**: `docs/PRD.md` 메타데이터를 2026-11-07/2.1로 갱신하고 §4.2~§4.4에 Universe 페이징·인증/no-store·오류 계약을, §5.6에 `FR-UNI-12`와 pair lock·최소 DTO를, §7.1/§7.6에 BlogPage·SettingsUniversePage API/UI 계약을, §9.8에 사용자 확정 결정 전문을, §10 M5·§11 테스트·§12 DoD·§13 확정 목록에 경계를 동기화했다.
- **상태 반영**: `.claude/team/pm/STATE.md`를 `APPROVED / 정본 반영 진행`으로 갱신했다. 사용자 승인으로 계약 승인 차단은 해소했지만 디자인 원본 부재로 최종 FE 시각 PASS와 M5 완료 판정은 여전히 보류한다.
- **범위·소유권**: 이번 턴에는 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 편집했다. `REQUIREMENTS.md`, governance 회의록/레지스터, M5 worklog, 제품 소스, Git은 수정하지 않았다.
- **검증**: 회의록 §6, 수정한 PRD 관련 절, STATE를 재독하고 A-v2 핵심 문자열·FR-UNI-12·디자인 gate를 검색한다. 종료 전에 PM 소유 파일에 `git diff --check`를 실행한다.
- **미해결**: backend/frontend A-v2 구현·RED→GREEN 테스트·실제 MySQL 경쟁 검증·독립 QA, 디자인 원본 복구와 1440px 시각 대조. 구현 중 새 API/보안/개인정보 결정이 필요하면 별도 심의를 연다.

## 2026-11-07 13:04 KST — M5 구현·QA 결과 및 시각 blocker 상태 정합화

- **근거 대조**: `.claude/team/backend/STATE.md`·`WORKLOG.md`, `.claude/team/frontend/STATE.md`·`WORKLOG.md`, `.claude/team/qa/STATE.md`, `qa/M5-review.md` 수정 루프 최종 판정·closure matrix, `docs/worklog/M5-universe.md`, 현재 Git 상태를 재독했다. `find docs/design -maxdepth 1 -type f -print` 결과는 `docs/design/AGENTS.md` 하나뿐이었다.
- **현재 구현·검증 사실**: 사용자 승인 A-v2 구현과 수정 루프가 완료됐고 독립 QA의 `BE-001`, `BE-002`, `QA-001`, `QA-002`, `DOC-001`은 모두 `CLOSED`다. 기능/API/자동검증은 `PASS · merge-ready YES`다. backend 전체는 `70 files / 457 tests / failures 0 / errors 0 / skipped 0`, `bootJar BUILD SUCCESSFUL`; frontend는 `31 files / 336 tests`, lint/build exit 0이다. — `qa/M5-review.md`, `.claude/team/frontend/STATE.md`
- **최종 인수 경계**: `DESIGN-SYSTEM.md`, `ZeroVerse Pages.dc.html`, `ZeroVerse Main Feed v2.dc.html`, `support.js` 원본 4개가 없어 `V-001`은 `OPEN / BLOCKER`다. 따라서 1440px 시각 DoD, 전체 M5 final acceptance, Git merge-ready는 `NO`로 유지하며 기능/API PASS를 전체 완료로 확대하지 않는다.
- **STATE 반영**: PM `STATE.md`를 `APPROVED / 구현·자동검증 완료, 시각 인수 대기`로 덮어쓰고, 다음 작업을 `디자인 원본 복구 → 독립 1440px QA → root final acceptance 및 Git closeout` 순서로 갱신했다. 기존의 “제품 구현 아직 없음/증거 없음” 현재형 판단은 제거했다.
- **Git·소유권**: 아직 M5 commit/PR/merge는 없다. 이번 정합성 갱신은 PM 소유 `.claude/team/pm/STATE.md` 덮어쓰기와 이 `WORKLOG.md` append만 수행했으며 제품 소스·PRD·governance·M5 worklog·QA 산출물·Git은 수정하지 않았다.
- **검증**: 역할 STATE·QA closure·M5 worklog·design 파일 목록·`git branch --show-current`/`git log`/`git status`를 직접 대조했다. 종료 전에 PM 두 파일 `git diff --check`, trailing whitespace, 변경 파일 범위를 다시 확인한다.
- **미해결**: 디자인 원본 복구와 1440px 독립 시각 대조만 전체 M5 final acceptance를 막는 열린 gate다. 원본 복구 전 시각 PASS나 merge 승인을 기록하지 않는다.

## 2026-11-07 16:42 KST — M5 디자인 원본 복구·시각 QA 진행 상태 동기화

- **근거 대조**: `docs/PRD.md` §9.8·§10~§13, `docs/design/` 파일 목록, `build/m5-visual-qa/evidence.json`, frontend 인계 및 현재 Git 상태를 직접 대조했다. 디자인 원본 4개(`DESIGN-SYSTEM.md`, 두 `.dc.html`, `support.js`)가 현재 존재하고, evidence viewport는 `1440×1000`이다.
- **시각 evidence**: `evidence.json`과 root screenshot 대조에서 SettingsUniversePage 여섯 카드, refresh/account switch, Blog hero 7개 관계 상태, 단일 hero action·RSS 미노출 checks가 PASS로 기록됐다. FE 최신 인계는 `31 files / 337 tests`, lint/build exit 0이다.
- **잔여 gate**: evidence의 신청 CTA 기대 카피는 기존 `유니버스 신청 ✦`이므로 마지막 카피를 `✦ 유니버스 신청`으로 보정한 fresh 검증과 독립 QA final verdict가 필요하다. 이 verdict 전에는 전체 M5 final acceptance·시각 PASS·Git merge-ready를 선언하지 않는다.
- **정본 반영**: PRD §9.8의 시각 상태, §10 M5 범위, §11 M5 FE 검증, §12 DoD, §13 확정 결정에서 “원본 부재” 현재형을 “원본 복구·시각 QA 진행”으로 갱신했다. A-v2 계약 원문·사용자 승인 이력과 원본 복구 전 PASS 금지 원칙은 보존했다.
- **STATE 반영**: PM `STATE.md`를 `APPROVED / 구현·자동검증 완료, 시각 QA final verdict 대기`로 갱신하고 다음 작업을 CTA 보정 → 독립 QA final verdict → root final acceptance/Git closeout 순으로 정리했다. 기능/API/자동검증 PASS와 전체 final acceptance NO를 분리 유지했다.
- **범위·소유권**: 이번 턴은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. frontend/QA 산출물, governance, M5 worklog, Git은 수정하지 않았고 Git 조작도 하지 않았다.
- **미해결**: 마지막 CTA 카피 보정, QA final verdict, 그 후 root의 최종 acceptance 및 Git closeout.

## 2026-11-07 16:50 KST — M5 최종 QA 인수 PASS·Git closeout 대기 동기화

- **근거 대조**: `qa/M5-review.md` 최종 독립 브라우저 재검토·finding closure·최종 판정, 최신 frontend STATE/WORKLOG, `build/m5-visual-qa/evidence.json`, `docs/design/` 원본과 현재 Git을 재독했다.
- **최종 QA 사실**: `BE-001`, `BE-002`, `QA-001`, `QA-002`, `DOC-001`, `V-001`, `V-002`가 모두 `CLOSED`다. Backend root evidence는 `70 XML suites / 457 tests / failures-errors-skips 0/0/0`, bootJar 성공; frontend는 `31 files / 338 tests`, lint/build exit 0이다.
- **브라우저 인수 사실**: 실제 API 기반 1440×1000 실행은 `14 screenshots / 43 checks / 28 PASS / failures=[]`이며 SettingsUniversePage 여섯 카드, refresh/account switch, Blog hero 7상태·guest·CTA `✦ 유니버스 신청`·RSS 미노출과 원본 screenshot 대조를 PASS로 기록했다. QA 최종 판정은 전체 M5 final acceptance **PASS · merge-ready YES**다.
- **정본 반영**: PRD §9.8·§10 M5·§11 M5 FE·§12 DoD·§13 확정 결정에서 시각 gate를 PASS로 전환하고 V-001/V-002 closure와 CTA 보정을 반영했다. A-v2 계약 원문과 승인 이력은 보존했다.
- **STATE 반영**: PM `STATE.md`를 `APPROVED / M5 인수 PASS, Git 마감 진행`으로 갱신했다. 제품 acceptance와 실제 Git closeout을 분리해 기록했다.
- **Git·소유권**: 현재 branch에는 아직 M5 commit/push/PR/merge가 없다. 이번 턴은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했고 제품 소스·QA·governance·M5 worklog·Git은 수정하지 않았다.
- **다음 단계**: root가 명시 파일 commit/push, `dev` 대상 PR·독립 리뷰·merge를 수행한 뒤 실제 근거를 PM 기록에 append한다.

## 2026-11-07 16:55 KST — M5 완료·PR #11 dev 통합 동기화

- **근거 대조**: root가 전달한 PR #11 및 실제 Git을 read-only로 재확인했다. `git branch --show-current`=`dev`, `HEAD`=`30a863000a918075e9e1530ec6627e2f9167989a`, `origin/dev`도 동일하며, `HEAD^{tree}`와 `origin/dev^{tree}`는 모두 `ae40db51c5de9f9d262347802962579775ee7c77`다. 구현 commit은 `060bbcf15d54025a8e098627be279143e4e3094a`다.
- **통합 사실**: PR #11(`https://github.com/0-virus/zero-verse/pull/11`)이 2026-11-07 16:53:41 KST `dev`에 merge됐다. M5 QA final acceptance PASS/merge-ready YES와 제품·1440px 시각 gate closure를 실제 Git 통합까지 연결했다.
- **정본 반영**: PRD §9.8·§10 M5·§12 DoD·§13 확정 결정의 현재 상태 문구를 M5 완료·`dev` 통합으로 갱신하고, A-v2 계약 원문·사용자 승인 이력·M8/M6 이후 범위 제외를 보존했다.
- **STATE 반영**: PM `STATE.md`를 `APPROVED / M5 완료·dev 통합`으로 갱신했다. 제품·QA·시각·Git 통합 blocker 없음과 M6 이후 자동 착수 금지를 명시했다.
- **범위·소유권**: 이번 턴은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. 제품 소스·테스트·QA·governance·Git 조작은 수행하지 않았다.
- **다음 단계**: root의 문서 closeout 정합성 대조 후 M5를 마감 상태로 유지한다. M6 이후는 사용자의 명시 지시 전 착수하지 않는다.

## 2026-11-09 13:15 KST — M6 A-v2 사용자 승인·PRD 정본 동기화 및 구현 착수

- **근거 대조**: 사용자 `A-v2 전체 승인`을 M6 회의록 §6 A-v2의 전체 계약 승인으로 확인하고, `docs/worklog/M6-comments-likes.md` Task1~6, backend/frontend/qa STATE, `DECISION-REGISTER.md`의 M6 행, 실제 Git `feature/M6-comments-likes`/`c46a26d`를 재독했다. 독립 Product/Architecture/Delivery 3인의 `APPROVE_WITH_CHANGES/HIGH`와 기존 M5 승인 범위를 M6 승인과 혼동하지 않았다.
- **정본 반영**: PM 소유 `docs/PRD.md`에 회의 §6 원문 링크와 승인된 전체 A-v2 정책을 §9.9로 기록하고, §5.7/§5.8 댓글·좋아요 계약, §7.2 PostDetail UI·M6 시각 범위, §10 M6 실행 순서, §11 M6 검증·M8 알림 분리, §13 확정 결정/§13.2 실행 상태를 동기화했다. 양방향 block-like 금지, ACL 상실 후 unlike 거부·like row 보존, bounded root/reply, 삭제 root GET·placeholder/count, scalar lock·PostService.get 정렬, invalid Bearer·ACL·no-view-increment, BlogPage count 미확장, M6 notification row 미생성을 새로 결정하지 않고 회의 §6 그대로 반영했다.
- **상태 반영**: PM `STATE.md`를 `APPROVED / M6 구현 착수`로 덮어썼다. 사용자 승인 blocker는 해소됐지만 실제 제품 구현·테스트·독립 QA·1440px 인수·Git 통합 전에는 M6 완료/PASS를 주장하지 않으며 backend Task1→2, frontend Task3→4, QA Task5, root Task6 순서를 기록했다.
- **소유권**: 이번 작업은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. REQUIREMENTS·governance·M6 worklog·backend/frontend/qa 산출물·제품 source·Git은 수정하지 않았고, 기존 타 작업자 변경을 되돌리지 않았다.
- **검증**: 변경 후 PRD §5.7/§5.8/§7.2/§9.9/§10/§11/§13.2, STATE 현재 단계·진행 중·다음 작업·차단 요인, WORKLOG append를 재독하고 핵심 문자열을 검색한다. `git diff --check -- docs/PRD.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`와 변경 파일 범위를 확인한다. 제품 테스트·빌드·Git 조작은 수행하지 않는다.
- **미해결**: backend/frontend 실제 구현과 RED→GREEN·MySQL 경쟁·OpenAPI, QA 실제 HTTP/Chrome 1440px 인수, root의 commit/PR/merge는 후속이며 M8 알림 생성은 M6 완료 범위가 아니다.

## 2026-11-09 13:17 KST — M6 초기 구현 산출물 재대조 정정

- **재대조**: 승인 직후 실제 작업 트리를 다시 확인해 `frontend/src/features/comment/`·`frontend/src/features/like/`의 API/types/targeted tests와 `src/test/java/com/zeroverse/domain/comment/CommentControllerTest.java`가 생성된 사실을 확인했다. backend domain 구현, PostDetail 연결, full test/실제 HTTP·MySQL·Chrome 검증은 아직 완료되지 않았다.
- **STATE 정정**: PM `STATE.md`의 “구현 착수”를 실제 초기 산출물과 맞췄고, 남은 BE domain/FE 연결/독립 QA/Git gate를 분리했다. 타 역할 STATE의 승인 대기 문구는 소유자가 갱신할 영역으로 그대로 보존했다.
- **FR 추적 정정**: root가 REQUIREMENTS에 추가한 `FR-LIKE-03` interactions GET을 확인해 PRD §5.8, §9.9, §11의 좋아요 추적 범위를 `FR-LIKE-01~03`으로 맞췄다. REQUIREMENTS·governance·타 역할·Git은 수정하지 않았다.
- **검증**: `rg --files`로 초기 산출물과 `rg -n 'FR-LIKE-01~03|M6 A-v2|13.2 M6' docs/PRD.md`를 확인하고, PM 소유 파일 `git diff --check`를 재실행한다. 제품 테스트·빌드·Git 조작은 수행하지 않는다.
- **미해결**: BE 댓글/좋아요 제품 구현과 FE PostDetail 연동, RED→GREEN·MySQL 경쟁·OpenAPI·1440px 독립 QA, root의 PR/merge가 남아 있다.

## 2026-11-09 14:57 KST — M6 완료 문서 closeout 동기화

- **한 일**: `docs/worklog/M6-comments-likes.md` 마지막 `[머지·완료]`, `qa/M6-review.md` §11, backend/frontend/qa STATE 및 실제 Git을 교차 대조했다. PM 소유 `docs/PRD.md` M6 현황에 완료 증거 1개 문단을 추가하고, `.claude/team/pm/STATE.md`를 M6 완료·`dev` 통합 스냅샷으로 갱신했다. 승인된 A-v2 계약은 변경하지 않았다.
- **산출물**: PR #12 `merged=true`, 구현 `55aedea966a45dc0e88a6b0d8926e709b366050e`, merge `4399f73acf19d66d96fafd2c4784c6a03fcfa282`, 통합 tree `7dd390ace316a1d548e4755dfc84c21f9e7bcdce` 및 local `dev` fast-forward를 PRD/STATE에 기록했다. M6 범위 밖인 BlogPage/PostCard count, M8 notification row, M7 착수는 후속으로 남겼다.
- **검증**: backend 77 suites/490 tests 및 failures/errors/skips 0/0/0, frontend 34 files/369 tests·lint/build PASS, 실제 API·Chrome 1440px 52/52 PASS, 독립 QA M6 PASS/merge-ready Yes 및 WR-01~03 CLOSED/ADDRESSED를 원문에서 확인했다. 구현/merge diff empty와 tree 동일·`dev` fast-forward를 `git diff`/`git rev-parse`/`git merge-base`로 확인했고, PM 소유 변경 후 `git diff --check`도 오류 없이 통과했다.
- **미해결**: M6 blocker 없음. M7 및 후속 count/알림은 별도 사용자 지시·승인·계획 전 착수하지 않는다.

## 2026-11-09 22:08 KST — M7 A-v2 승인·PRD 정본 동기화 및 구현 재개

- **한 일**: 헌법·AGENTS·PM 지침/STATE, `docs/governance/meetings/M7-20261109-feed-search.md` §6 A-v2, `docs/worklog/M7-feed-search.md` 전체, `qa/M7-review.md`, REQUIREMENTS §6.10-A 및 현재 Git/source baseline을 직접 대조했다. 사용자의 `승인된 작업 시작해.` 지시로 A-v2 승인·보류 해제를 확인하고 PM 소유 `docs/PRD.md`만 승인 계약에 맞춰 갱신했다.
- **PRD 산출물**: §5.9/§5.10에 feed/search API·ACL·post-centric 검색·counts/matchedTypes를, §7.1/§7.2와 SearchPage에 기존 세 Universe 목록 `page=0,size=1` `totalElements` 공급원·`/universe/summary` 미생성·피드 전용 following(친구 제외)과 기존 following 현황(친구 포함)을, §9.10에 인증 상태표·paging/sort·RR snapshot·`content_text` V4/V5/audit/`updated_at`/전체 rollback/offline 복구·FE stale 계약을 기록했다. §10 M7 실행 순서·§11 테스트·§12 DoD·§13 item 9/§13.3 현황도 정합화했으며 post-centric 보조 카피와 M8/BlogPage count/S3/admin 제외를 명시했다. PRD 최종 개정일은 2026-11-09로 갱신했다.
- **상태 산출물**: `.claude/team/pm/STATE.md`를 `M7 승인된 정본 동기화·구현 착수 준비`로 갱신했다. REQUIREMENTS·governance·M7 마일스톤 worklog·제품 source·테스트·Git은 수정하지 않았고 사용자 미추적 `package.json`, `package-lock.json`, `run-backend.sh`, `run-frontend.sh`, `.env.local`, `.local-data/`, 기존 DB·업로드 보존을 기록했다.
- **검증**: `rg`/`sed`로 PRD §5.9/§5.10/§7/§9.10/§10/§11/§12/§13.3 및 `/universe/summary`, `/universe/following`, `content_text`, `following` 잔존 참조를 재검색한다. 제품 구현·테스트·runtime·Git 조작은 하지 않았다. 현재 source baseline은 `feature/M7-feed-search` HEAD `bc99cba`이고 M7 acceptance는 NOT RUN이다.
- **미해결**: root REQUIREMENTS §6.10-A 동기화, Task1~4 구현/TDD, V4/V5 격리 migration·복구·RR/query-bound 증거, 실제 HTTP/MySQL/Chrome 1440px, 독립 QA·PR/merge가 남아 있다. 현재 Colima socket 부재는 승인 후 root가 해소할 실행 조건이다. M7 완료/PASS와 M8 착수는 주장하지 않는다.

## 2026-11-09 22:13 KST — M7 현재 구현 산출물 재대조 정정

- **재대조**: 승인 재개 직후 동시 작업자의 변경을 다시 읽어, backend `PostTextExtractor`/`PostTextExtractorV1`·`Post`/`PostContentService` `content_text` 연계 및 관련 테스트 소스, frontend `features/feed`·`features/search` API/types와 `mainFeedBehavior`·`searchBehavior` 테스트 소스가 일부 추가된 사실을 확인했다. MainPage/SearchPage/TopBar 실제 연결, feed/search backend service·repository·controller·DTO, V4/V5 migration·maintenance/복구 및 M7 인수는 아직 남아 있다.
- **정본·STATE 정정**: 위 부분 진행을 `docs/PRD.md` §9.10 실행 상태·§10 M7·§13.3 및 PM `STATE.md`에 반영했다. M7은 **부분 구현 진행 중**이며 해당 소스의 테스트 실행·PASS, 전체 M7 완료/PASS, 독립 QA·실제 HTTP/MySQL/Chrome·PR/merge를 주장하지 않는다. 이전 22:08 기록은 당시 baseline 관측으로 보존한다.
- **소유권·검증**: PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했고 REQUIREMENTS·governance·M7 worklog·backend/frontend/qa 산출물·Git은 건드리거나 되돌리지 않았다. 다음 검증에서 편집 파일 재독·`git diff --check`·핵심 계약/잔존 route 참조를 확인한다.
- **미해결**: root REQUIREMENTS §6.10-A 대조, Task1 V4/V5 및 Task2 서버 구현, Task3/4 페이지 연결·full tests, Task5 독립 인수와 Task6 Git closeout이 남아 있다. `/universe/summary`는 PRD route-map에서 기존 세 목록 `totalElements` 공급원으로 대체한 승인 계약을 유지한다.

## 2026-11-09 23:14 KST — M7 구현 checkpoint·실제 인수 대기 정합화

- **근거 대조**: `docs/worklog/M7-feed-search.md` 최신 구현 checkpoint, `build/m7-evidence/backend-final-1/summary.json`·`backend-final-1.log`·`frontend-final-{tests,lint,build}.log`·`acceptance-1.log`, backend/frontend/qa STATE와 현재 Git을 직접 대조했다. 구현 commit은 Task1 `5d7ede211bb0093c31d8ef09a2c861868e107e1d`, Task2 `059ceed9b941c74cb092e490b6402958abcf1136`, FE `0b3008ad72b571afb47fc64936d0d745b6a4157e`이며 현재 HEAD는 FE commit이다.
- **자동검증·리뷰**: backend `91 suites/538 tests`, failures/errors/skips `0/0/0`, `BUILD SUCCESSFUL`, immutable JAR SHA-256 `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`; frontend `41 files/405 tests`, lint/build exit 0이다. 전체 독립 리뷰는 신규 Critical/Important 없음으로 기록됐지만 최종 QA PASS는 아니다.
- **실제 인수 경계**: acceptance run1은 actor/friend fixture까지 진행한 뒤 oneWay 초기 설정에서 camelCase slug fixture의 400 `BLOG_003`으로 중단됐다. 따라서 실제 HTTP/MySQL/Chrome 1440px acceptance, 5탭·page/back·empty/error/retry 보강 결과, QA PASS/merge-ready를 완료로 기록하지 않았다. QA harness는 result-card 판정과 해당 브라우저 흐름을 보강 중이다.
- **정본·STATE 반영**: 위 사실을 PM 소유 `docs/PRD.md`의 §9.10 실행 상태·§10 M7·§13 item 9/§13.3 및 `.claude/team/pm/STATE.md`에 반영했다. 과거 승인 대기·제품 미착수 문구는 현재 사실로 남기지 않고, A-v2 승인·M8 제외·실제 인수/통합 미완료 경계는 유지했다.
- **소유권·미해결**: 이번 갱신은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. REQUIREMENTS·governance·M7 worklog·제품 source·QA harness·Git은 수정·되돌리지 않았다. 세 구현 commit은 아직 push/PR/`dev` merge되지 않았고, QA run2·독립 최종 review·root Git closeout이 남아 있다. M8은 착수하지 않는다.

## 2026-11-09 23:29 KST — M7 actual acceptance PASS·시각 QA 대기 정합화

- **근거 대조**: `build/m7-acceptance-20261109-232636/evidence.json`과 `docs/worklog/M7-feed-search.md` 최신 checkpoint를 직접 읽었다. run4는 1440×1000에서 `259 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`로 종료했고, `verification.filesChecked=354`, source hash와 actual source hash `974f5174a5c6c181bbd228c5c29215450da0413a7ce9a9f8036daec9d0164e6c`, JAR hash와 actual JAR hash `0cfc53e777449a430cf2f17114113850d9d94d04a4b172a230599e7e41e5d672`가 각각 일치했다. 4개 screenshot artifact도 생성됐다.
- **자동검증·독립 리뷰**: backend `91 suites/538 tests`, failures/errors/skips `0/0/0`, bootJar 성공, frontend `41 files/405 tests`, lint/build exit 0을 유지한다. `/root/m7_whole_branch_review`의 제품·QA fix finding은 모두 CLOSED이며 신규 Critical/Important는 없다. 이전 run1~3 실패/중단 이력은 기존 M7 worklog 항목을 그대로 보존했다.
- **남은 인수 경계**: QA가 run4의 Main/Search 4 screenshots를 디자인 원본과 최종 대조하고 `qa/M7-review.md` 독립 시각 판정을 갱신 중이다. 따라서 actual acceptance PASS는 기록하되 최종 M7 Ready-to-merge, push/PR/`dev` merge는 기록하지 않았다. M8은 착수하지 않는다.
- **정본·STATE 반영**: 위 상태를 PM 소유 `docs/PRD.md` §9.10·§10 M7·§13 item 9/§13.3와 `.claude/team/pm/STATE.md`에 반영했다. A-v2 범위·post-centric 검색·following 의미·content_text/migration/RR/auth 계약과 M8 제외는 유지했다.
- **소유권·검증**: 이번 갱신은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. REQUIREMENTS·governance·M7 worklog·QA/product source·Git은 수정·되돌리지 않았다. 종료 전 PM 파일 재독, `git diff --check`, trailing whitespace 및 `/universe/summary` 잔존 참조를 확인한다.

## 2026-11-09 23:51 KST — M7 최종 QA·dev 통합 closeout 정합화

- **근거 대조**: `docs/worklog/M7-feed-search.md` 최종 `[머지]`, `qa/M7-review.md` 최종 4 screenshot 직접 대조 및 M7 QA PASS/merge-ready Yes, backend/frontend STATE, 실제 local Git `dev`를 재독했다. M7 PR [#13](https://github.com/0-virus/zero-verse/pull/13)은 tested head `2b984b6a2fc3aafd6d650c6ccb1cd8213a528545`에서 merge SHA `08863207b8385177b619091410bbbd43e52a337c`로 `dev`에 통합됐고, merge tree `dc68037477c6fed6b77d537235d79093803a983e`가 tested head tree와 동일하다.
- **최종 증거**: backend `91 suites/538 tests`, failures/errors/skips `0/0/0`, bootJar 성공; frontend `41 files/405 tests`, lint/build exit 0; 실제 HTTP·Chrome 1440px run4 `259 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`; source manifest 354개와 source/JAR SHA 일치; 제품·QA fix finding CLOSED, 독립 시각 QA PASS다. 이전 run1~3 fixture/harness 실패 이력은 M7 worklog에 보존했다.
- **정본·STATE 반영**: PM 소유 `docs/PRD.md` §9.10·§10 M7·§13 item 9/§13.3을 M7 구현·자동검증·실제 acceptance·독립 시각 QA PASS 및 PR #13 `dev` 통합 완료로 갱신했다. `.claude/team/pm/STATE.md`를 `M7 완료·dev 통합`, 차단 없음, M8 미착수 상태로 갱신했다. API18087/FE5177·전용 MySQL/Colima 정리와 사용자 환경·DB·업로드 보존은 root 기록을 근거로 반영했다.
- **범위·검증**: 이번 턴은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. M8은 시작하지 않았고 제품·QA·governance·마일스톤 worklog·Git은 수정하지 않았다. 변경 후 PRD/STATE/WORKLOG를 재독하고 PM 소유 tracked 파일 `git diff --check`, trailing whitespace, `/universe/summary` 의도적 잔존 문구를 검색한다.
- **미해결**: M7 차단 요인은 없다. root의 docs-only 최종 커밋/푸시 후 사용자 다음 지시 전 M8을 착수하지 않는다.

## 2026-11-10 13:12 KST — M8 사용자 정책 9개 PRD 반영·기술안 미확정 분리

- **근거 대조**: `.claude/CONSTITUTION.md`→`AGENTS.md`/`CLAUDE.md`→PM 지침/STATE를 순서대로 읽고, M8 회의 `M8-20261110-notifications.md` §2·§5, `docs/worklog/M8-notifications.md`, backend/frontend/qa STATE, M7 worklog/QA 최종 기록과 실제 Git을 직접 대조했다. 사용자 `모두 권고안으로 진행하자`는 1A~6A·7번 권고·8A·9A의 제품 정책 확정으로만 해석했다.
- **PRD 산출물**: `docs/PRD.md` 최종 개정일을 2026-11-10으로 갱신하고 §9.11을 추가했다. §5.11·§7.2·§10 M8·§13 item 10/§13.4에는 전체 이력(디자인 30일 문구 override), 답글 COMMENT/REPLY 이중수신·동일 수신자 REPLY 우선 dedupe, 실제 재행동 재알림·동일요청 비중복, eligible 발행전이, 원인 취소 이력보존, 권한상실 generic·차단 숨김, 클릭 읽음·전체 범위 read-all·공유 unread·최근 3 all, 주기 자동 갱신 없이 진입/open/복귀 갱신·소급 없음만 반영했다. 읽음·삭제 후 서버 재조회로 자체 상태를 동기화하는 동작은 유지했다.
- **승인 경계**: 회의 §5 A-v1의 API/DTO·동기 발행·잠금/fan-out·인덱스/migration·snapshot·FE store 및 실행계획은 `권고(미확정)`으로 분리했고, 독립 심의·별도 구현 승인 전 확정 정책이나 완료 근거로 쓰지 않았다. M8 제품 코드·migration·테스트·runtime은 미착수다.
- **M7 정정**: 실제 `HEAD=dev=b80ede08c3e54ff3f3d6742b58c401d869574331` 및 `origin/dev`를 확인해 `docs: close out M7 delivery and verification` docs-only closeout이 이미 끝난 사실을 STATE/PRD에 반영했다. M7 완료·PR #13 `dev` 통합은 유지하고 현재 상태를 docs-only closeout 완료로 정정했다.
- **소유권**: 이번 턴은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. REQUIREMENTS·governance·M8 마일스톤 worklog·backend/frontend/qa 산출물·제품 소스·Git은 수정하지 않았고, 다른 untracked/modified 변경은 보존했다.
- **검증**: 변경 후 PRD·PM STATE·PM WORKLOG를 재독했고 §9.11의 정책 항목을 `policy_items=9`로 세었으며 §5.11/§7.2/§10/§13.4, `권고(미확정)`, `b80ede0`를 검색했다. `git diff --check -- docs/PRD.md .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`는 오류 없이 통과했고, tracked 변경 범위는 PM `STATE.md`·`WORKLOG.md`로 확인했다(`docs/PRD.md`는 `.gitignore`의 `docs/*` 적용). M7 evidence 직접 재집계는 backend `91 suites/538 tests`, failures/errors/skips `0/0/0`; frontend `41 files/405 tests` passed; acceptance `259 PASS/0 FAIL/0 NOT_RUN`, `failures=[]`, viewport `1440×1000`, files `354`, source/JAR hash 각 actual 일치였다. 제품 테스트·빌드·runtime·Git 조작은 수행하지 않았다.
- **미해결**: M8 기술 계약·실행계획의 독립 심의와 별도 구현 승인, 이후 정본 동기화·backend/frontend 구현·독립 QA·실제 인수·Git 통합이 남아 있다. M9로 자동 확장하지 않는다.
- **추가 정합화(13:20 KST)**: root 재독에서 §5.11의 갱신 문구가 자체 읽음/삭제 후 서버 재조회까지 막는 것으로 읽히지 않도록 `주기적인 자동 갱신 없이 화면 진입·드롭다운 열기·브라우저 복귀에서 갱신`으로 좁히고, 사용자 동작 뒤 자체 서버 재조회 동기화는 유지한다고 §5.11·§7.2·§9.11·§10 및 PM STATE에 명시했다. A-v1 기술안은 계속 미확정이다.

## 2026-11-10 13:29 KST — M8 A-v2 사용자 승인 대기 상태 동기화

- **근거**: root가 갱신한 M8 회의 §9 A-v2와 `docs/worklog/M8-notifications.md` Task1~6 및 독립 Product/Architecture/Delivery 결과(`APPROVE_WITH_CHANGES/HIGH`)를 확인했다. 9개 제품 정책은 기존 승인 그대로 유지한다.
- **정본·STATE 반영**: PRD §5.11·§9.11·§10 M8·§13.4 및 PM STATE의 현행 상태를 `USER_DECISION_REQUIRED`·§9 A-v2 구체 계약/실행 승인 대기로 동기화했다. §5 A-v1은 심의 입력 이력으로만 남겼다.
- **승인 대상 요약**: 공개 API/DTO, 원행위·알림 원자성, 정렬 잠금·후보 재검증·총 3회 attempt, V6 인덱스, M8 구현·독립 QA·`dev` 통합이다. 이는 승인 대기 요약이며 확정 규범·구현 완료로 기록하지 않았다.
- **범위**: PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. 제품 코드·테스트·DB/runtime·Git 및 타 역할 변경은 건드리지 않았다.
- **미해결**: 사용자 명시 기술·실행 승인 전 M8 구현은 미착수다.

## 2026-11-10 13:42 KST — M8 A-v2 전체 승인·Task1~6 실행 상태 동기화

- **근거**: 사용자의 원문 `A-v2 전체 승인`과 M8 회의 §10을 확인했다. §2의 제품 정책 9개 승인과 함께 §9 A-v2 구체 계약, M8 worklog Task1~6의 구현·독립 QA·실제 인수·`dev` 통합 실행이 승인됐고, root가 `b80ede0`에서 `feature/M8-notifications`를 생성했다.
- **PRD·STATE 산출물**: `docs/PRD.md` §5.11·§7.2·§9.11·§10 M8·§13 item 10/§13.4와 PM `STATE.md`를 현재 `APPROVED`·실행 중 상태로 동기화했다. A-v2의 개별 read 현재 권한 재평가 canonical `NotificationItem`, `SUSPENDED` GET read-only, dynamic unavailable masking·권한 복구 재평가, same-transaction rollback, sorted locks·current candidate recheck·총 3회 attempt, V6 인덱스 2개를 구현 정본으로 기록했다. §5 A-v1은 심의 입력 이력으로만 보존했다.
- **완료 경계**: 구현·자동검증·독립 QA·실제 인수·`dev` 통합은 별도 완료 gate로 남겼으며 아직 완료·PASS로 주장하지 않았다. M7 `b80ede0` docs-only 최종 closeout 완료와 M7 통합 완료는 유지했다.
- **범위**: PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. 제품·테스트·QA·REQUIREMENTS·governance·M8 마일스톤 worklog·runtime·Git은 수정하지 않았고 다른 역할 변경을 되돌리지 않았다.
- **검증**: 변경 문구를 PRD §5.11/§7.2/§9.11/§10/§13.4와 PM STATE/WORKLOG에서 재독하고, 핵심 승인·실행·완료 gate 문구 및 PM 파일 범위를 `rg`로 점검한 뒤 `git diff --check`를 실행한다.

## 2026-11-10 13:45 KST — M8 PM 문구 재독·diffcheck 완료

- **검증 결과**: PRD §5.11·§7.2·§9.11·§10 M8·§13.3/§13.4와 PM `STATE.md`/`WORKLOG.md`를 재독했다. `A-v2 전체 승인`, `APPROVED`, canonical `NotificationItem`, `SUSPENDED` GET read-only, dynamic unavailable masking/복구, same-transaction, sorted locks/current candidate recheck/총 3회 attempt, V6 인덱스 2개 및 별도 구현·검증 gate가 일관되게 남아 있음을 `rg`로 확인했다. M7 `b80ede0` docs-only closeout 완료도 유지된다.
- **검증 명령**: PM 3개 파일 대상 `git diff --check`는 오류 없이 통과했고, trailing whitespace 검색 결과는 0건이다. 제품 코드·테스트·runtime·Git 조작은 하지 않았다.

## 2026-11-10 18:00 KST — M8 Task1~4 중간 closeout·실제 인수 run2 정합화

- **근거 대조**: 승인된 회의 §9~10·ADR-0007·PRD §9.11/§10/§13.4와 backend/frontend/qa STATE·WORKLOG, `docs/worklog/M8-notifications.md`, 실제 Git feature branch를 재독했다. M8 제품 정책 9개와 A-v2 승인 상태는 유지하며 M9는 범위에서 제외했다.
- **제품/자동검증 사실**: backend 최신 전체는 `102 suites / 599 tests / failures 0 / errors 0 / skipped 0`, `bootJar BUILD SUCCESSFUL`; frontend 최신 전체는 `47 files / 439 tests PASS`, lint/build exit 0이다. 최종 리뷰의 DELETE `400 VALIDATION_001` OpenAPI 문서와 SUSPENDED preview 읽기 전용 안내는 backend controller 5 tests/bootJar 및 frontend shell 8 tests/lint/build를 추가 통과했고 커밋 `12d7ab5`에 보존됐다.
- **독립 리뷰·인수 사실**: 독립 전체 리뷰는 신규 제품 Critical/Important 차단 결함 없이 `With fixes`를 판정했다. QA 도구 수정은 manifest 정렬, fixture/삭제 순서, query bound, 실제 DOM locator, SPA stale, 정상 서명 wrong-type JWT, null 보존 fingerprint까지 반영했다. 실제 run2 `build/m8-acceptance-20261110-172852/evidence.json`은 `310 PASS / 4 FAIL` 뒤 로그인 상태 `/signin` 접근이 GuestOnlyRoute에 막힌 검사 절차 timeout으로 종료됐다. HTTP·DB·인증·fingerprint와 SELECT bound(size1=4,size100=4,차이=0)은 통과했으나 브라우저 선택자·외곽 폭·0 unread badge·same-Provider 계정 전환 검사는 재실행 전 보완 대상이다.
- **정본/STATE 반영**: PRD M8 상태와 §13.4를 Task1~4 구현·자동검증 완료, Task5 인수 보완·재실행 및 Task6 통합 대기로 갱신했다. PM STATE를 같은 근거로 덮어썼고 최종 PASS·merge-ready·`dev` 통합 완료는 기록하지 않았다.
- **소유권·미해결**: 이번 작업은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. 제품 코드·QA harness·governance·M8 milestone worklog·Git은 수정하지 않았다. QA 도구 수정 및 최종 acceptance evidence, 독립 scoped re-review, root PR/merge가 남아 있다.
- **검증**: 편집한 PRD·STATE·WORKLOG를 재독하고 핵심 상태 문자열·수치를 검색한다. PM 소유 파일에 `git diff --check`와 trailing whitespace 검사를 실행한다.

## 2026-11-10 17:37 KST — 중간 closeout 시각 정정

- 앞선 `2026-11-10 18:00 KST` closeout 항목의 표기 시각은 root가 확인한 실제 실행 시각보다 늦어 정정한다. 해당 항목의 상태·수치·미해결 gate 내용은 유지하고, 현재 PM STATE의 마지막 갱신 시각을 `17:37 KST`로 맞췄다.
- run3 실제 인수·최종 독립 판정·PR/`dev` 통합 대기 상태와 M9 제외 범위에는 변경이 없다.
- 검증: PM STATE/WORKLOG를 재독하고 PM 소유 파일 `git diff --check`를 다시 실행한다. 제품 코드·QA harness·runtime·Git은 수정하지 않는다.

## 2026-11-10 17:40:26 KST — M8 실제 인수 run3 PASS 정합화

- **근거 대조**: root가 제공한 `build/m8-acceptance-20261110-173900/evidence.json`과 실제 manifest를 대조했다. run3는 `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`로 종료됐고 1440×1000 screenshot 5개를 생성했다.
- **artifact·성능 근거**: source hash `108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47`, immutable JAR hash `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5`가 tested manifest와 일치한다. SQL bound는 size1 SELECT 4, size100 SELECT 4, 차이 0이다.
- **정본/STATE 반영**: PRD M8 status/§13.4와 PM STATE를 Task1~5 구현·자동검증·실제 인수 PASS 및 최종 독립 리뷰·Task6 `dev` 통합 대기 상태로 갱신했다. 최종 merge-ready·`dev` 통합 완료는 주장하지 않는다.
- **소유권·미해결**: 이번 갱신은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. 남은 gate는 독립 최종 scoped re-review와 root의 명시 파일 PR/`dev` merge이며 M9는 범위에서 제외한다.
- **검증**: 편집한 PRD·STATE·WORKLOG를 재독하고 핵심 PASS/hash/대기 문자열을 검색한다. PM 소유 파일 `git diff --check`를 실행한다.

## 2026-11-10 17:47:12 KST — M8 최종 PASS·PR #14 `dev` 통합 정합화

- **근거 대조**: root가 전달한 PR #14, `qa/M8-review.md` §10, run3 evidence 및 실제 Git 통합 결과를 대조했다. 독립 QA는 5개 실제 PNG를 확인하고 `Ready to merge: Yes`로 판정했다.
- **실제 인수·artifact 사실**: run3 `build/m8-acceptance-20261110-173900/evidence.json`은 `323 PASS / 0 FAIL / 0 NOT_RUN`, `failures=[]`다. source hash `108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47`, immutable JAR hash `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5`가 일치하고 SQL bound는 size1=4,size100=4,차이0이다.
- **Git 통합 사실**: PR #14(`https://github.com/0-virus/zero-verse/pull/14`)가 merge SHA `b9fcb0469f42077447ce8a8842ce22f24bc94af5`로 `dev`에 merge됐다. tested head `201cd181ec8332e85f2cc985c9560d947b2833f4`와 통합 tree `9da6077a89a4528df3cb55c13556ed28300cfd28`가 일치하고 local `dev` fast-forward도 확인됐다.
- **정본/STATE 반영**: PRD M8 상태·§13.4와 PM STATE를 Task1~6 완료·최종 PASS/merge-ready·`dev` 통합 완료로 갱신했다. M9는 별도 사용자 지시 전 대기한다.
- **소유권·후속**: 이번 갱신은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수정했다. root가 post-merge docs-only closeout을 기록할 예정이다. 제품 코드·QA·governance·runtime·기타 역할 파일은 수정하지 않았다.
- **검증**: 편집한 PRD·STATE·WORKLOG를 재독하고 최종 PASS/PR #14/tree/hash 문자열을 검색한 뒤 PM 소유 파일 `git diff --check`를 실행한다.

## 2026-11-10 17:51:01 KST — M8 §10 현재 완료 경계 문구 정정

- 최종 브리프에서 현재 M8 §10의 `구현·자동검증·독립 QA·실제 인수·dev 통합은 별도 완료 gate이며 아직 완료를 주장하지 않는다` 문구가 최종 상태와 충돌함을 확인했다. 해당 문장을 QA §10 `Ready to merge: Yes`, run3 `323 PASS / 0 FAIL / 0 NOT_RUN`, PR #14 `dev` 통합 완료 근거로 교체했다.
- 역사적 승인·계약 설명의 “별도 완료 gate” 문구는 당시 심의/계약 의미를 보존하기 위해 유지했고, 현재 M8 상태 문구와 충돌하지 않는지 재검색했다. PM STATE의 M8 gate 문구도 모두 통과한 상태로 정정했다.
- 검증: PRD·STATE·WORKLOG 재독 및 `git diff --check`를 실행한다. 제품 코드·QA·runtime·Git은 수정하지 않는다.

## 2026-11-11 12:58:40 KST — M9 착수·PM 상태 정합화

- **한 일**: 헌법 → root `AGENTS.md`/`CLAUDE.md` → PM 지침/STATE 순서로 읽고, `docs/worklog/M9-admin.md`, M9 회의 상태, REQUIREMENTS §6.13, PRD 관리자 화면·M9 마일스톤, 실제 Git을 직접 대조했다. 사용자 `m9 완료를 목표로 착수해줘`에 따른 현재 상태를 M9 `REVIEWING`으로 정리했다.
- **M8 정정**: 이전 PM STATE의 `b80ede0` docs-only closeout 대기와 현재 `HEAD` 주장은 낡은 상태였다. 실제 Git은 `feature/M9-admin`, `HEAD=dev=origin/dev=848d272`이며 `2cc4ff9` M8 closeout과 `56ea133` M8 상태 정합화가 존재하고, `848d272`가 그 후속 커밋임을 확인했다. M8 `docs/worklog/M8-notifications.md`의 `[머지·완료]`도 재확인했다.
- **M9 정합화**: `docs/worklog/M9-admin.md`와 회의의 상태가 계획·심의 REVIEWING, 구체 HIGH 계약 사용자 승인 대기, 제품 구현 미착수임을 확인했다. `git diff --name-only -- src frontend`는 출력이 없었고 기존 Admin scaffold/router와 ADMIN 오류코드만 선행 산출물로 보존한다. 독립 council의 결론은 사용하지 않고 회의의 REVIEWING 상태·정본·Git 사실만 반영했다.
- **PM 산출물**: `.claude/team/pm/STATE.md`를 현재 M9 상태로 덮어썼다. PRD는 수정하지 않았다. 승인 후 동기화 후보를 PRD §5.13·§7·§9·§10·§11·§13으로 표시하고, soft delete/공개 효과·deleted 노출·SUSPENDED ADMIN·self 보호/오류·idempotency·lastAccessAt·summary 시간대·refresh 폐기를 `권고(미확정)`/차단 요인으로 남겼다.
- **검증**: `git branch --show-current`, `git status --short`, `git log --oneline -10`, `git rev-parse HEAD/dev/origin/dev`, `git show -s 2cc4ff9 56ea133 848d272`, `git diff --name-only -- src frontend`, PRD/REQUIREMENTS/M9 worklog·회의 검색을 실행했다. PM STATE/WORKLOG 재독과 `git diff --check -- .claude/team/pm/STATE.md .claude/team/pm/WORKLOG.md`도 오류 없이 통과했다. 제품 테스트·build·runtime·DB·Git 조작은 하지 않았다.
- **미해결**: M9 A-v2·실행계획의 사용자 승인, 그 후 REQUIREMENTS/governance 정본 확정과 PM PRD 동기화, backend/frontend 구현, 독립 QA·실제 인수·`dev` 통합이 남아 있다. M10·배포·사용자 DB 적용은 범위 밖이다.

## 2026-11-11 13:03:54 KST — M9 구체 계약·실행 승인 대기 정합화

- **한 일**: root가 갱신한 M9 회의 §10/§10.1 A-v2와 Task1~5 사용자 비동기 승인 요청 제출 상태를 확인하고 PM STATE를 `REVIEWING`에서 `USER_DECISION_REQUIRED`로 동기화했다. A-v2는 아직 사용자 승인 전 제안으로 유지했다.
- **심의 상태**: 독립 Product/Architecture/Delivery 결과는 각각 `APPROVE_WITH_CHANGES/HIGH`(확신도 89/93/88)이다. 이 결과와 A-v2의 구체 계약은 승인 대기 근거로만 기록하며 제품 결정으로 확정하지 않는다.
- **Git 정합화**: 현재 branch는 `feature/M9-admin`, `HEAD=e5c38cd`, `dev=origin/dev=848d272`다. `e5c38cd`는 외부 사용자 gitignore 변경을 보존한 커밋이며 제품 변경은 없다. M9 구현·테스트·runtime은 미착수다.
- **PM 산출물**: `.claude/team/pm/STATE.md`만 현재 승인 대기·HEAD 상태로 갱신하고 이 항목을 append했다. PRD·제품·QA·governance는 수정하지 않았다. 승인 전 PRD 동기화 후보 §5.13/§7/§9/§10/§11/§13과 미확정 충돌은 그대로 유지했다.
- **검증**: `git branch --show-current`, `git rev-parse HEAD`, `git rev-parse dev`, `git log -3 --oneline`, M9 회의 §10/§10.1·`USER_DECISION_REQUIRED` 검색을 실행했다. 추가 테스트·build·runtime·DB 조사는 하지 않았다.
- **미해결**: 사용자 M9 A-v2·Task1~5 비동기 승인 응답 대기. 무응답을 승인으로 간주하지 않으며, 승인 후에만 PM PRD 동기화와 구현 gate를 진행한다.

## 2026-11-11 13:19:47 KST — M9 A-v2 승인·Task1 PRD 정본 동기화

- **근거**: 사용자 `A-v2 전체 승인`을 M9 회의 §11의 승인으로 확인하고, REQUIREMENTS §6.13-A, ADR-0008, `docs/worklog/M9-admin.md`의 승인 Task1 및 기존 PRD 관리자 화면/마일스톤을 대조했다. 회의 상태는 `APPROVED`이며 승인 자체를 구현·검증 PASS로 간주하지 않는다.
- **PRD 동기화**: PM 소유 `docs/PRD.md` §5.13에 정확한 5개 API/DTO·DB current ACTIVE ADMIN/no-store·deleted 포함 ID 정렬 lock·RR/batch/query bound·user-only soft delete·기존 상태/ACL/refresh 경계를 반영했다. §7 관리자 목록/상세에 current probe·stale 격리·lastAccess 기록 없음·승인 삭제 카피와 디자인 override를 반영했고, §9.12에 승인 결정 로그, §10 M9 실행 순서, §11 M9 검증 매트릭스, §13 확정 항목/§13.5 현행 상태를 추가했다.
- **반영한 승인 경계**: 동일값 성공·audit 무변경, self role/status/delete `ADMIN_002/400`, missing/deleted/repeated DELETE `USER_001/404`, KST 주간 summary와 명시 RR/SELECT≤14·size1→100 증가0, 정지 기존 상태표·PUBLIC·알림 GET read-only, refresh 전역 revoke 비확대, bootstrap/실사용 DB 승격·새 library/migration·M10·배포 제외를 확정 정본에 기록했다. 과거 M9 미승인 문구는 회의 이력으로 덮어쓰지 않았다.
- **STATE 동기화**: PM `STATE.md`를 `APPROVED`·Task1 완료·Task2~5 실행/완료 gate 대기로 갱신했다. 현재 branch `feature/M9-admin`, `HEAD=e5c38cd`, `dev=origin/dev=848d272`를 유지한다.
- **소유권·검증**: 이번 턴은 PM 소유 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 편집했다. 제품 코드·타 역할·governance·Git은 수정하지 않았고, 테스트/build/runtime/DB는 실행하지 않았다. 변경 후 PRD/STATE/WORKLOG 재독 및 `git diff --check`를 수행했고 오류 없이 통과했다.
- **미해결**: Task2 backend·Task3 frontend 구현/자동검증, Task4 독립 QA·실제 API/Chrome 1440px 인수, Task5 root PR/`dev` merge/tree 대조/runtime 정리가 남아 있다. M9 최종 PASS는 해당 gate 후에만 기록한다.

## 2026-11-11 15:35:06 KST — M9 UTC 실행 전제·audit 정밀도 후속 정본 동기화

- **근거 대조**: M9 회의 §12, ADR-0008 실행 전제 보완, `README.md` 서버 시각 기준, RISK-0021을 직접 읽고 `docs/worklog/M9-admin.md` 최신 기록과 backend/frontend/qa STATE·실제 Git을 대조했다. 현재 branch는 `feature/M9-admin`, `HEAD=01d8582`, `dev=origin/dev=848d272`다. `9a4d03f` backend source와 `3d34468` frontend fix, `01d8582` QA harness/closure의 소유·범위를 확인했다.
- **Backend 실행 사실**: `9a4d03f`의 `107 suites/632 tests`, failures/errors/skips `0/0/0`, `bootJar` 성공은 UTC 보완 전 baseline이다. `recovery-signup-audit-kst-1`에서 Asia/Seoul JVM의 실제 가입 audit/관리자 UTC 표현/즉시 `joinedThisWeek`가 1 test·3 behavioral assertions RED였고, `recovery-runtime-utc-green-1`에서도 `DATETIME(0)` 반올림으로 1건 RED였다. 현재 작업 트리의 UTC `bootRun`/Gradle test 설정, 직접 JAR의 비UTC DB 연결 전 차단, `SecondPrecisionAuditTimeProvider` 초 truncate 보완 후보와 `recovery-runtime-utc-green-2` `15 suites/79 tests`, failures/errors/skips `0/0/0`, `bootJar` 성공 증거를 확인했다. 전체 재검증 전이므로 Task2 최종 PASS로 기록하지 않았다.
- **Frontend/QA 사실**: M9 worklog의 root 보고는 `3d34468` 이후 full `51 files/464 tests PASS`, lint/build exit 0이다. 다만 현재 보관 raw `frontend/.m9-evidence/frontend-final-test-2.log`는 F5 이전 `51 files/456 tests PASS`이고 F5 focused는 `1 file/4 tests PASS`여서, PM은 464를 최종 M9 acceptance 수치로 확대하지 않고 provenance 확인 대상으로 남겼다. QA `01d8582`는 I1~I9 정적 closure/harness 준비이며 실제 API·MySQL·Chrome 1440px 인수는 `NOT RUN`이다.
- **PRD·STATE 반영**: `docs/PRD.md` 최종 개정일을 2026-11-11로 맞추고 §5.13·§10 M9·§11·§13.5에 UTC JVM 지원(`bootRun`/Gradle test UTC, JAR `-Duser.timezone=UTC`, 비UTC Spring/DB 연결 전 차단), `DATETIME(0)` audit 초 단위 truncate, 역사 `DATETIME` provenance 자동 보정 금지·실사용 DB 적용 제외와 현재 검증 경계를 추가했다. 이를 승인 계약의 구현 보완으로 명시하고 §9에 새 사용자 결정을 추가하지 않았다. PM `STATE.md`는 `01d8582`, baseline/보완 증거, FE 수치 provenance, QA NOT RUN, 남은 Task2/4/5 gate로 갱신했다.
- **소유권·미해결**: 이번 편집은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수행했다. backend/frontend/qa/governance/worklog 원본·제품 코드·Git·DB는 수정하거나 실행하지 않았다. 남은 것은 UTC 보완 전체 재검증, immutable source/JAR·FE provenance, QA 격리 runtime/실제 인수, root의 PR·`dev` merge/tree 대조이며 M9 최종 PASS는 아직 없다.
- **검증**: PRD·PM STATE·PM WORKLOG를 다시 읽고 `rg`로 `UTC`, `DATETIME(0)`, `RISK-0021`, `recovery-runtime-utc-green-2`, `NOT RUN`, `01d8582`, `9a4d03f`, `3d34468` 참조를 확인한다. PM 소유 파일에 `git diff --check`와 trailing-whitespace 검사를 실행한다. `.superpowers/sdd/M9-admin/pm-sync-report.md`는 PM 소유 경로가 아니므로 이 작업에서 생성·수정하지 않는다.

## 2026-11-11 15:43:29 KST — M9 최종 자동검증 provenance·QA audit-2 정정

- **정정 근거**: root가 제공한 `build/m9-evidence/frontend-final-test.log`를 직접 읽어 `3d34468` 이후 root 최종 full이 exit 0, `51 files/464 tests PASS`임을 확인했다. `build/m9-evidence/frontend-final-result.json`도 `success=true`, `testResults=51`, `numTotalTests=464`, `numPassedTests=464`, `numFailedTests=0`, `numPendingTests=0`이다. 기존 `frontend/.m9-evidence/frontend-final-test-2.log`의 `51/456`은 F5 이전 별도 실행 이력으로 최종 464와 모순이 아니므로 PM STATE/PRD의 FE provenance blocker를 제거했다.
- **Backend 정정 근거**: 현재 `HEAD=6a8763d`(`fix(m9): enforce UTC runtime and align audit precision`)와 `build/m9-evidence/backend-final-2/summary.json`을 확인했다. 최종 backend 자동검증은 `109 suites/637 tests`, failures/errors/skips `0/0/0`, exit 0, `bootJar` 성공, JAR SHA-256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`다. `9a4d03f`의 `107/632`는 UTC 보완 전 baseline으로 역사 보존한다.
- **QA gate 정정**: `.superpowers/sdd/M9-admin/acceptance-audit-2.md`를 직접 읽었다. audit-2는 이전 I1~I9 `STATIC CLOSED` 선언을 승인하지 않고 `CHANGES REQUIRED`(Critical 0 / Important 9 / Minor 1), 실제 acceptance `NOT RUN`으로 판정했다. 따라서 QA 상태는 fix round2·scoped 재검토 대기이며, 실제 API/MySQL/Chrome 1440px 인수·독립 최종 PASS·merge는 아직 없다.
- **PRD·STATE 반영**: PM `docs/PRD.md`와 `.claude/team/pm/STATE.md`에서 FE 464/구조화 evidence와 backend 637 final PASS를 확정 자동검증 증거로 반영하고, F7 reporter 대기를 제거했다. QA는 `CHANGES REQUIRED`·fix round2·actual `NOT RUN`으로 정정했다. 기존 DATETIME provenance/실사용 DB 제외와 최종 PASS 경계는 유지했다.
- **소유권·미해결**: 이번 편집은 `docs/PRD.md`, `.claude/team/pm/STATE.md`, `.claude/team/pm/WORKLOG.md`만 수행했다. QA source/harness·제품 코드·governance/worklog 원본·Git·DB는 수정하지 않았다. 남은 것은 QA fix round2/scoped 재검토, 격리 runtime 실제 인수, Task5 PR·`dev` merge/tree 대조다.
- **검증**: FE JSON summary를 Node로 재집계(`testResults=51`, `464/464`, failed/pending `0/0`), backend summary JSON 재독, audit-2 재독, PM 파일 재독·참조 검색·`git diff --check`·trailing-whitespace 검사를 수행한다. `.superpowers/sdd/M9-admin/pm-sync-report.md`는 PM 소유 경로 밖이라 계속 생성·수정하지 않는다.

## 2026-11-11 17:53:47 KST — M9 후보 실제 인수 PASS·최종 review/`dev` 통합 대기 정합화

- **직접 근거 대조**: 현재 Git은 `feature/M9-admin`, `HEAD=84e03a4`, `dev=origin/dev=848d272`다. `build/m9-acceptance-20261111-174413/evidence.json`을 직접 재집계해 `milestone=M9`, `contract=A-v2`, `status=PASS`, `checks=396`, `failures=0`, `requiredNotRun=[]`, `requiredFailed=[]`, 1440×1000, screenshot 2개를 확인했다. `source filesChecked=424`, source/actual source hash 일치, JAR/actual JAR hash 일치(`c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`)도 확인했다. root가 함께 전달한 `acceptance-diagnostic-7.log` 종료 0과 source 117건 before/after·current hash match, SQL `list size=1:5`, `list size=100:5`, `detail:3`, `list delta=0` 기록을 후보 인수 근거로 반영했다.
- **자동검증 provenance**: FE `build/m9-evidence/frontend-final-result.json`은 `success=true`, 51 files, 464 passed/0 failed/0 pending이고, BE `build/m9-evidence/backend-final-2/summary.json`은 `109 suites/637 tests`, failures/errors/skips `0/0/0`, exit 0, bootJar 및 JAR SHA `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`다. FE 464·BE 637 source는 후보 실행에서 변경되지 않았다. FE 이전 `51/456`, audit-2의 Important 9/Minor 1 및 이전 diagnostic 실패는 별도 과거 실행·판정 이력으로 보존한다.
- **현행 경계**: QA static loop/runtime fixture 보완은 `3a2752e`까지 완료됐다. 후보 실제 인수는 PASS로 정본에 반영하지만 fresh `m9_final_review` 전체·시각 최종 판정은 아직 진행 중이며, Task5 PR/`dev` 통합/tree 대조/runtime 정리도 대기다. 따라서 M9 최종 PASS·merge·실사용 DB 적용·M10·배포는 주장하지 않는다.
- **PM 산출물·소유권**: `docs/PRD.md` §10 M9·§13.5와 `.claude/team/pm/STATE.md`를 위 상태로 갱신하고 이 항목을 append했다. 제품 source/QA source/governance/마일스톤 worklog/Git/DB와 `.superpowers/sdd/M9-admin/pm-sync-report.md`는 수정하지 않았다.
- **검증**: PRD·PM STATE·WORKLOG 재독, 후보 JSON/BE summary 직접 집계, 현재 branch/HEAD/dev 확인, PM 소유 파일 `git diff --check`·trailing whitespace·핵심 상태 참조 검색을 수행한다. 이번 PM 턴에는 테스트·build·runtime·DB를 실행하지 않았다.

## 2026-11-12 10:22:04 KST — M9 최종 독립 review PASS·READY TO MERGE 정합화

- **직접 근거 대조**: 현재 Git은 `feature/M9-admin`, `HEAD=5df2db0`(`fix(m9): preserve literal admin search whitespace`), `dev=origin/dev=848d272`다. `5df2db0`의 제품 변경은 `adminApi.ts`와 `adminApi.test.ts`의 M1 수정이며 backend source/JAR는 변경되지 않았다. `final-review.md`의 Minor M1과 `final-m1-rereview.md`의 scoped 재검토를 대조해 Critical/Important/Minor `0/0/0`, `CLEAN / READY TO MERGE`를 확인했다.
- **자동검증·무결성**: M1 수정 후 root fresh FE full은 `51 files/465 tests PASS`, lint/build exit 0이다. 기존 `frontend-final-result.json`의 `51/464`와 run7 FE 464는 수정 전 baseline으로 구분했다. `build/m9-evidence/tested-version.json`을 직접 재집계해 `424 files`, source SHA-256 `ababaa1c00610e6a12b8ef1ec7cd876f1a3ab81fba57ba537c630e6484530ef3`, 개별 hash mismatch `0`을 확인했고, backend `109 suites/637 tests`, failures/errors/skips `0/0/0`, JAR SHA-256 `c6f381f72bf94528f54e3bb805403d23a0e119673c6183c9aff66af3ac0d9c52`는 불변이다.
- **인수 경계**: run7 `build/m9-acceptance-20261111-174413/evidence.json`의 `396/0/0`, 1440×1000·screenshot 2개와 당시 source hash `63d720de69a286736f77406cf3a33019ef4871b09cd6632ec7f483ce15b9602c`는 M1 수정 전 실제 인수의 역사 증거로 보존한다. 현재 FE465는 focused/full FE 검증과 scoped 독립 review로 닫았으며, run7을 수정 후 source의 acceptance로 재사용하지 않는다.
- **PM 정본 변경**: `docs/PRD.md` §10 M9·§13.5에서 상태를 `Task1~4 및 최종 독립 review PASS / READY TO MERGE / Task5 PR·dev 통합 대기`로 갱신하고 FE465·새 source hash·기존 JAR 불변·run7 역사 경계를 반영했다. `.claude/team/pm/STATE.md`도 같은 상태로 갱신했으며 통합 완료 또는 merge 완료를 쓰지 않았다. 새 사용자 결정은 만들지 않았다.
- **검증**: backend/frontend/qa STATE와 role worklog, PRD §5.13·§9.12·§10 M9·§11·§13.5, REQUIREMENTS §6.13-A, ADR-0008, M9 worklog 및 final review 보고서를 재독했다. manifest 424건/개별 hash·JAR SHA를 직접 확인하고 PM 편집 파일 재독·핵심 참조 검색·`git diff --check`를 수행한다. QA/제품/Git/PR/runtime 파일은 수정하지 않았다.
- **미해결**: Task5의 명시 파일 PR, `dev` merge/tree 대조, M9 runtime 정리만 남아 있다. 사용자 DB·기존 업로드·M10·배포는 계속 범위 밖이다.
