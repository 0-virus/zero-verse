# Search DTO 경계

검색 응답은 PageResponse 필드와 5개 범위 counts를 함께 반환한다. 각 항목의 matchedTypes는
선택 탭과 무관하게 `POST, BLOG, USER, TAG` 순서를 유지한다.
