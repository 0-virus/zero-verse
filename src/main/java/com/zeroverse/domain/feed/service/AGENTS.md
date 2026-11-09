# Feed service 경계

피드 조회는 QueryDSL DB predicate 뒤에 offset paging을 적용한다. `content_json`·`content_html`을
읽지 않고 `content_text` projection과 선택 ID batch 조회만 사용한다.
