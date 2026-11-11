# ADR-0008 — M9 관리자 계약

- 상태: ACCEPTED
- 결정일: 2026-11-11 KST
- 결정 주체: 사용자 `A-v2 전체 승인`
- 정본: [M9 회의 §5·§10·§11](../meetings/M9-20261111-admin.md), REQUIREMENTS §6.13-A, PRD M9, [실행계획](../../worklog/M9-admin.md)

## 결정

- admin 5개 API는 JWT 유효성 이후 DB현재 ACTIVE ADMIN·nondeleted로 인증한다. claim role과 독립이며 성공/오류 private,no-store. mutation은 deleted 포함 users ID오름차순 lock 후 current actor→target을 재검증한다.
- 자기 ADMIN 회수·정지·삭제는 ADMIN_002/400, 유지 요청은 성공. 동일값은 audit 무변경, missing/deleted target과 반복 DELETE는 USER_001/404.
- 사용자 soft delete만 수행하고 하위 blog/post/comment/universe/notification/upload/refresh 이력은 보존한다. 기존 ACL에 따라 blog/post/image는 열람 불가, 다른 살아있는 글의 댓글은 placeholder, 알림 actor는 masking. 삭제 안내는 데이터보존/접근차단을 정확히 명시한다.
- 마지막 접속을 새로 수집하지 않고 null/기록없음. summary는 삭제사용자 제외 전체기준·이번주 KST월요일, 상세/목록 RR·batch·14 SQL이하/size1→100증가0.
- SUSPENDED의 기존 도메인 상태표·PUBLIC읽기·알림GET read-only를 유지하고 모든 설정쓰기를 새로 금지하지 않는다. refresh 정책과 진행중 요청의 경계를 회의대로 검증한다.
- FE는 현재권한 probe·세대격리·권한폐기시 민감정보/retry 제거, 디자인 1100/860px·1440전용과 승인 카피를 적용한다.

## 대안·영향·검증

접속추적/cascade삭제/복구·감사체계는 DB·개인정보·범위를 확장하므로 제외했다. 제품 bootstrap/실사용 DB 승격은 추가하지 않는다. 합성 ADMIN fixture와 실제 MySQL경쟁·하위 fingerprint·HTTP·1440px Chrome·BE/FE전체자동검증·저자와 다른 QA를 완료 gate로 한다. 기존 V1~V6·사용자 DB/업로드를 보존하고 M10/배포로 확대하지 않는다.

## 2026-11-11 실행 전제 검증 보완

회의 §12의 재현으로 UTC JVM 지원 전제를 실행 경로에 명시한다. bootRun/테스트는 UTC이며 직접 JAR은 `-Duser.timezone=UTC`를 요구하고 비UTC 실행은 DB 연결 전에 중단한다. audit는 기존 DATETIME(0)에 맞춰 초 단위 truncate하여 반올림된 미래 가입 시각을 방지한다. 이는 승인된 UTC DB audit 계약의 결함 수정이며 과거 DATETIME의 시간대 호환성이나 자동 보정을 보장하지 않는다. 기존 DB 적용은 RISK-0021의 별도 점검 대상이다.
