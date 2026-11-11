# admin service 경계

조회는 actor current DB 권한 확인 뒤 `REPEATABLE_READ` snapshot에서 목록·summary·상세를
읽는다. mutation은 ID 오름차순 User lock 후 actor→target 현재 상태를 재검증하고,
동일값은 변경하지 않는다. user-only soft delete로 child row/파일/refresh 이력을 보존한다.
이미 managed 상태였던 User도 lock 후 `refresh(PESSIMISTIC_WRITE)`로 최신 상태를 재확인한다.
M9 aggregate의 audit DTO/주 경계는 UTC DB DATETIME 기준이며 서버 기본 시간대에 의존하지 않는다.
