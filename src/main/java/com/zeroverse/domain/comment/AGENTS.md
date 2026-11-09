# comment 도메인

M6 댓글·대댓글 CRUD와 bounded 조회를 소유한다. V1 `comments` 테이블을 재사용하며 댓글은
`deleted_at` soft delete를 사용한다. 목록은 root와 1단계 reply를 각각 `PageResponse`로 제공하고
삭제 댓글·탈퇴 작성자는 placeholder로 보존한다. mutation은 post interaction lock과 현재 ACL·차단
상태를 lock 이후 재검증한다.
