# like feature 지침

- 좋아요 API는 이 경계의 `likeApi.ts`에서만 `apiClient`를 호출한다.
- 서버 `InteractionState`의 liked/count/canLike/canUnlike를 canonical 상태로 사용한다.
- UI는 낙관적으로 갱신할 수 있지만, 실패 시 이전 상태로 rollback하고 늦은 응답은 PostDetail 세대 guard로 버린다.
- 좋아요 범위는 PostDetailPage이며 BlogPage/PostCard 목록 count는 M6에서 확장하지 않는다.
