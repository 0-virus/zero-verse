# Post discovery repository 경계

M7 피드·검색은 이 QueryDSL repository의 DB predicate를 통해 soft-delete·publication·Universe
ACL·검색 match를 먼저 적용한다. projection은 목록 scalar와 `content_text` 앞부분만 선택하고
`content_json`·`content_html`을 entity로 적재하지 않는다.
