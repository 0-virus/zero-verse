# upload feature 지침

- 이 경로는 frontend 역할이 소유한다. 파일 저장소·권한·바이트 검증은 backend 계약을 소비하며 이 경로에서 우회하지 않는다.
- 업로드 성공 응답은 UUID id, 요청 purpose, 허용 MIME/size와 정확히 일치하는 상대 canonical URL `/api/v1/uploads/{UUID}/content`만 제품 저장값으로 허용한다. external/blob/data/file URL은 쓰기 경로에 넣지 않는다.
- `ManagedImage`는 인증 canonical URL을 Bearer fetch→Blob으로 표시하고, viewer identity/account switch/unmount에서 늦은 응답을 무효화하고 Blob URL을 revoke한다. legacy external URL은 읽기 호환 범위만 유지한다.
- 실제 파일은 1 byte 이상 5 MiB 이하와 허용 이미지 MIME 경계를 따른다. multipart 요청에 JSON `Content-Type`을 강제하지 않는다.
- 변경 시 `uploadApi.test.ts`와 `ManagedImage.test.tsx`를 우선 갱신하고, 이미지 소비 지점의 identity/cache 회귀를 함께 확인한다.
