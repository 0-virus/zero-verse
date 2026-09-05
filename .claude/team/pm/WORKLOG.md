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
