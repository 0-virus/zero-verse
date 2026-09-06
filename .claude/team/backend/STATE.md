# backend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-06 17:49 KST

## 현재 기준

- 기준 브랜치: `feature/M3-categories`, 기준 HEAD 표기 `1690731`. Git 조작은 이 역할에서 수행하지 않는다.
- M3 category 계약은 [ADR-0005](../../../docs/governance/decisions/ADR-0005-categories-contract.md) `ACCEPTED`, M3 회의록 `APPROVED`, 사용자 `시작` 승인에 근거한다. REQUIREMENTS FR-CAT01~05/NFR04~09와 PRD §5.4/§9.5/§10~12를 함께 따른다.
- 구현 저자는 초안 `m3_backend`와 후속 `m3_backend_resume`이며, migration/wrapper/policy 보완의 제한 작업도 `m3_backend`가 담당했다. 독립 QA 최종 검토와 root 최종 승인은 아직 끝나지 않았다.

## M3 구현 현황

- `src/main/java/com/zeroverse/domain/category/`에 entity·DTO·repository·service·controller와 실제 MySQL count/visibility/order/delete 규칙이 구현되어 있다.
- `src/main/resources/db/migration/V2__category_active_unique.sql`은 V1을 수정하지 않고 active-key unique를 추가하는 forward migration이다. 활성 name/order 제약, soft-delete 후 재사용, parent 경로 및 blog owner 삭제 검증을 포함한다.
- `BlogRepository`의 category 경로는 active blog와 active owner를 확인하고, 쓰기는 blog lock 후 재조회한다. `ErrorCode`의 CAT_004 고정 문구, `GlobalExceptionHandler`의 request binding 400 경계, `JacksonConfig`의 strict numeric/enum 입력 정책이 반영되어 있다.
- category HTTP/OpenAPI 테스트에는 공개 root `parentId: null`, trim-before-size, CAT_004/AUTH 오류, malformed JSON/numeric/enum 입력, 삭제 owner 경계 및 GET `security: []` 구조 검증이 있다. controller 6번째 테스트와 LWW assertion 변경도 최신 full suite에 포함됐다.

## 검증 사실

- root full 검증은 `build/m3-root-full-build.log`에서 exit 0, `BUILD SUCCESSFUL in 10m 24s`로 끝났다. XML 직접 집계는 56 suites/370 tests, failures 0/errors 0/skips 0이며 `CategoryServiceMySqlTest` 14, `CategoryControllerMySqlTest` 6, `CategoryMigrationTest` 1, `GlobalExceptionHandlerTest` 5를 포함한다.
- 산출 JAR는 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`이며 2026-09-06 17:13:54 KST 생성됐다. root가 이를 PID 4904로 127.0.0.1:8080에 기동해 local profile을 확인했다.
- API/DB smoke는 기동 전 V1/success=1 및 users/blogs/categories/posts = 1/1/1/0, 기동 후 V1/V2 success=1/1 및 동일 행수 보존을 확인했다. Swagger 실제 JSON에서 category GET `security=[]`, POST `bearerAuth`를 확인했고 QA HTTP script도 exit 0이었다. 이후 2계정 추가로 행 수가 증가한 것은 정상 smoke 데이터이며 삭제하지 않는다.
- 이전 25-test snapshot과 numeric enum 21-test 실패는 진행 중간의 역사 기록으로 `WORKLOG.md`에 보존한다. 최신 full 결과가 이를 대체한다.

## 다음 작업과 위험

1. 브라우저 FE 검증과 QA/root 최종 검토를 완료한다. 현재 M3 최종 승인·merge는 브라우저 검증 전까지 보류한다.
2. 최신 JAR·API smoke 및 full XML 증거를 독립 검토 기록과 대조한다.
3. M4의 Post category_id 경로는 ADR-0005대로 동일 blog lock에 참여해야 한다.

## 역사 요약

- M0 스캐폴딩과 M1 인증은 `dev`에 머지됐다.
- M2 backend 검증·OpenAPI 보완은 XML 53개/348 tests, failures/errors/skips 0과 JAR/API 문서 smoke를 root·QA가 독립 대조해 완료했고, M2 PR #8은 merge됐다. 상세 명령·SHA·시각은 기존 `WORKLOG.md` 항목을 보존한다.
- M3 이전 상태의 Q1~Q4 결정 대기 기록과 중간 25/21-test 기록은 역사로만 남기며, 현재 구현 판단은 ACCEPTED ADR-0005와 최신 full 검증 증거를 따른다.

## 주요 소유 경로

- `src/main/java/com/zeroverse/**`
- `src/test/java/com/zeroverse/**`
- `src/main/resources/db/migration/**`
- `build.gradle`, `settings.gradle`, Gradle wrapper
