# post feature 지침

- 이 경로는 frontend 역할이 소유한다. 공유 HTTP 계약과 backend 정본을 임의로 넓히거나 변경하지 않는다.
- `contentJson`이 게시글 본문의 원본이며 `contentHtml`은 함께 보내는 파생 snapshot이다. TipTap schema가 허용하는 node/mark/attrs만 저장하고, category·visibility·tag·images를 같은 저장 snapshot으로 보낸다.
- 목록·초안 API는 `PageResponse`의 `items/page/size/totalElements/totalPages/hasNext/hasPrevious`를 소비한다. 초안은 `page=0,size=20`에서 시작하되 후속 페이지 접근을 유지한다.
- 업로드 이미지는 이 feature에서 임의 URL을 만들지 않는다. canonical upload 응답과 이미지 집합을 검증한 뒤 post snapshot에 반영한다.
- 구현 변경은 `PostEditor`/`DraftPicker` 인접 테스트와 `frontend/src/test/`의 post/router 행동 테스트를 함께 갱신하고, 전체 suite 재실행 여부는 부모 지시를 따른다.
