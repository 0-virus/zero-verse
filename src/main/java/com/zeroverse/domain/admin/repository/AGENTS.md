# admin repository 경계

관리자 조회는 native scalar projection과 batch/집계 SQL로 구현한다. users 목록·상세에서
password/bio/content JSON/LONGTEXT를 select하지 않고, size 1→100 SQL 증가 0 및 인증 포함
SELECT 14 이하를 유지한다. User mutation lock은 삭제 행도 포함해야 한다.
