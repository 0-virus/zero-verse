# search feature 지침

M7 통합 게시글 검색 API 경계와 검색 DTO를 소유한다.

- 검색 결과는 게시글 중심 `FeedPostSummary`이며 `matchedTypes`는 서버의 전체 매칭 타입 순서를 그대로 소비한다.
- `counts`는 선택 탭·현재 페이지와 무관한 서버 snapshot이다. 클라이언트에서 합산하거나 재집계하지 않는다.
- keyword는 trim과 연속 공백 한 칸 정규화 후 UTF-16 100자 제한을 적용한다.
- API 함수 외부에서 `apiClient`를 직접 호출하지 않는다. URL·계정 세대 stale 방어와 상태 폐기는 화면에서 담당한다.
- 디자인은 `docs/design/DESIGN-SYSTEM.md` §8.8과 Pages 원본의 1000px 단일 컬럼을 따른다.
