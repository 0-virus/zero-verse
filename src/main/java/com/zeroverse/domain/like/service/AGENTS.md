# like service 경계

ACTIVE actor·현재 post ACL·양방향 BLOCKED를 lock 후 재검증한다. add는 idempotent, remove는 block 자체로
PUBLIC 접근을 막지 않는 한 idempotent hard delete다. interactions는 조회수를 올리지 않는다.
