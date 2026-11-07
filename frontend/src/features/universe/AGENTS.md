# universe feature

M5 Universe 관계 화면과 API 경계를 소유한다.

- API 함수는 `universeApi.ts`에서만 `apiClient`를 호출한다.
- 관계 사용자 요약은 `id`, `nickname`, `profileImageUrl`, `blogSlug`만 사용한다.
- 목록은 서버 `PageResponse`를 그대로 소비하며 클라이언트에서 전체 페이지를 합치지 않는다.
- 관계 snapshot은 로그인 계정과 대상 블로그에 묶어 stale 응답을 화면에 반영하지 않는다.
- 시각 토큰은 `frontend/src/styles/index.css`와 `docs/design/**` 정본을 따른다.
