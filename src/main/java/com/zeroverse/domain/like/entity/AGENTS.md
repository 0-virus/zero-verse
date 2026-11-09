# like entity 경계

V1 `post_likes`의 post/user FK와 생성 시각을 매핑한다. 삭제는 entity soft delete가 아니라 service의
hard delete로 처리한다.
