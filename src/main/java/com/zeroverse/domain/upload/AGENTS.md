# upload 도메인

M4 로컬 이미지 업로드·metadata·권한 있는 binary read를 소유한다.

- 기본 저장 경로는 `zeroverse.upload.directory`(기본 `.local-data/uploads`)이며 응답에는 canonical API URL만 노출한다.
- 허용 MIME은 JPEG/PNG/WebP/GIF, 바이트 범위는 1..5,242,880이다. Tika bytes 판별과 bounded read를 모두 적용한다.
- 파일명은 서버 UUID, `CREATE_NEW`·containment·symlink 차단을 지키고 파일/DB 실패 시 이번 요청의 임시파일만 정리한다.
- 이미지 GET은 매 요청 DB 연결·resource access predicate를 재평가하고 `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 반환한다.
