# backend 현재 상태

## 현재 단계

- 2026-11-10 사용자 A-v2 전체 승인 M8 Task3 생성·원자성·경쟁 구현 및 independent review1 Important fix round1의 자체검증과 source freeze를 완료했다. 구현자 `/root/m8_generation_impl`; Task2 API/entity/V6 구현자는 `/root/m8_backend_api`로 분리한다. 최종 독립 QA/M8 완료 판정은 아직 아니다.
- 생성 경로는 Post create/update, Comment create, Like add, Universe request/accept이며 원본과 알림을 같은 READ_COMMITTED/REQUIRES_NEW transaction에서 저장한다. 확정 rollback1213/1205/후보충돌만 총3attempt, commit 불명확·업무/권한/validation 오류 재시도 없음.
- POST는 scalar blog→전체 users 정렬→기존 post→원본 변경 직전 current relation-only 재검증 순서다. 새 생존 후보 발견 시 전체 rollback, 삭제 후보 제외, SUSPENDED 수신자 포함. Task2 DTO/API/V6/Generator 저장 인터페이스는 유지한다.

## 진행 중

- review1 Important였던 reject/cancel의 request replacement 경쟁을 actual barrier RED2→GREEN으로 수정했다. 원래 requestId와 잠금 후 row ID가 다르면 UNI_004이며 새 B=PENDING 및 A/B 알림 전체행 snapshot을 보존한다. 제품 변경은 UniverseService 조건2줄이다.
- 최신 `task3-fix1-green-covering.log` exit0/SUCCESS14s, XML3suites26tests/실패오류skip0(Universe unit19/MySQL4/replacement3). `task3-fix1-green-xml/` 보존. 독립 reviewer closure는 아직 후속이며 단일 Gradle 실행권을 root에 반환했다.
- 제품/테스트는 freeze했고 단일 Gradle 실행권을 root에 반환했다. 독립 reviewer가 검토할 상세 파일·실측·한계는 `.superpowers/sdd/M8-notifications/task-3-report.md`에 있다.
- fix round1 이전 영향 테스트 `task3-final-recheck-order-green.log`는 exit0/BUILD SUCCESSFUL, XML3suites/29tests/실패오류skip0(Generation6/Rollback6/FanoutConcurrency17)이다. 직전 최종 executor4/Universeunit19/actor-null legacy1까지 distinct53건이 통과했다. 전체 backend test/bootJar는 root 후속이다.
- 실제 MySQL1213→2attempt,1205→3attempt 소진0건, candidateadded→2attempt1건, 101번째 INSERT 실패전체rollback, mutual publish/read-all 양방향/read-delete/postdelete 경쟁과 data_lock_waits를 검증했다. M6 경쟁/M7 GET 무부작용 회귀 명령도 SUCCESS다.
- 마지막1001 수신자=1001 notification/11logicalbatch/2018SQL/854.487ms/heap 전후차이19,417,736bytes/lockwait0/attempt1이다. heap 값은 GC 포함 차이로 peak/RSS가 아니며 JDBC batch 또는 운영SLA로 과장하지 않는다.

## 다음 작업

- root/독립 QA가 최종 source review·전체 test bootJar·실제 API/브라우저·합성 V5 migration/compatibility·dev 통합 gate를 진행한다. finding이 나오면 원작성자가 재현·수정하고 독립 재검토한다.
- M9/S3/사용자 DB 적용은 현재 범위에 없다. 이전 M4~M7 역사와 Task2 경과는 기존 backend WORKLOG 및 각 마일스톤 worklog가 정본이다.

## 차단 요인

- Task3 자체검증에 미해결 실패는 없다. 별도 root 전체검증/독립승인 gate와 운영 fanout SLA·peak memory 미측정 한계가 남는다.
- 사용자 root 미추적4파일/.env.local/.local-data/DB13307/runtime13318은 보존했다. Git·PR·의존성·migration·공개 DTO 변경을 수행하지 않았다.

## 갱신 시각

2026-11-10 16:56 KST · `/root/m8_generation_impl`
