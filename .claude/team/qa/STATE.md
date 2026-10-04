# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-04 KST (사용자 승인에 따른 리더의 상태 문구 갱신)

## 현재 단계

- **로컬 M4 최종 QA PASS**다. PRD §9.7·회의 §20.8의 승인 범위인 로컬 파일 저장, 인증 multipart/content API, 글·이미지 접근권한, 5MiB 제한, 자동 테스트와 root 독립 runtime/browser 증거를 닫았다.
- 사용자가 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”**라고 결정했으므로 Chrome 파일 선택·전송 자동화는 실행하지 않았다. multipart API·FE 자동 업로드, 실제 파일 저장·권한·이미지 표시·재시작 보존은 검증했다.
- 제품 로컬 M4 PASS와 별도로 root의 Git 통합도 확인됐다. 구현 커밋 `4404f1e` 푸시, PR #10 `dev` 머지(`c34ff4e979bd6efffc3a2aea28c2be307ccac3b5`) 완료다. 운영 배포 완료를 뜻하지 않으며 QA는 Git을 조작하지 않았다.

## 최종 검증 결과

- Backend full: `build/m4-final-backend-full-20261003-1900.log` BUILD SUCCESSFUL 13m49s, 보존 archive `build/m4-final-junit-20261003-1915` 직접 XML 집계 66 files/426 tests/0 failure/0 error/0 skipped.
- Backend bootJar: `build/m4-final-bootjar-20261003-1920.log` BUILD SUCCESSFUL 18s. 최종 runtime `build/m4-runtime-20261003-181423.jar` SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`.
- Frontend: `build/m4-frontend-final-results.json` 28 test files/311 passed/0 failed/0 pending; `build/m4-frontend-r7-results.json` 2 files/40 passed/0 failed/0 pending. frontend lint/build exit 0.
- Root 7차 HTTP smoke exit 0. malformed leaf 400/VALIDATION_001, ordered-list attrs true/true, large page 2147483647 정상 empty page, 권한·MIME·5MiB·purpose/rebind/detach/delete/view/snapshot gates 통과.
- 최종 D2B5 runtime에서 OpenAPI posts 201, uploads 201, content GET operation과 실제 오류 응답을 확인했다. 기존 image post23의 재시작 후 HTTP 200/395 bytes/image/png/no-store/nosniff와 원본·파일·HTTP SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`가 일치한다.
- 독립 whole review는 FE R1/R2 방어/R4/R5/R6/R7 및 BE R2/R3/R7/R8을 종료 가능으로 판정했고 추가 Critical/Important 회귀가 없다. root Chrome은 1440px과 ordered-list/image 표시를 확인했다. symlink proof도 read/write 차단과 marker 보존으로 통과했다.

## 다음 작업

1. M4 인수와 원격 통합은 완료됐다. 최종 결과는 `docs/worklog/M4-posts.md` [머지]와 PR #10을 따른다.
2. 후속 제품 변경이 있으면 해당 범위에 새 독립 검증을 적용한다. 기존 테스트를 새 실행으로 재기록하지 않는다.
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
