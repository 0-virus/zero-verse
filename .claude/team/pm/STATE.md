# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 프로젝트 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-10-03 KST (commit-review: M4 문서·검증기 공개 승인 경계 및 XHR 정합화)

## 현재 단계

- PRD §10 기준 M0·M1은 완료·`dev` 머지 기록이 있다.
- M2 설정은 `dev`에 merge commit `4c129e20f58a6ccb9c61246d103934702516c295`로 머지됐고, PR #8은 GitHub에서 `MERGED`(`mergedAt=2026-09-05T20:41:12Z`, 2026-09-06 05:41:12 KST)다. M2 worklog의 최종 `[머지]` 기록과 QA 확인도 완료됐다.
- M3 카테고리는 회의록 §4 Q1~Q4가 사용자 원문 `시작`으로 승인됐고 회의록은 `APPROVED`, ADR-0005는 `ACCEPTED`다. 고정 커밋 `822b4ce1d6c78695e87ac7883e8577b066e03aea`가 PR #9로 `dev`에 실제 머지됐고, root 조회 결과는 `MERGED`, `mergedAt=2026-09-09T00:08:47Z`, merge `59badfe42d539a33091a387b9ee6119d838190b8`이다. 같은 `origin/dev`에서 현재 `feature/M4-posts`가 생성됐다. 고정 11개 기록과 검증 요약만 공개됐고 미커밋 M4 문서는 공개 payload에 포함되지 않았다. 제품 `src`·`frontend`는 `4fc9ae2` 이후 불변이다.
- M3 구현·자동/DB/API 검증은 완료 근거가 있다. BE는 기존 산출물 XML 56 files/370 tests, failures/errors/skips 0/0/0 및 build exit 0이고, FE는 `build/m3-final-frontend-tests.log` 23 files/273 tests PASS, lint/build exit 0이다. QA HTTP smoke와 `/root/m3_final_qa` 독립 최종 QA `APPROVE`, 리더 승인도 완료됐다. 기존 LOCKED·mouse DnD root evidence도 유지한다.
- 최신 사용자 `그렇게 해줘`와 회의록 §10에 따라 M4 이미지 접근 방향은 **비공개 S3 + 글 열람권한 연동**으로 승인됐다. M4 전체 판정은 여전히 `BLOCKED`이며, Q1~Q5 세부 계약과 DB/응답/만료/endpoint·업로드 기술은 `권고(미확정)`이다. 제품 구현·M4 추가 승인·세부 계약 확정은 없다.
- Architecture 검토에서 Q1의 slug-list 생략 권고를 철회했다. 정본의 `/blogs/slug/{urlSlug}/posts`와 `/blogs/{blogId}/posts` 두 경로를 유지하며 API 삭제는 승인하지 않는다. Q2 ledger와 정확한 콘텐츠/API 계약, 이미지 세부 계약은 미결이다. 영구 public URL은 재설계 대상이며, 이미지 v2 §11 초안의 독립 3인 심의 종합은 `APPROVE_WITH_CHANGES/HIGH`로 완료됐지만 상세 계약·사용자 승인은 미승인이다.
- 실제 AWS 값 전 LocalStack 사용은 기존 PRD §13.1 확정 범위이며 AWS 실서비스 비용 없이 검증하려는 경계는 유지한다. 09:34 당시에는 2026-03-23 이후 통합 LocalStack for AWS 이미지의 auth token·사용 조건을 [공식 발표](https://blog.localstack.cloud/localstack-for-aws-release-2026-03-0/)·[설치 문서](https://docs.localstack.cloud/aws/getting-started/installation/)로 확인하고 Process/User/Machine `LOCALSTACK_AUTH_TOKEN` 존재가 모두 `False`, 관련 Docker image/container 조회가 없음을 기록했다. 이는 계정 부재의 증거가 아니며 당시 환경 미확보 역사로 보존한다. 최신 회의 §13 실측은 사용자의 `lstk` Community 로그인·라이선스 성공, `2026.8.1` image·Compose `healthy`·`s3=running`·`127.0.0.1:14566` 단일 publish와 독립 startup QA 좁은 범위 `PASS`(parser 오류 0, 비차단 주의만 확인)로 로컬 startup/auth gate가 해소됐음을 보여 준다. M3 공개 승인·push·PR Ready·dev merge는 완료됐고 M4 문서는 M3 공개 payload에 자동 포함하지 않았다.
- 최신 사용자 `승인. 내가 할 일을 알려줘.`에 따라 비용·구매 없는 SeaweedFS 4.47 공식 native `weed.exe`의 **U0 전용** 다운로드·기동·재검증을 완료했다. SeaweedFS S3는 별도 loopback `127.0.0.1:14568`, 기존 LocalStack `127.0.0.1:14566`과 컨테이너·설정·합성 자원은 보존하며, 브라우저 harness `127.0.0.1:14567`과 기존 AWS SDK Java2 S3 `2.49.6`·Tika core `3.3.2`는 유지했다. Java는 19/19 cases PASS·`/verify` 27/28·exit 1, fresh CUA 브라우저는 19 cases 중 18 PASS·`/verify` 27/28이었다. PAB는 공식 4.47 handler의 무조건 `ErrNotImplemented`로 `UNSUPPORTED HTTP 501`, OwnershipControls는 PASS였다. 브라우저 `over-5mb-signed-at-max`는 fetch 및 same-file XHR 모두 status 미노출(`status 0/error/noheaders`)이었지만 객체 부재 검증은 PASS였다. raw HTTP/TCP의 403은 browser 403/reset의 직접 관찰이 아니며 CORS 대 connection reset 원인은 미확정이다. 기준 완화·skip은 하지 않았고, 임시 XHR 진단 코드는 제거 완료됐으며 jsdom clean-page self-check가 PASS(exit 0)했다. 근거는 `build/u0-seaweedfs-diagnostic-evidence.md`와 기존 runtime 로그다. 전체 U0는 `BLOCKED`다.

## 진행 중

- M2 merge로 M3의 선행 gate는 해소됐고, Q1~Q4 승인 범위는 `active_key`, root page+children/CategoryResponse, 조회자별 count와 `includeDrafts`, `CAT_004~007`, 생성 시 parent 선택·DEFAULT/LOCKED 정책, setup 부분 복구, `last-write-wins`, M4 동일 `blog_id` lock이다.
- M4 준비 기준은 [M4 worklog](../../../docs/worklog/M4-posts.md)와 [M4 기획 심의](../../../docs/governance/meetings/M4-20260908-posts.md) §10·§11.5·§12·§14·§15·§16다. 비공개 S3 + 글 열람권한 연동 이미지 제공 방향과 좁은 U0 검증 범위는 각각 승인됐지만, 독립 3인 종합은 `APPROVE_WITH_CHANGES/HIGH` 조건부 권고로 완료됐고 M4 전체와 A 단계별 구현은 `BLOCKED`·미승인이다. Q1~Q5를 승인된 계약으로 취급하지 않는다. Q1은 두 slug 목록 경로를 유지하고, Q2 ledger·정확한 콘텐츠/API·이미지 DB/응답/만료/endpoint·업로드 계약은 추가 심의 전 동결한다. 이미지 v2 §11 상세 계약·사용자 승인은 미승인이다. U0 검증용 AWS SDK BOM/S3 `2.49.6`·Tika core `3.3.2`는 BE 공식 확인 후 고정됐다. 기존 LocalStack U0는 `localstack.platform.plugin/iam-enforcement` 허용 false 진단으로 BLOCKED이고, SeaweedFS 최종 실측도 Java 27/28·브라우저 27/28 VERIFY, PAB 501 및 브라우저 TypeError/CORS로 전체 U0 BLOCKED다. 회의 §11.5·§12의 U1은 U0 통과 및 별도 M4 소비 계약 승인 뒤 제품 도메인/FE 연결을 검증하는 최소 vertical slice이며 실제 AWS 검증 단계가 아니다.
- TipTap v3의 `Link`/`Underline`이 `StarterKit`에 내장된다는 root 안건의 공식 문서 사실은 별도 확장 중복 등록을 전제하지 않도록 반영하되, 실제 FE 의존성·표/Markdown·sanitizer·이미지·보안 계약은 M4 심의의 `권고(미확정)`으로 유지한다.
- 실제 source/test와 로그는 기존 M3 산출물 기준으로 대조했다. BE JAR SHA-256은 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`로 기존 검증 기록과 일치한다. PM은 제품 코드·QA 산출물·governance·Git을 편집하지 않았다.
- 현재 작업 트리에는 PM 소유 외의 기존 변경 `.claude/team/JOURNAL.md`, `.claude/team/qa/STATE.md`가 있어 보존한다. M3의 고정 11개 기록·검증 요약 공개와 PR #9 `dev` merge는 완료됐고, 최신 사용자 원문 `변경 사항 확인하고 커밋 및 푸시`에 따라 현재 검토된 M4 문서·U0 검증기까지 `feature/M4-posts` 공개 범위가 승인됐다. 이는 Git commit/push 범위이며 M4 제품 구현·U0 PASS·PR/merge 승인은 포함하지 않는다.

## 다음 작업

1. [M4 기획 심의](../../../docs/governance/meetings/M4-20260908-posts.md) §10·§11.5·§12·§14·§15·§16·§19와 최신 진단 증거를 기준으로 전체 M4/U1 및 현재 U0의 `BLOCKED`·미승인을 유지한다. SeaweedFS 4.47 native `weed.exe` U0 대체 실측은 완료됐고, 공식 PAB handler는 `ErrNotImplemented`를 무조건 반환한다. Java는 19/19 cases PASS·`/verify` 27/28·exit 1, fresh CUA 브라우저는 19 cases 중 18 PASS·`/verify` 27/28, same-file XHR은 `status 0/error/noheaders`였다. raw HTTP/TCP 403은 browser 403/reset의 직접 관찰이 아니며 원인은 미확정이다. 기준 완화·skip은 하지 않으며 임시 XHR 진단 코드는 제거 완료됐고 jsdom clean-page self-check는 PASS(exit 0)다.
2. Q1 두 slug 목록 경로는 정본대로 유지한다. Q2 ledger와 Q1~Q5의 정확한 콘텐츠/API·이미지 DB/응답/만료/endpoint·업로드 계약은 별도 초안·심의·사용자 확인 전 동결하며, 제품 endpoint·DB/migration·도메인/제품 FE·60초 TTL·5MB UI·실제 AWS/구매·제품 공개 게시 승인을 진행하지 않는다. 최신 사용자 Git 승인 범위의 M4 문서·U0 검증기 공개는 별도 허용됐지만 제품 구현·U0 PASS·PR/merge 승인은 아니다. 실제 AWS 값 전 LocalStack은 PRD §13.1 범위 안에서만 준비한다.
3. M3 공개 기록 11개와 검증 요약은 승인된 범위로 게시·merge 완료됐다. 최신 사용자 Git 승인에 따라 현재 검토된 M4 문서·U0 검증기는 `feature/M4-posts` 공개 승인 범위에 포함된다. 이는 제품 구현·U0 PASS·PR/merge 승인으로 확대하지 않으며 M4/U0 `BLOCKED`를 유지한다.

## 차단 요인

- M3 검증·공개·merge 차단은 없다. LOCKED UI gate, `/root/m3_final_qa` QA `APPROVE`, 리더 승인, 고정 11개 기록·검증 요약 공개와 PR #9 `dev` merge가 완료됐다. 최신 사용자 Git 승인에 따라 현재 검토된 M4 문서·U0 검증기 공개 범위도 차단되지 않지만, 이는 제품 M4 구현·U0 PASS·PR/merge 승인과 별개다.
- M4의 현재 차단은 독립 3인 심의 종합 `BLOCKED`, Q1~Q5 세부 계약 `권고(미확정)`, U0 보안/라이선스 gate 실패 및 SeaweedFS PAB/브라우저 transport 문제 미해결이다. RISK-0012의 LocalStack 기동 환경 gate는 회의 §13의 `healthy`/`s3=running`/14566 loopback 실측으로 해소됐지만, 기존 LocalStack IAM enforcement와 SeaweedFS PAB 호환성은 해결되지 않았다. SeaweedFS Java는 19/19 cases PASS·27/28 VERIFY·exit 1, fresh CUA 브라우저는 19 cases 중 18 PASS·27/28 VERIFY, same-file XHR은 status 0/error/noheaders이며 raw HTTP/TCP 403은 browser 403/reset의 직접 관찰이 아니다. 기준 완화·검사 skip·U0 PASS 선언은 하지 않는다. 임시 XHR 진단 코드는 제거 완료됐고 jsdom clean-page self-check는 PASS(exit 0)이며 runtime `14567/14568`은 종료돼 LISTENING 0, 데이터는 기존 `0/8/0/8`+신규 `8/8`로 총 6 bucket 보존됐다. A 단계별 진행은 조건부 권고일 뿐 승인되지 않았다. Q1 slug-list 생략 권고는 철회됐고 두 정본 경로를 유지한다. 사용자 승인으로 Q4 이미지 접근 **방향**과 U0 대체 검증 범위가 정해졌지만, Q2 ledger와 정확한 콘텐츠/API·이미지 DB/응답/만료/endpoint·업로드 계약은 미결이다. 회의 §11.5·§12의 U1은 실제 AWS가 아니라 U0 통과·별도 M4 소비 계약 승인 뒤 제품 최소 vertical slice다. `feature/M4-posts` 분기 자체는 root가 완료했지만, 제품 endpoint·DB/migration·도메인/제품 FE·60초 TTL·5MB UI·새 보안/의존성·실제 AWS·구매·전체 M4 구현은 승인하거나 시작하지 않았고, 최신 사용자 Git 승인 범위의 M4 문서·U0 검증기 공개만 별도로 허용하며 제품 공개 게시·PR/merge는 승인하지 않았다. 영구 public URL은 재설계 대상이다.

## 주요 산출물

- `docs/PM-M3-readiness.md`
- `docs/PM-M4-readiness.md`
- `.claude/team/pm/WORKLOG.md`
- `docs/PRD.md`
