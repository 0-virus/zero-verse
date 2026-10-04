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
