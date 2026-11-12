# backend 현재 상태

## 현재 단계

- M10 `IMPLEMENTATION / TASK2 DONE · RUNTIME CONTRACT DIAGNOSIS HANDED OFF · ROOT REVIEW PENDING` 단계다. M9 A-v2 backend는 PR #15로 `dev`에 통합되어 완료 상태이며, 현재 branch는 `feature/M10-closeout`이다.
- M9 merge SHA는 `c0df2d33d0dce5ac71f1b358672606f717e739ae`, tested head는
  `e3efa6b5c81291c006fc824e064d90d42cb6e0af`, 통합 tree는
  `409c084b2189f13fb5f44a5ffb375726475becaf`다.
- M10 Task2에서 승인 brief·회의록·REQUIREMENTS NFR-09·PRD §11~§12의 결정성/빌드 gate 범위를 적용했다. M10 전체 완료·branch PASS를 뜻하지 않으며 root의 독립 review·FE/QA·통합 gate가 남아 있다.

## 진행 중

- Task2 산출물은 `gradlew` 표준 Gradle 8.10 script 복구와 POSIX executable mode `755`, `PostConcurrencyTest`의 test-context Clock 고정이다. `gradlew.bat`·wrapper properties·wrapper JAR·제품 main source·API/DB/security/dependency는 변경하지 않았다.
- 조회수 경계 테스트는 `FIXED_NOW=2026-11-12T03:00:00Z`와 `@SpyBean Clock`을 해당 Spring test context에서만 사용하고 `@AfterEach reset`으로 정리한다. `PostService`의 24h 정책은 변경하지 않았다.
- 최종 fresh gate는 JDK21/전용 Docker socket/Gradle cache와 `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/private/tmp`(macOS `/var` symlink-ancestor 회피)를 사용해 `test check bootJar --rerun-tasks --no-daemon --max-workers=1` exit 0으로 끝났다. JUnit XML 직접 집계는 109 suites / 637 tests / failures 0 / errors 0 / skipped 0이다.
- 현재 source 정적 집계는 request-mapping annotation 75개(클래스 레벨 포함), `@Operation` 61개, mapping 파일 14개다. 최종 JAR SHA-256은 `1ade616199bd0da11daa5856dcd5d88fe8fae4042c0ee8ab66725baa01188b21`이다.
- 기존 preflight의 M9 historical acceptance(396 PASS)와 M0~M8 수치는 현재 M10 source/JAR 증거로 합산·재사용하지 않는다.

## 다음 작업

- root가 Task2 report·diff·검증 증거와 실제 `FR-POST-05` 캐시 헤더 판정을 독립 review에 넘기고, FE/QA current artifact 및 M10 전체 HTTP/Chrome/manifest gate와 함께 최종 판단한다. QA는 PUBLIC guest 상세 `private` 오판을 수정해야 한다.
- backend는 추가 제품 수정·API/DB/security 계약·dependency·migration·배포/S3·실사용 DB 적용을 하지 않는다. 새 backend 제품 실패가 관찰될 때만 root의 명시 배정으로 backend 소유 범위를 재개한다. 이번 `private-no-store` 실패는 QA 계약 불일치로 판정했다.

## 차단 요인

- Task2 자체의 제품 차단은 없다. 다만 macOS 기본 JUnit `@TempDir`가 `/var` symlink 아래에 놓이면 `LocalImageStore`의 의도된 symlink-ancestor 보안 검사와 충돌해 upload 테스트 7건이 실패할 수 있다. 최종 gate에서는 테스트 worker에 `-Djava.io.tmpdir=/private/tmp`를 전달해 15건을 재실행했고 모두 통과했다. 이는 제품 코드 완화가 아닌 실행 환경 조건이다.
- root fresh gate·독립 QA·현재 source/JAR identity가 M10 전체에서 닫히기 전에는 M10 완료나 branch PASS를 주장할 수 없다.
- RISK-0001/0003/0005/0006/0007/0008/0009/0010/0011/0012/0013과 RISK-0021은 각각 운영·기존 DB·S3/U0·추가 정책 범위의 잔여 위험이다. M10 로컬 closeout의 PASS와 운영/배포 readiness를 혼동하지 않는다.
- RISK-0021(기존 DATETIME provenance·실사용 DB 시간대 전환·운영 ADMIN bootstrap)은 별도 심의/승인 전까지 OPEN이다. 사용자 DB `13307`, 기존 환경·업로드, S3, 배포, 운영 전역 revoke는 이번 범위가 아니다.

## 갱신 시각

2026-11-12 13:48 KST · `/root/m10_cache_header_fix` (M10 실제 guest post cache header 계약 대조·root 인계)
