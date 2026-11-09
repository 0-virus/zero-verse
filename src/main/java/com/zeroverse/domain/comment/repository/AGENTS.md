# comment repository 경계

삭제 row는 목록 placeholder를 위해 조회할 수 있다. root/reply 정렬은 `createdAt DESC,id DESC`를
유지하고 최초 replies는 root별 N+1 없이 MySQL window batch로 조회한다. lock query는 comment row만
잠그고 post/user 잠금은 공통 interaction lock 순서를 따른다.
