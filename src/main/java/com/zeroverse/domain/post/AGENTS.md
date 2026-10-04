# post 도메인

M4 게시글·콘텐츠·조회수·본문 이미지 연결을 소유한다.

- 저장 원본은 검증된 TipTap JSON이며 `content_html`은 서버 렌더링·정화 결과만 저장한다.
- 모든 쓰기는 활성 blog lock → 활성 owner 확인 → post lock 순서를 따른다.
- 공개 predicate는 PUBLIC/UNIVERSE/PRIVATE·draft와 Universe 방향을 함께 평가하며, 비공개 데이터가 목록·인접글·이미지 경로에서 누출되지 않도록 한다.
- `V1__init.sql`은 수정하지 않고 스키마 변경은 forward migration으로만 추가한다.
- 테스트는 실제 MySQL Testcontainers(`MySqlTestSupport`)와 MockMvc를 사용한다.
