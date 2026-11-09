# comment feature 지침

- 댓글 API는 이 경계의 `commentApi.ts`에서만 `apiClient`를 호출한다.
- 댓글 본문은 plain text로 소비하며 HTML/Markdown을 삽입하거나 변환하지 않는다.
- `CommentItem`의 `content=null`, `deleted=true`, `author=null`은 삭제/탈퇴 placeholder로 보존한다.
- root와 reply `PageResponse`는 화면에서 독립적으로 관리하고, `canEdit`·`canDelete`·`canReply` 서버 상태를 임의 추론하지 않는다.
- PostDetail 행동 테스트와 API 계약 테스트를 함께 유지한다.
