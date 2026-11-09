# comment entity 경계

V1 `comments`의 post/user/parent FK와 `deleted_at` soft delete를 유지한다. parent 깊이·동일 post
검증은 service에서 수행하며 entity는 content 변경과 생성만 담당한다.
