# QA 산출물 지침

- 이 디렉터리는 독립 QA 검토 기록만 보관한다.
- `src/**`, `frontend/**`, 타 역할 테스트와 governance/worklog 원본은 읽기 전용이다.
- 검토 산출물은 실제 명령·출력·파일 위치·요구사항 근거와 미검증 범위를 함께 기록한다.

## 배포 후속 QA

- `qa/X-deployment-review.md`는 X-deployment A-v2의 독립 검증 매트릭스와 FE LOW 리뷰 슬롯이다.
- `qa/X-deployment-backend-review.md`는 backend 구현에 대한 **별도 2차 독립 검토자 전용 산출물**이다. primary backend 구현자/리뷰어의 결과를 덮어쓰거나 재작성하지 않으며, QA 문서와 하네스는 이 artifact의 open finding·재리뷰 상태를 참조하되 승인으로 승격하지 않는다.
- `qa/x-deployment-s3-acceptance.mjs`는 T5 개발 S3/API/브라우저 인수 전용 fail-closed harness다. 기본은 self-check/NOT RUN이며, root가 synthetic runtime과 명시적 `T5_AWS_WRITE_APPROVED=1`을 전달한 경우에만 승인 prefix에 mutation을 수행한다. private runtime 설정·사용자 DB·비밀·presigned URL/object key 기록은 금지한다.
- `qa/x-deployment-production-acceptance.mjs`는 T6 운영 HTTPS/API/실제 Chrome/S3 인수 전용 fail-closed harness다. 정확한 `https://zeroverse.co.kr`, `T6_RUN_ID=deployment-YYYYMMDD-<고유 suffix>`, `T6_WRITE_APPROVED=1`이 모두 있어야 합성 owner/viewer 2계정과 합성 게시물/이미지만 생성한다. `zeroverse/prod/` 밖의 객체, IAM·버킷 정책, 운영 기존 데이터, SQL reset/delete는 건드리지 않으며, receipt는 토큰·쿠키·비밀번호·presigned URL·object key를 기록하지 않는다. 계정/업로드/post ID·이메일은 ignored `build/t6-deployment-20261116/**/fixture-private.json`에 mode 600으로 기록하고, root 재시작 점검용 합성 email/password만 별도 `credentials-private.json`에 mode 600으로 기록한다. 기본 실행은 self-check/NOT RUN이며 네트워크 DNS 또는 운영 gate 문제로 중단된 결과를 PASS로 승격하지 않는다.
- 이 문서의 `NOT RUN`을 실행 결과나 PASS로 승격하지 않는다. 실제 AWS·운영 DB·배포 서버·사용자 데이터는 명시된 실행 gate와 승인 없이는 사용하지 않는다.
