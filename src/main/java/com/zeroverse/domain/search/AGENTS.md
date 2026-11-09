# M7 search 도메인 경계

통합 검색은 게시글 중심 결과만 반환한다. `BLOG`·`USER`·`TAG`는 게시글의 매칭 범위이며,
ACL·facet·matchedTypes는 같은 QueryDSL `REPEATABLE_READ` snapshot에서 계산한다.
