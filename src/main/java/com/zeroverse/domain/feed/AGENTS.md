# M7 feed 도메인 경계

공개·유니버스 피드는 `PostDiscoveryRepository`의 DB ACL/정렬/페이지 결과만 사용한다.
서비스는 `REPEATABLE_READ` read-only snapshot에서 page count와 선택 post ID의 batch metadata를
조합하며 조회수·알림·관계 mutation을 수행하지 않는다. `relation=following`은 M7 피드 전용 의미다.
