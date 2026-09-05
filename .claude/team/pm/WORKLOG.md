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
