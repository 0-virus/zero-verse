# like 도메인

M6 post like와 interaction 상태 조회를 소유한다. V1 `post_likes`의 `(post_id,user_id)` unique와
hard delete를 유지하고 post interaction lock으로 같은 post의 add/remove를 직렬화한다. M6에서는
notification row를 생성하지 않는다.

M8에서는 위 M6 당시의 미생성 경계를 대체한다. absent→present INSERT 분기에서만 LIKE를 생성한다.
반복 POST는 0건, 실제 unlike→relike는 새 사건이다. 원본/알림은 같은 새 transaction이며 unlike는 이력을 지우지 않는다.
