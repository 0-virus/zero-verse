# notification service 경계

GET은 RR snapshot에서 현재 ACL·actor·target을 재평가하고 SUSPENDED viewer의 읽기를 허용한다.
read/delete/read-all은 receiver ACTIVE 재검증과 소유권·차단 경계를 지키며, Task3 생성 연계는
같은 transaction에 참여할 수 있는 명시적 generator 저장 계약을 사용한다.
