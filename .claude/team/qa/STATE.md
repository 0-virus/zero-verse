# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

## 현재 단계

- **M5 독립 최종 인수·dev 통합은 완료** 상태다. M5 finding BE-001/BE-002/QA-001/QA-002/DOC-001/V-001/V-002는 이전 최종 기록대로 CLOSED다.
- **M6 댓글·좋아요 최종 독립 QA는 PASS**다. `docs/governance/meetings/M6-20261109-comments-likes.md`의 보완 A-v2는 `APPROVED`·HIGH이며 `REQUIREMENTS` FR-LIKE-03/확정 세부 계약과 `PRD`에 동기화됐다.
- 최종 source 동결본을 backend/FE 독립 검토하고, backend 77 suites/490 tests, FE 34 files/369 tests·lint/build, 실제 HTTP/Chrome 1440px 52 checks, MySQL 경쟁/query-bound/no-notification 증거를 대조했다. 모든 필수 M6 gate가 CLOSED이며 PR #12가 `dev`에 통합됐다(merge `4399f73acf19d66d96fafd2c4784c6a03fcfa282`).

## 진행 중

- `docs/REQUIREMENTS.md` FR-COM-01~04/FR-LIKE-01~03, NFR-04/09, `docs/PRD.md` §5.7/5.8/§7.2/§9.2-P/§9.3-③/§9.9/§10~12, 디자인 원본, M6 worklog/회의록과 최종 source를 읽었다.
- V1 `comments`/`post_likes` 테이블·unique/index와 최종 backend `Comment`·`Like` repository/service/controller/OpenAPI, frontend feature/PostDetail 연결을 승인 A-v2와 대조했다. 초기 baseline의 “부분 구현·closure 대기” 표현은 과거 관측이며, 최종 backend/FE gate는 §최종 판정과 WORKLOG 최신 항목을 따른다.
- 승인된 A-v2와 정본의 M6 no-notification, FR-LIKE-03 interactions endpoint, 삭제 root GET/create, count 제외, DTO/길이/차단·unlike/lock 계약을 `M6-CONTRACT-01~06` 검증 주의점으로 정리했다.
- FE round1에서 FE-001~004를 닫았고, round2/round3에서 FE-005/006 및 WR-01/02/03까지 재검토해 모두 **CLOSED / Important 또는 Minor**로 갱신했다. root fresh FE는 `34 files / 369 tests`, lint/build PASS다.
- M6-BE-03 query-string 공개 matcher는 실제 immutable runtime HTTP로 `comments?page=0&size=20`, `parentId` reply page, guest interactions, malformed Bearer AUTH_004, private/no-store를 확인해 **CLOSED (실제 HTTP 경계)**로 갱신했다. `.param` 추정이 아닌 `build/m6-acceptance/evidence.json`의 실제 URL 호출 증거다.
- whole review의 `FE-005` root page 왕복 stale reply loading과 `FE-006` canonical GET 권한 거부 상태 잔류는 round2/round3 source·unit 재검토에서 **CLOSED / Important**로 갱신됐다. WR-01/02/03은 ADDRESSED이며 새 Critical/Important finding이 없다.
- `qa/m6-acceptance.mjs`의 읽기 권한 상실 browser 시나리오를 최종 실행했다. actor 화면을 열린 채 owner의 전체 `UpdatePostRequest`로 동일 합성 PUBLIC 글을 PRIVATE로 전환하고, stale like `LIKE_001`→canonical interactions `POST_002` 및 좋아요 count/댓글/action clear를 확인했다. 최종 evidence는 기존 46개에 6개를 더한 52/52 PASS다.

## 산출물

- `qa/M6-review.md`: 승인 상태 경계, source baseline, FR별 인수 매트릭스, 실제 HTTP/Chrome 1440px, MySQL 경쟁·query bound·rollback·account stale 시나리오.
- `qa/m6-visual-qa.mjs`: 승인 계약에 맞춘 API/Chrome 1440px PostDetail 검증 harness(최종 acceptance 증거와 함께 검토 완료).
- `qa/m6-acceptance.mjs`: random synthetic fixture 생성·seed와 실제 브라우저 mutation/access-loss acceptance harness(최종 실행 52/52 PASS). credentials/token은 메모리 전용이다.
- `build/m6-acceptance/evidence.json`: 최종 immutable runtime 결과 `52 PASS / 0 FAIL`, 1440px screenshot 3개 및 synthetic fixture ID만 기록(credential/token 없음).

## 다음 작업

1. M6 QA 및 dev 통합 closeout 완료. 후속 마일스톤 착수 시 새 승인 정본과 별도 QA 범위를 만든다.
2. 후속 마일스톤에서는 M6 범위 밖 BlogPage/PostCard count 및 M8 notification 계약을 별도 기준으로 검토한다.

## 차단 요인

- M6 QA blocker는 없다. backend/FE 최종 source·자동검증·MySQL 경쟁/query-bound/no-notification/ACL·HTTP/Chrome evidence가 모두 closure됐다.
- M6 승인 계약상 notification row는 생성하지 않고 FR-NOT-05는 M8 범위다. 최종 backend 증거에서 M6 mutation 전후 notification 불변을 확인했으며, M8 생성 자체는 이번 판정 범위가 아니다.

## 갱신 시각

- 2026-11-09 KST — M6 최종 독립 QA PASS 및 dev 통합 완료. FE-001~006 CLOSED, WR-01/02/03 ADDRESSED, backend 77 suites/490 tests 0/0/0, FE 34 files/369 tests·lint/build PASS, final immutable runtime 52/52 PASS, 1440px 원본·제품 PostDetail 대조 PASS. PR #12 merge SHA `4399f73acf19d66d96fafd2c4784c6a03fcfa282`, 통합 tree `7dd390ace316a1d548e4755dfc84c21f9e7bcdce`를 root 보고로 확인했다.

## 소유권 준수

QA는 `qa/**`, `.claude/team/qa/**`만 수정했다. 제품 코드·타 역할 테스트·PRD·REQUIREMENTS·governance·M6 worklog 원본·Git은 수정하지 않았다. 기존 root의 `.claude/team/JOURNAL.md` 및 M6 정본 untracked 변경은 보존했다.
