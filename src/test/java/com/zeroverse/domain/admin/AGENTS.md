# M9 관리자 backend 검증

REQUIREMENTS §6.13-A·ADR-0008·승인 M9 회의 §5/§10/10.1을 실제 MySQL로 검증한다.
Controller는 현재 DB 권한·malformed ACCESS·검증·no-store, Query는 scalar 통계·literal 검색·
안정 paging·KST/UTC 경계, Mutation은 self/current actor·audit·user-only 삭제와 하위 row 보존을
담당한다. Concurrency는 실제 users/refresh row wait와 유한 종료, QueryBound는 인증 포함
SELECT<=14·size1→100 증가0·LONGTEXT 미적재 및 목록/상세 RR writer barrier를 검증한다.
Testcontainers 합성 DB만 사용하며 실사용 DB·환경·업로드·migration을 변경하지 않는다.
