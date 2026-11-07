# universe 도메인 경계

`Universe`는 V1 `universes` 테이블의 단방향 신청-수락 관계다. M5 관계 CRUD·목록·snapshot·차단은
이 도메인 소유이며 M4 게시글 접근 predicate는 ACCEPTED 방향만 읽는다. 재신청 가능한 거절·취소·삭제·
차단 해제는 hard delete하고 V1 migration은 수정하지 않는다. 모든 mutation은 actor/target 사용자 행을
ID 오름차순으로 잠근 뒤 양방향 row를 다시 읽는다.
