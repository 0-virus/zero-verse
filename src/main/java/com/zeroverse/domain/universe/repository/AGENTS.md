# Universe repository 경계

관계 방향·상태·soft-deleted 사용자·닉네임 필터는 DB 쿼리에서 제한한다. 목록 정렬은 서비스가 전달하는
`updatedAt DESC, id DESC` Pageable을 사용하며, pair mutation은 서비스의 사용자 row lock 뒤 재조회한다.
