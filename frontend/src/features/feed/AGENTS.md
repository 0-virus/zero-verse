# feed feature 지침

M7 메인 피드의 API 경계와 화면 DTO를 소유한다.

- 서버 `FeedPage`와 `FeedPostSummary`를 그대로 소비하며 total/count를 클라이언트에서 재집계하지 않는다.
- guest는 `/api/v1/feed/public`, 인증 사용자는 `/api/v1/feed/universe`만 호출한다. `friends`/`following`의 의미는 M7 승인 계약을 따른다.
- keyword는 trim과 연속 공백 한 칸 정규화 후 UTF-16 100자 제한을 적용한다.
- API 함수 외부에서 `apiClient`를 직접 호출하지 않는다. 계정·URL 세대 stale 방어는 화면에서 담당한다.
- 디자인은 `docs/design/DESIGN-SYSTEM.md`와 Main Feed v2 원본의 1440px·radius0·토큰을 따른다.
