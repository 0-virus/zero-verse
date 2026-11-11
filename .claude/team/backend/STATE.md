# backend 현재 상태

## 현재 단계

- 2026-11-11 M9 Task2 M1 fix round1 구현·자체 검증 완료, source frozen. root의 독립 시간대 평가 A 선택으로 지원 bootRun/test/JAR JVM을 UTC로 일치시켰다. JAR main은 non-UTC를 Spring/DB 시작 전에 명확 거부하며 시간대를 몰래 변경하지 않는다.
- 실제 signup UTC 재검증에서 DATETIME(0) 반올림의 별도 실패도 발견했다. root 추가 ruling으로 SecondPrecisionAuditTimeProvider가 기존 JVM local audit의 소수 초만 버리도록 보정했다. provider timezone 전환/legacy reader/manual writer/기존행/schema 변경은 없다.
- M1 이전 full107suites632tests0/0/0와 앞선 Task2 Approved는 당시 baseline이다. 신규 발견 뒤 독립 판정은 Needs fixes였으며 현재 수정본은 scoped 재검토·새 full을 기다린다.

## 진행 중

- 최종 recovery-runtime-utc-green-2:15suites79tests/0failure0error0skip, bootJar PASS30s. host JAVA_TOOL_OPTIONS를 Asia/Seoul로 둔 상태에서 Gradle test의 명시 UTC가 실제 우선됨을 확인했다.
- 실제 HTTP signup DB/DTO createdAt·updatedAt UTC before/after bounds, 즉시 joinedThisWeek1→2, .999999999 provider/실제 DATETIME(0) floor insert/update, 제어된 서로 다른 초 updatedAt strictafter/createdAt보존, non-UTC 기동거부2/UTC기동1 및 주변 Post/Discovery/Comment/Notification/Refresh 회귀 통과.
- 최종 실제 JAR clean-env KST 실행은 UTC안내와 exit1, Spring배너/Hikari/DB 초기화 없음. 원문 KST signup RED와 UTC 정밀도 RED는 보존했다. 증거/명령/영향은 task2 report의 M1 round1 append, manifest는 build/m9-evidence/task2/recovery-runtime-utc-source-manifest.json이다.

## 다음 작업

- root에 Gradle 실행권 반환. 독립 scoped 재검토와 최종 source freeze 이후 root 새 full·bootJar, 합성 실제 API/Chrome 인수, 승인된 dev 통합을 수행한다. 기존632와 신규79를 합쳐 새 전체PASS로 표현하지 않는다.
- README는 root, PRD는 별도 소유자가 UTC runtime 전제와 역사 DATETIME 한계를 동기화한다. backend는 이 문서를 편집하지 않았다.

## 차단 요인

- M1 자체 targeted의 남은 실패는 없다. 독립 재검토/최종 full·인수·dev 통합은 후속 gate다.
- 영향은 BaseEntity 상속11entity의 앞으로 생성·수정되는 createdAt/updatedAt precision이다. 같은 초 updatedAt 동일은 기존 DATETIME(0) 정밀도 한계다. 기존 DATETIME은 provenance가 없어 실제 사용자 DB 시간대 전환·재해석·일괄 ±9h 보정은 별도 조사/결정 전까지 금지한다.
- 사용자 DB13307·환경·업로드·기존행·schema·legacy reader/manual writer·FE/QA/Git은 변경하지 않았다. 운영 bootstrap/전역 revoke/M10/배포는 범위 밖이다.

## 갱신 시각

2026-11-11 15:35 KST · `/root/m9_backend_recovery` (M1 round1 source frozen, Gradle root 반환)
