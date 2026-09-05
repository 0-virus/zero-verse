# backend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-09-06 05:38 KST

## 현재 단계

- 기준 브랜치: `feature/M2-settings`, HEAD `ed8f2c1` (PR #8 원격 HEAD `403eb31`보다 1커밋 앞섬).
- M0 스캐폴딩과 M1 인증은 `dev` 머지 기록이 있다.
- M2 backend 검증과 OpenAPI 문서 최소 보완은 완료됐고 루트·QA 독립 대조 결과 최종 APPROVE됐다. M2 전체는 아직 미머지다. 수정은 Controller annotation과 OpenAPI 회귀 테스트로 한정했다.

## 진행 중

- M2 최신 DTO 변경, HTTP 계약, 보안 allowlist, 동시성 및 전체 backend gate 결과를 부모에게 전달했다.
- Controller annotation에서 M2 보호 operation의 `bearerAuth` 참조, 공개 Blog/auth 발급 operation의 `security=[]`, M2 실제 오류 코드·JSON envelope 응답을 문서화했다.
- `@ApiResponses` 추가로 자동 200 concrete schema가 사라지는 회귀를 발견해 모든 M2 operation에 `200 + useReturnTypeSchema=true`를 추가했다. OpenAPI 회귀는 media type과 무관하게 concrete `ApiResponse<...>` `$ref`를 확인한다.
- 새 JAR의 실제 `/v3/api-docs` JSON을 루트·QA가 직접 대조해 `bearerAuth`, 공개 `security=[]`, concrete 성공 schema와 오류 응답을 확인했다. XML 53개/348 tests/실패·오류·skip 0 및 JAR SHA 증거도 대조 완료했다.
- M3 Architecture 독립 검토(`APPROVE_WITH_CHANGES`, 92/100, HIGH)가 상세 제출됐고 진행자가 회의록에 취합했다. Q1~Q4 사용자 승인 전에는 M3 구현을 시작하지 않는다.
- 사용자·타 역할의 워킹트리 변경은 보존하며 다른 역할 변경과 섞지 않았다.

## 다음 작업

1. 부모가 backend 소유 변경과 역할 기록을 명시적으로 stage/commit하고 M2 실제 머지를 진행한다.
2. 진행자가 M3 Q1~Q4 사용자 승인을 받은 뒤 확정 계약·ADR·위험·worklog 반영 여부를 판정한다.
3. 승인된 M3 계약이 배정될 때까지 backend 제품 파일은 동결한다.

## 차단 요인

- M2 최종 검증은 APPROVE됐으나 실제 Git stage/commit/merge가 아직 완료되지 않았다.
- M3 Q1~Q4 사용자 승인이 아직 완료되지 않았다.

## 후속 위험

- RISK-0005의 실제 HTTPS Refresh-cookie smoke는 최초 배포 전 운영 후속 게이트로 남아 있다.
- 실제 현황은 이 스냅샷보다 Git과 M2 worklog를 우선한다.

## 주요 산출물

- `src/main/java/com/zeroverse/**`
- `src/test/java/com/zeroverse/**`
- `src/main/resources/db/migration/**`
- `build.gradle`, `settings.gradle`, Gradle wrapper
