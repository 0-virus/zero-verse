# comment service 경계

현재 게시글 ACL·ACTIVE actor·양방향 BLOCKED를 확인한다. create/update/delete는 lock 이후 post와
comment/parent를 재조회하며 parent는 같은 post의 살아 있는 root만 신규 reply를 허용한다. M6에서는
notification row를 생성하지 않는다.

M8에서는 위 M6 당시의 미생성 경계를 대체한다. create는 새 transaction executor에서 원본 INSERT와
COMMENT/REPLY 알림을 함께 저장·flush한다. update/delete는 생성하지 않으며 원인 삭제 후 알림 이력을 보존한다.
