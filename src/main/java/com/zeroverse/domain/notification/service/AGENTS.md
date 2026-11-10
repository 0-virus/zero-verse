# notification service 경계

GET은 RR snapshot에서 현재 ACL·actor·target을 재평가하고 SUSPENDED viewer의 읽기를 허용한다.
read/delete/read-all은 receiver ACTIVE 재검증과 소유권·차단 경계를 지키며, Task3 생성 연계는
같은 transaction에 참여할 수 있는 명시적 generator 저장 계약을 사용한다.

생성 service는 원행위 transaction 안에서만 사용한다. `NotificationEventService`는 COMMENT/REPLY 수신자를
ID로 dedupe(REPLY 우선)하고 자기행위·현재 ACL·block을 검사한다. POST 수신자는 publication lock에서
현재 ACCEPTED·생존·차단을 확정한다. `NotificationGenerator`의 100개 logical batch는 단일 transaction이며
IDENTITY 엔티티의 실제 INSERT statement 수와 JDBC batch 여부는 성능 증거에서 구분한다.
