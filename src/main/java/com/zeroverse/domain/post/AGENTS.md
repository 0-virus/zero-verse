# post 도메인

M4 게시글·콘텐츠·조회수·본문 이미지 연결을 소유한다.

- 저장 원본은 검증된 TipTap JSON이며 `content_html`은 서버 렌더링·정화 결과만 저장한다.
- 모든 쓰기는 활성 blog lock → 활성 owner 확인 → post lock 순서를 따른다.
- 공개 predicate는 PUBLIC/UNIVERSE/PRIVATE·draft와 Universe 방향을 함께 평가하며, 비공개 데이터가 목록·인접글·이미지 경로에서 누출되지 않도록 한다.
- `V1__init.sql`은 수정하지 않고 스키마 변경은 forward migration으로만 추가한다.
- 테스트는 실제 MySQL Testcontainers(`MySqlTestSupport`)와 MockMvc를 사용한다.

## M8 발행 알림

create/update는 `NotificationTransactionExecutor`의 새 transaction에서 실행한다. `NotificationPublicationLock`이
scalar blog → owner·전체 후보 receiver ID 오름차순 user 잠금을 수행한다.
PostService가 기존 post를 잠근 뒤 원본 변경 직전에 current relation-only 재검증으로 수신자를 확정한다. 살아 있는 새 미잠금 후보가 발견되면 전체 rollback·최대 총 3 attempt다.
발행 eligible false→true만 POST를 생성하며 M4/M7 콘텐츠·이미지·태그·audit 계약을 유지한다.
