# comment service 경계

현재 게시글 ACL·ACTIVE actor·양방향 BLOCKED를 확인한다. create/update/delete는 lock 이후 post와
comment/parent를 재조회하며 parent는 같은 post의 살아 있는 root만 신규 reply를 허용한다. M6에서는
notification row를 생성하지 않는다.
