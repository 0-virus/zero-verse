# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-09 14:48:17 KST

## 현재 단계

- M5 A-v2 FE 구현·디자인 대조·독립 QA를 PASS로 인수했고, root 확인 기준 M5는 `dev`에 통합된 완료 상태다.
- M5 closeout 기준 commit은 `c46a26d`였고, M6 구현은 PR #12로 `dev`에 통합됐다.
- M6 통합: 구현 `55aedea966a45dc0e88a6b0d8926e709b366050e`, merge `4399f73acf19d66d96fafd2c4784c6a03fcfa282`, root 확인 통합 tree `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`; local `dev` fast-forward가 완료됐다. 소유 경계는 `frontend/**` 및 `.claude/team/frontend/**`이며 제품·테스트·Git 추가 편집은 하지 않는다.

## M6 준비·계약 상태

- `docs/governance/meetings/M6-20261109-comments-likes.md` §6 A-v2가 사용자 승인되어 M6 FE Task3→4 구현 및 QA FE-001~004 수정 루프·round2/round3 Important 수정·자체 검증을 완료했다. BE·FE·actual·독립 QA 게이트가 모두 PASS로 닫혔다.
- 최신 §6 계약에는 bounded root/reply 댓글 페이징, `CommentItem.canEdit/canDelete/canReply`, interactions의 `canUnlike`가 반영되어 있다.
- 차단 관계에서는 PUBLIC 글에도 새 좋아요를 허용하지 않고(`LIKE_001`, `canLike=false`), 기존 좋아요 취소는 별도 허용하는 방향으로 계약이 정리됐다.
- FE에 댓글/대댓글 API·types·행동 UI, interactions/좋아요 API·optimistic/canonical 상태, stale guard를 추가했고 `PostDetailPage`에 단일 컬럼 댓글 섹션과 원본 액션바를 연결했다.

## M6 조사 결과

- `frontend/src/pages/PostDetailPage.tsx`와 `frontend/src/features/post/postApi.ts`·`types.ts`를 정본과 대조한 뒤, 댓글/좋아요 계약을 새 `features/comment`·`features/like`로 분리했다.
- 디자인 정본의 PostDetail 헤더 `28px 34px`·26px avatar·우측 tag, 액션바 `18px 34px`·3px border/6px shadow, 댓글 `26px` 간격·34px avatar·13.5px body를 반영했다.
- root/reply 독립 loading/error/page, deleted placeholder, server canEdit/canDelete/canReply, guest/차단 common UX, optimistic rollback와 canonical reload, post/account 세대 guard를 구현했다. QA FE-001 수정으로 mutation API 성공 뒤 canonical GET 실패 시 성공 DTO·soft-delete 상태를 유지하고, GET 오류만 별도 notice/다시 시도 상태로 남긴다. round2에서 root page 전환 시 reply loading/error/request 세대를 함께 초기화하고, `AUTH_004/POST_001/POST_002` 읽기권한 상실은 parent/child 댓글·interaction 상태를 폐기하며 `LIKE_001`·transient 오류는 기존 PUBLIC 읽기 상태를 유지한다. FE-002의 post/account busy reset과 FE-003의 root page generation 포함 reply guard를 적용했다. round3에서 `LIKE_001` mutation 뒤 interactions 재확인(공식 canonical·읽기권한 폐기·일시 오류 보존/재시도)과 canonical callback의 세대 검사를 보강했으며, 글·계정 전환 시 CommentSection의 이전 busy mutation도 초기화한다. 직접 확정된 읽기권한 mutation 거부(`POST_001` 좋아요, `POST_002` 댓글)도 rollback 대신 interaction·댓글 상태를 폐기한다.

## 검증·변경 경계

- 이번 M6 wave에서 변경한 제품 경로는 `frontend/**`뿐이며 새 domain 지침 `frontend/src/features/comment/AGENTS.md`, `frontend/src/features/like/AGENTS.md`를 추가했다. dependency·Git은 변경하지 않았다.
- TDD RED→GREEN: comment/like API 모듈 해석 실패 2 suites/0 tests 후 2 files/3 tests GREEN; QA 수정 전 canonical rollback·busy 고착·root page stale race RED를 재현했다. round2에서 root 복귀 답글 loading 고착과 permission GET 상태 잔존 RED를 재현했다. round3에서 `LIKE_001` 재확인 3분기, 전환 뒤 늦은 canonical 권한거부 전파, 직접 mutation `POST_001/POST_002` 상태 폐기를 RED로 재현한 뒤 수정했다.
- 최종 검증: targeted `npm test -- --run src/features/comment/commentApi.test.ts src/features/like/likeApi.test.ts src/test/m6PostDetailBehavior.test.tsx --maxWorkers=1` → 3 files/31 tests passed; 전체 `npm test -- --run --maxWorkers=1` → 34 files/369 tests passed, failure/error/skip 0; `npm run lint` exit 0; `npm run build` exit 0(`tsc -b`, Vite 141 modules, >500KB chunk warning만); `git diff --check -- frontend .claude/team/frontend` 오류 없음.
- root closeout 전달 기준 BE 490 tests, FE 369 tests, actual 52 checks, 최종 독립 QA 및 WR01~03가 모두 PASS/CLOSED다. npm audit의 기존 2 moderate/6 high와 dependency는 변경하지 않았다.

## 다음 작업 / 차단 요인

- M6 통합 완료·차단 없음. 후속 마일스톤은 별도 범위와 승인으로 다룬다.
- 현재 frontend 역할은 closeout 상태이며 제품/테스트 소스는 동결한다.
