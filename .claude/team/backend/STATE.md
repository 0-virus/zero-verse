# backend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-08 10:05 KST

## 현재 단계

- 기준 브랜치: `feature/M3-categories`, 현재 HEAD `08239e0514b6a1a78f090c3b0961e8fd4a60403e`(`08239e0`). `1690731`은 M3 분기 기준선이므로 현재 기준으로 사용하지 않는다. Git 조작은 이 역할에서 수행하지 않는다.
- M3 category 계약은 [ADR-0005](../../../docs/governance/decisions/ADR-0005-categories-contract.md) `ACCEPTED`, M3 회의록 `APPROVED`, 사용자 `시작` 승인에 근거한다. REQUIREMENTS FR-CAT01~05/NFR04~09와 PRD §5.4/§9.5/§10~12를 함께 따른다.
- 구현 커밋은 `067cd1174aeec1452b6c74402e750fdad75c3c05`이며 구현 저자는 초안 `m3_backend`와 후속 `m3_backend_resume`이다. 독립 QA 최종 acceptance·root 최종 승인·`dev` merge는 아직 끝나지 않았다. M3 검증·PR #9 `dev` merge·마감 기록 완료가 이번 실행의 종료 경계이며, M4는 이번 실행 범위가 아니므로 착수하지 않는다.

## 진행 중

- backend `src/**`, `src/test/**`, Gradle 경로는 `067cd11..08239e0`에서 변경되지 않았다.
- `src/main/java/com/zeroverse/domain/category/`에 entity·DTO·repository·service·controller와 실제 MySQL count/visibility/order/delete 규칙이 구현되어 있다.
- `src/main/resources/db/migration/V2__category_active_unique.sql`은 V1을 수정하지 않고 active-key unique를 추가하는 forward migration이다. 활성 name/order 제약, soft-delete 후 재사용, parent 경로 및 blog owner 삭제 검증을 포함한다.
- `BlogRepository`의 category 경로는 active blog와 active owner를 확인하고, 쓰기는 blog lock 후 재조회한다. `ErrorCode`의 CAT_004 고정 문구, `GlobalExceptionHandler`의 request binding 400 경계, `JacksonConfig`의 strict numeric/enum 입력 정책이 반영되어 있다.
- category HTTP/OpenAPI 테스트에는 공개 root `parentId: null`, trim-before-size, CAT_004/AUTH 오류, malformed JSON/numeric/enum 입력, 삭제 owner 경계 및 GET `security: []` 구조 검증이 있다. controller 6번째 테스트와 LWW assertion 변경도 최신 full suite에 포함됐다.

## 검증 사실

