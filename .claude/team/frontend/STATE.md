# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-03 (M4 최종 독립 리뷰·Git 통합 대기 인계)

## 현재 단계

- 사용자 승인 로컬 파일 저장 범위의 M4 Task4~6 제품 FE 구현이 checkout에 반영되어 있다. 현재 checkout/HEAD와 다른 역할의 변경은 보존하며 Git stage/commit/push는 리더 소유다.
- 소유 경계는 `frontend/**` 및 `.claude/team/frontend/**`다. backend/QA/root 정본·runtime·브라우저 최종 판정은 수정하지 않는다.
- 독립 whole reviewer `m4_whole_review`는 최종 `Ready to merge: Yes`로 판정했고 R1~R8 및 R7 후속 잔여는 0이다. root의 API·Chrome R1/R4/R5·orderedList 표식 검증과 최종 PNG artifact도 보존되어 있다(`docs/worklog/M4-posts.md` 18:15 기록 참조).
- frontend 범위의 리뷰·runtime 인계는 해소되었고, 현재 남은 상태는 root 소유 Git 통합/merge 대기다. 이 역할은 Git stage/commit/push를 수행하지 않았으며 통합 완료를 주장하지 않는다.

## 이번 wave 구현

- `PostEditor` Markdown 일반 paste와 toolbar paste를 `insertContent(..., { contentType: 'markdown' })` 경로로 바꿔 현재 선택 영역의 앞뒤 본문을 보존하고, bold/link Markdown도 감지한다.
- `DraftPicker`와 `SettingsPostsPage`가 표준 `page=0,size=20`에서 시작해 `hasNext/hasPrevious` 페이지 이동을 제공한다. EditPage는 picker 선택 시 `/edit/{id}`로 이동해 새 글의 full snapshot을 다시 조회한다.
- DEFAULT 카테고리가 실제 목록에 있으면 GENERAL이 먼저 와도 실제 DEFAULT ID를 초기 선택·payload에 사용하고 빈 fallback option은 숨긴다. 기존 DEFAULT `미분류` label은 중복되지 않는다.
- `Prose`는 비배열/잘못된 node content와 marks를 안전하게 무시하고, orderedList `start/type`, table header/cell `colspan/rowspan`을 허용된 값으로 렌더링한다.
- orderedList `type`의 HTML 의미가 Tailwind `list-decimal`에 덮이지 않도록 `1/a/A/i/I`를 `decimal/lower-alpha/upper-alpha/lower-roman/upper-roman` inline style로 매핑한다.
- 구현 하위 경로 지침을 동기화해 `frontend/src/features/post/AGENTS.md`와 `frontend/src/features/upload/AGENTS.md`를 추가했다. 제품 소스·테스트 동작은 변경하지 않았다.
- TipTap 3.31.3 editor JSON fixture로 orderedList `start=5,type=A`, table `colspan=2,rowspan=2`의 상세 렌더·편집 저장 round-trip 회귀를 추가했다.

## 검증 결과

- RED: whole-review 회귀 묶음에서 8개 의도 실패를 재현했다(기존 문서 교체 2, category fallback 1, draft picker paging 1, Prose malformed/attrs 2, EditPage draft navigation 1, SettingsPosts paging 1). 첫 경로 지정 오류로 `No test files found`가 한 번 있었고, 올바른 상대 경로 RED는 `4 files / 8 failed / 61 passed`, exit 1이었다.
- 관련 GREEN: `npm.cmd test -- --maxWorkers=1 src/features/post/PostEditor.test.tsx` → 1 file / 11 tests passed; `npm.cmd test -- --maxWorkers=1 src/test/components.test.tsx src/test/router.test.tsx src/test/categoryBehavior.test.tsx` → 3 files / 59 tests passed.
- fresh 최종 구조화 전체: `npm.cmd test -- --maxWorkers=1 --reporter=json --outputFile=../build/m4-frontend-final-results.json` → JSON `testResults=28`, assertions `311`, passed `311`, failed `0`, skipped/todo `0`, exit 0. OOM을 힙 상향·skip·제외로 우회하지 않고 최소 worker 직렬 실행으로 종료를 확인했으며 root가 `build/m4-frontend-final-results.json`을 직접 읽을 수 있다.
- `npm.cmd run lint` → exit 0.
- `npm.cmd run build` → exit 0 (`tsc -b`, Vite 132 modules, JS 857.80 kB / gzip 264.76 kB); 500 kB chunk warning만 남았다.
- R7 영향범위 구조화: `npm.cmd test -- --maxWorkers=1 --reporter=json --outputFile=../build/m4-frontend-r7-results.json src/test/components.test.tsx src/features/post/PostEditor.test.tsx` → `2 suites / 40 tests`, passed 40, failed/pending/todo 0, success true. 전체 311 suite는 재실행하지 않았다.
- 변경 파일 재독 및 `git diff --check -- frontend .claude/team/frontend` → `DIFF_CHECK_EXIT=0`을 확인했다(LF→CRLF 경고만). backend API compatibility와 1440px 실제 화면은 root/독립 QA 범위다.

## 최종 인계

- 독립 whole reviewer 최종 판정은 `Ready to merge: Yes`이며 R1~R8 및 R7 후속 잔여는 0이다.
- root가 API, Chrome R1/R4/R5, orderedList `start=5,type=A` 및 lower-alpha 표식, 최종 PNG artifact를 검증·보존했고 M4 worklog 18:15에 기록했다.
- FE 범위의 독립 review/runtime pending은 해소되었다. 남은 것은 root 소유 Git 통합/merge뿐이며, 이 역할은 Git stage/commit/push를 하지 않았고 통합 완료·대기 완료를 주장하지 않는다.
- 이 기록 이후 frontend 역할의 추가 문서 쓰기·제품 수정·테스트/빌드는 계획하지 않는다. logout UI는 이번 배정 범위가 아니다.

## 주요 산출물

- `frontend/src/features/post/PostEditor.tsx`
- `frontend/src/features/post/DraftPicker.tsx`
- `frontend/src/components/ui/Prose.tsx`
- `frontend/src/pages/EditPage.tsx`
- `frontend/src/pages/SettingsPostsPage.tsx`
- `frontend/src/features/post/PostEditor.test.tsx`
- `frontend/src/test/components.test.tsx`
- `frontend/src/test/router.test.tsx`
- `frontend/src/test/categoryBehavior.test.tsx`
