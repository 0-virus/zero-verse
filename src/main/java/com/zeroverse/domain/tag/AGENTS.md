# tag 도메인

M4 게시글 태그와 PostTag 연결을 소유한다.

- 이름은 trim/lowercase 정규화하고 100자·게시글당 10개 제한을 적용한다.
- tags의 active row는 `normalized_name` unique를 따르며, 동시 생성은 MySQL 원자 upsert 후 재조회한다.
- PostTag는 snapshot 동기화 시 hard delete 후 재생성한다.
