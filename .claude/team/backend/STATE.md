# backend 현재 상태

## 현재 단계

- 2026-11-10 사용자 A-v2 전체 승인 M8에서 backend Task2 API/entity/V6와 Task3 생성·원자성·경쟁 구현이 각각 `/root/m8_backend_api`, `/root/m8_generation_impl`에 의해 완료되었고, 독립 backend review(`/root/m8_backend_review`, `/root/m8_generation_review`)가 승인했다. backend 최종 독립 시각 review는 진행 중이며 M8 전체 완료와 `dev` 통합은 아직 아니다.
- root 전체 backend 검증 `backend-final-2`: 102 suites / 599 tests / failures 0 / errors 0 / skipped 0, `bootJar` PASS, JAR SHA-256 `1cc46fd46eebcbcbe1189235a19875491ee69f54c64e1fb185191095f4630a51`. DELETE OpenAPI 400 보완 뒤 문서·테스트 전용 `backend-final-doc`: 1 suite / 5 tests / failures 0 / errors 0 / skipped 0, JAR SHA-256 `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5`.
- 실제 M8 HTTP/Chrome 인수 `build/m8-acceptance-20261110-173900/evidence.json`: 323 PASS / 0 FAIL / 0 NOT_RUN, source/JAR manifest 일치(source `108e7120a07ab43ebfc550e34a2f13ca7f613d4d0388dd44781bf43cc95add47`, JAR `1cab24e82cb1cf524b0184145ffa49a802db924d4a00b08afc862299014fd1c5`, 396 files). 생성 경로는 Post create/update, Comment create, Like add, Universe request/accept이며 원본과 알림을 같은 READ_COMMITTED/REQUIRES_NEW transaction에서 저장한다.

## 진행 중

- backend 소유 구현·독립 review·전체 자동검증·실제 HTTP/Chrome 인수는 모두 증거를 남기고 통과했다. 현재 남은 단계는 최종 독립 시각 review 종료와 root의 `dev` 통합뿐이다.
- DELETE `400 VALIDATION_001` SpringDoc 보완은 `backend-final-doc` 5개 테스트와 bootJar로 확인되었다. V6/기존 row 보존·생성 원자성·정렬 잠금·후보 재검증·총 3회 retry 계약은 기존 review와 `backend-final-2`에 포함된다.
- 실제 MySQL1213→2attempt,1205→3attempt 소진0건, candidateadded→2attempt1건, 101번째 INSERT 실패전체rollback, mutual publish/read-all 양방향/read-delete/postdelete 경쟁과 data_lock_waits를 검증했다. M6 경쟁/M7 GET 무부작용 회귀 명령도 SUCCESS다.
- 마지막1001 수신자=1001 notification/11logicalbatch/2018SQL/854.487ms/heap 전후차이19,417,736bytes/lockwait0/attempt1이다. heap 값은 GC 포함 차이로 peak/RSS가 아니며 JDBC batch 또는 운영SLA로 과장하지 않는다.

## 다음 작업

- root가 최종 독립 시각 review 결과를 반영한 뒤 `dev` 통합 gate를 진행한다. backend에 새 finding이 생길 때만 원작성자 수정·재검토를 배정한다.
- M9/S3/사용자 DB 적용은 현재 범위에 없다. 이전 M4~M7 역사와 Task2 경과는 기존 backend WORKLOG 및 각 마일스톤 worklog가 정본이다.

## 차단 요인

- backend 기능·테스트·실제 인수의 미해결 실패는 없다. 최종 독립 시각 review가 진행 중이고 `dev` 통합 전이다.
- 운영 fanout SLA·peak memory와 물리 JDBC batch 보장은 이번 승인 증거 범위 밖이다. 1001명 fan-out 수치의 heap 차이는 GC 포함 값이며 peak/RSS로 해석하지 않고, logical batch는 JDBC batch 보장으로 주장하지 않는다.
- 사용자 root 미추적4파일/.env.local/.local-data/DB13307/runtime13318은 보존했다. Git·PR·의존성·migration·공개 DTO 변경을 수행하지 않았다.

## 갱신 시각

2026-11-10 17:41 KST · `/root/m8_backend_final_doc`
