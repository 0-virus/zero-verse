# Search service 경계

검색은 `content_text` 앞부분 projection·EXISTS tag predicate·선택 페이지 ID batch metadata만
사용한다. HTML/JSON LONGTEXT 원문을 목록에서 읽거나 per-card query를 실행하지 않는다.
