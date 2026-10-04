# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-03 18:18 KST

## 현재 단계

- **로컬 M4 최종 QA PASS**다. PRD §9.7·회의 §20.8의 승인 범위인 로컬 파일 저장, 인증 multipart/content API, 글·이미지 접근권한, 5MiB 제한, 자동 테스트와 root 독립 runtime/browser 증거를 닫았다.
- 사용자가 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”**라고 결정했으므로 Chrome 파일 선택·전송 자동화는 실행하지 않았다. multipart API·FE 자동 업로드, 실제 파일 저장·권한·이미지 표시·재시작 보존은 검증했다.
- 이 PASS는 제품 로컬 M4 인수 판정이며 commit/push/merge/release 완료를 의미하지 않는다. QA는 Git을 조작하지 않는다.

## 최종 검증 결과

- Backend full: `build/m4-final-backend-full-20261003-1900.log` BUILD SUCCESSFUL 13m49s, 보존 archive `build/m4-final-junit-20261003-1915` 직접 XML 집계 66 files/426 tests/0 failure/0 error/0 skipped.
- Backend bootJar: `build/m4-final-bootjar-20261003-1920.log` BUILD SUCCESSFUL 18s. 최종 runtime `build/m4-runtime-20261003-181423.jar` SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`.
- Frontend: `build/m4-frontend-final-results.json` 28 test files/311 passed/0 failed/0 pending; `build/m4-frontend-r7-results.json` 2 files/40 passed/0 failed/0 pending. frontend lint/build exit 0.
- Root 7차 HTTP smoke exit 0. malformed leaf 400/VALIDATION_001, ordered-list attrs true/true, large page 2147483647 정상 empty page, 권한·MIME·5MiB·purpose/rebind/detach/delete/view/snapshot gates 통과.
- 최종 D2B5 runtime에서 OpenAPI posts 201, uploads 201, content GET operation과 실제 오류 응답을 확인했다. 기존 image post23의 재시작 후 HTTP 200/395 bytes/image/png/no-store/nosniff와 원본·파일·HTTP SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`가 일치한다.
- 독립 whole review는 FE R1/R2 방어/R4/R5/R6/R7 및 BE R2/R3/R7/R8을 종료 가능으로 판정했고 추가 Critical/Important 회귀가 없다. root Chrome은 1440px과 ordered-list/image 표시를 확인했다. symlink proof도 read/write 차단과 marker 보존으로 통과했다.

## 다음 작업

1. root가 QA 판정을 반영한 명시 파일만 stage하여 diff/commit/PR 절차를 진행한다. QA는 stage/commit/push/merge하지 않는다.
2. 이후 원격 PR/review/merge 결과가 오면 로컬 M4 PASS와 원격 통합 상태를 분리해 기록한다.
3. S3/presigned/AWS/LocalStack/U0 후속과 M5 이후 기능은 새 승인·별도 마일스톤으로 다룬다.

## 비차단 잔여 범위

- 브라우저 파일 선택·전송 자동화는 사용자 명시 결정으로 제외했다.
- S3/실제 AWS/U0 및 운영 배포 hardening은 후속이며 기존 U0 `BLOCKED` 판정을 유지한다.
- M5 관계 CRUD·댓글·좋아요는 이번 판정에 포함하지 않는다.

## 주요 산출물

- `qa/M4-review.md`: 승인 계약, 전체 gate, 최종 로컬 M4 PASS 및 잔여 범위.
- `qa/m4-api-smoke.ps1`: HTTP acceptance helper.
- `qa/M4-u0-review.md`: U0-ALT 독립 판정 및 BLOCKED 위험.
- `.claude/team/qa/WORKLOG.md`: append-only QA 검증 이력.

QA는 제품 코드, 타 역할 테스트, runtime/DB/browser fixture, Git을 변경하지 않았다.
