# backend 현재 상태

## 현재 단계

- M10 `COMPLETE / DEV INTEGRATED`다. PR #16 (`https://github.com/0-virus/zero-verse/pull/16`)이 `dev`에 merge되었고 merge SHA는 `288f29cffef0671c20a365921f087fe02d4dfcdb`다.
- 검증된 feature head `b5843743c5b86f84cb0bb045e60dc063aa8cccae`와 merge tree `23ce12bc28c1613c04537587692b89ed9f1922e8`가 동일하며 head→merge diff는 비어 있다.
- M9는 PR #15로 이미 `dev`에 통합된 COMPLETE 상태다. M10은 로컬 MVP 마감이며 배포/S3/U0/실사용 DB/운영 ADMIN bootstrap은 포함하지 않는다.

## 완료 산출물

- `gradlew`는 표준 Gradle 8.10 POSIX wrapper와 executable mode `755`로 복구됐다. wrapper JAR·properties·bat와 dependency는 변경하지 않았다.
- `PostConcurrencyTest`는 test-context에만 고정 Clock을 주입해 24시간 경계를 결정적으로 검증한다. `PostService`의 제품 정책은 변경하지 않았다.
- 최종 backend gate는 JDK21·격리 Colima Docker socket·`JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp` 조건에서 `test check bootJar --rerun-tasks --no-daemon --max-workers=1` exit 0이다. JUnit XML 직접 집계는 `109 suites / 637 tests / failures 0 / errors 0 / skipped 0`이다.
- acceptance가 실제 사용한 immutable JAR SHA-256은 `1ade616199bd0da11daa5856dcd5d88fe8fae4042c0ee8ab66725baa01188b21`이다. 최종 forced repackage에서 생성된 `9dedd8...`는 별도 backend gate 산출물이며 acceptance JAR로 대체하지 않는다.

## 범위·증거 경계

- 실제 M10 acceptance는 격리 MySQL `13320`, API `18090`, FE `5180`에서 `82/82 PASS`, failures `0`, 13개 고유 `1440×1000` PNG였다. 이 acceptance의 provenance는 실행 당시 feature head/tree와 source/JAR/FE/harness manifest에 결속되며, 이후 QA/PM 문서 커밋으로 재라벨링하지 않는다.
- frontend 최종 gate는 `51 files / 472 tests`, lint/build PASS다. frontend bundle `958.38 kB` warning은 비차단으로 남겼다.
- `qa/M10-review.md` 독립 QA verdict는 `APPROVED / READY TO MERGE`, whole-branch scoped review는 Critical0/Important0이며 I-1~I-4를 ADDRESSED로 판정했다.
- backend가 실제 M10 acceptance에서 수정하거나 실행한 것은 격리 합성 환경뿐이다. 사용자 DB `13307`, 기존 업로드, S3, 배포 환경은 접근하지 않았다. M10 runtime API/FE/MySQL은 정상 종료됐고 합성 DB·업로드·증거는 보존됐다.

## 다음 작업

- backend 제품 구현·테스트·Gradle 변경은 사용자 또는 새 마일스톤의 명시 배정 전 대기한다.
- 운영 배포, S3 전환/U0, 실사용 DB 시간대·기존 DATETIME provenance, 운영 ADMIN bootstrap, 전역 refresh revoke와 SLA는 별도 결정·검증 범위로 인계한다.

## 차단 요인 / 잔여 위험

- M10 backend 완료를 막는 차단 요인은 없다. macOS에서 upload 테스트를 실행할 때 `/private/tmp`와 전용 Docker socket 조건이 필요하다는 실행 환경 주의만 남는다.
- RISK-0001/0003/0005/0006/0007/0008/0009/0010/0011/0012/0013 및 RISK-0021은 운영·기존 DB·S3/U0·추가 정책 위험으로 OPEN 또는 별도 상태다. local MVP PASS를 production readiness로 확대하지 않는다.

## 갱신 시각

2026-11-12 KST · `/root/m10_backend_merge_record` · PR #16 dev 통합 후 기록 동기화
