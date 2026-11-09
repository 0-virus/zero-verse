# like 도메인

M6 post like와 interaction 상태 조회를 소유한다. V1 `post_likes`의 `(post_id,user_id)` unique와
hard delete를 유지하고 post interaction lock으로 같은 post의 add/remove를 직렬화한다. M6에서는
notification row를 생성하지 않는다.