- root가 남긴 기존 full 검증 산출물은 `build/m3-root-full-build.log`의 exit 0, `BUILD SUCCESSFUL in 10m 24s`, XML 56 suites/370 tests, failures 0/errors 0/skips 0이다. `CategoryServiceMySqlTest` 14, `CategoryControllerMySqlTest` 6, `CategoryMigrationTest` 1, `GlobalExceptionHandlerTest` 5를 포함한다.
- 기존 산출 JAR는 `build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar`이며 2026-09-06 17:13:54 KST 생성됐다. SHA-256은 `422F7216E6B70A8BC533C9F84605C9E3368B961C0F0AC1F58A6B9113819FE369`다. root가 이를 PID 8712로 `127.0.0.1:8080`에 기동했다.
- root의 기존 실행 기록에서 M3 합성 DB(13306) 재기동 후 Flyway V1/V2 success=1/1, 기동 시 `users/blogs/categories/posts = 5/5/18/0` 보존을 확인했다. 이후 새 UI 합성 계정이 추가됐으며 fixture는 삭제하지 않는다. Swagger 실제 JSON의 category GET `security=[]`, POST `bearerAuth`와 QA HTTP script exit 0도 기존 evidence이며 오늘 이 상태 턴에 Swagger/full test를 재실행하지 않았다.
- frontend 구현자와 분리된 root CUA 실행에서 `프론트엔드`를 `백엔드` 위로 native mouse DnD했다. UI 성공 notice `카테고리 순서를 저장했습니다.`와 DB active ID/order `19:0, 21:1, 20:2, 22:3`을 확인해 DnD gate는 root 독립 evidence로 PASS다. QA 직접 조작으로 표기하지 않는다.
- 2026-09-08 기존 Chrome fixture(ID 22)의 LOCKED 시도는 경고·확인창·OK focus를 관측했으나 `accept`가 timeout 후 kernel reset으로 끝났고 이어진 `getTab`도 timeout됐다. DB에서 ID 22가 계속 GENERAL인 과거 실패로 보존하며 새 IAB 결과와 섞지 않는다.
- 2026-09-07 과거 재시도에서는 확인창 도구가 `No dialog is showing`을 반환했고 새 탭의 타입이 GENERAL이었다. 이 과거 evidence도 LOCKED 저장 PASS로 승격하지 않는다.
- 2026-09-08 새 IAB fixture(blog ID 7)에서 category ID 26 `LOCKED`, `displayOrder=3` 저장·재조회와 새로고침 후 복원을 확인했다. `회고`의 순서 이동·이름 변경·삭제·타입 선택이 disabled이고 `카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.` notice가 표시됐다. 이는 root 독립 실제 UI·DB evidence로 LOCKED gate PASS이며 QA 직접 조작으로 표기하지 않는다.
- 이전 25-test snapshot과 numeric enum 21-test 실패는 진행 중간의 역사 기록으로 `WORKLOG.md`에 보존한다. 최신 full 결과가 이를 대체한다.

## 다음 작업

1. 새 IAB LOCKED evidence를 독립 QA가 기존 BE/FE/API evidence와 최종 대조해 M3 acceptance를 판정한다.
2. QA 최종 판정 후 PR #9 Ready 전환 및 `dev` merge·M3 마감 기록을 완료한다. 이 순서가 이번 실행의 종료 경계다.
3. 미래에 별도 M4를 착수할 때 Post의 모든 `category_id` 쓰기가 동일 blog lock과 활성·동일 블로그 재검증을 공유하는지 인계 참고로 사용한다. M4는 이번 실행 범위가 아니다.

## 차단 요인

- LOCKED 저장·재조회·reload 후 불변 상태는 root 독립 evidence로 확인됐고, 독립 QA 최종 판정이 남아 있다. QA 판정 전에는 M3 PASS/APPROVE·`dev` merge를 주장하지 않는다.
- PR #9는 Draft/OPEN 상태이며 LOCKED gate와 M3 마감 기록 전에는 `dev` merge를 할 수 없다. M4는 이번 실행 범위가 아니며 착수하지 않는다.
- 이번 문서 전용 턴에는 Gradle/테스트/JAR/API/DB를 재실행하지 않았다. full test/XML·JAR·Swagger·HTTP 수치는 기존 산출물 및 root/QA 최신 기록을 재대조한 것이다.

## 역사 요약

- M0 스캐폴딩과 M1 인증은 `dev`에 머지됐다.
- M2 backend 검증·OpenAPI 보완은 XML 53개/348 tests, failures/errors/skips 0과 JAR/API 문서 smoke를 root·QA가 독립 대조해 완료했고, M2 PR #8은 merge됐다. 상세 명령·SHA·시각은 기존 `WORKLOG.md` 항목을 보존한다.
- M3 이전 상태의 Q1~Q4 결정 대기 기록과 중간 25/21-test 기록은 역사로만 남기며, 현재 구현 판단은 ACCEPTED ADR-0005와 최신 full 검증 증거를 따른다.

## 주요 소유 경로

- `src/main/java/com/zeroverse/**`
- `src/test/java/com/zeroverse/**`
- `src/main/resources/db/migration/**`
- `build.gradle`, `settings.gradle`, Gradle wrapper
