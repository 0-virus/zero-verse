# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 프로젝트 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-10-04 KST (사용자 승인에 따른 리더의 상태 문구 갱신)

## 현재 단계

- PRD §10 기준 M0·M1은 완료·`dev` 머지 기록이 있다. M2 설정은 merge commit `4c129e20f58a6ccb9c61246d103934702516c295`로 `dev`에 머지됐고, M3 카테고리는 승인 계약·독립 QA·PR #9 `dev` merge(`59badfe42d539a33091a387b9ee6119d838190b8`)까지 완료됐다.
- 구현 커밋 `4404f1e`는 `feature/M4-posts`로 푸시됐고 PR #10이 `dev`에 머지됐다(`c34ff4e979bd6efffc3a2aea28c2be307ccac3b5`). 로컬 checkout도 `dev`로 fast-forward했다. 최신 HEAD는 Git에서 확인하며 PM 역할은 Git을 조작하지 않았다.
- 최신 사용자 결정은 로컬 파일 저장 기반 M4 완성이다. 서버 로컬 폴더에 실제 파일을 저장하는 multipart API를 사용하고, 글·이미지 접근권한과 최대 5MiB 제한을 유지한다. S3 presigned/비공개 S3 연동, 실제 AWS, LocalStack·SeaweedFS 호환성 검증은 후속 범위이며 M4 로컬 구현의 선행 gate가 아니다.
- 최신 사용자 결정 **`브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해`**에 따라 Chrome 확장의 파일 URL 접근 권한을 확대하지 않는다. 실제 브라우저 파일 선택·전송 자동화만 제외하고, API/FE 자동 업로드, 실제 로컬 저장, 접근제어, 5MiB 경계, 브라우저 이미지 표시, 재시작 후 보존은 인수 범위에 포함한다.
- 승인된 로컬 범위의 M4 구현·검증·최종 QA 문서화·Git 통합이 완료됐다. R1~R8 독립 whole-review는 `Ready to merge: Yes`, 잔여 Critical/Important/Minor `0`, `qa/M4-review.md` 최종 판정은 로컬 M4 PASS다.

## 진행 중

- 진행 중인 제품 작업은 없다. 아래는 완료된 검증 근거이며, 7차 HTTP smoke는 기능 수정 후 F11E artifact에서 실행했다. 최종 D2B5는 UploadController의 OpenAPI annotation만 달라 최종 runtime의 문서와 기존 이미지 응답을 별도로 재확인했다.

- backend 최종 full 로그 `build/m4-final-backend-full-20261003-1900.log`는 `BUILD SUCCESSFUL`(13m49s, exit 0)이다. 보존 JUnit `build/m4-final-junit-20261003-1915/test-results`를 직접 집계한 결과 66 files/426 tests/failures-errors-skips `0/0/0`이다.
- 최종 bootJar 로그 `build/m4-final-bootjar-20261003-1920.log`는 성공했으며 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`는 66,354,802 bytes, SHA-256 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`이다. backend STATE의 R3 SQL ACL/page overflow, R8 deleted parent, profile/upload/V3 targeted 증거와 일치한다.
- frontend 최종 JSON `build/m4-frontend-final-results.json`은 28 files/311 passed/0 failed/0 pending(`success=true`)이며 frontend STATE의 lint/build exit 0 및 R7 후속 2 suites/40 passed 증거와 일치한다.
- 최종 artifact runtime에 대한 7차 HTTP smoke, malformed content `400/VALIDATION_001`, ordered-list `start/type` 보존, `page=2147483647&size=20` 정상 empty 응답, 재시작 전후 기존 PNG URL·메타데이터·395 bytes·SHA-256·비로그인 Chrome 표시 동일성이 M4 worklog에 기록됐다. 실제 PNG SHA-256은 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`이다.
- 최종 D2B5 runtime에서 OpenAPI `POST /posts` 201·`POST /uploads` 201·content GET operation 및 실제 HTTP 응답이 PASS다. 기존 PNG도 HTTP 200/image/png/395 bytes, 로컬 파일과 SHA-256 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67` 일치, `no-store`/`nosniff`를 확인했다. 최종 QA 문서화도 완료됐으며 파일 선택창 자동화만 사용자 결정상 미실행으로 남긴다. U0의 과거 `BLOCKED`는 M4 로컬 PASS와 분리한다.

## 다음 작업

1. M4 제품·QA·PR 통합 결과는 `docs/worklog/M4-posts.md` 최종 [리뷰]·[머지]를 따른다. 추가 M4 구현 작업은 없다.
2. M5 착수나 S3/U0 범위 확장은 별도 사용자 지시 전 진행하지 않는다.

## 차단 요인

- 로컬 M4 제품 인수와 원격 PR 통합의 미해결 차단 요인은 없다. 운영 배포·S3 완료를 의미하지 않는다.
- 사용자 결정으로 브라우저 파일 선택·전송 자동화는 의도적으로 제외한다. 이를 누락된 PASS로 보정하지 않는다.
- U0 LocalStack/SeaweedFS는 IAM/PAB capability·browser transport 이슈로 별도 `BLOCKED`이며, §9.7의 사용자 결정에 따라 로컬 M4 완료를 막지 않는다. S3 연동과 M5 이후 기능도 후속 범위다.

## 주요 산출물

- `docs/PRD.md`
- `docs/PM-M3-readiness.md`
- `docs/PM-M4-readiness.md`
- `.claude/team/pm/WORKLOG.md`
