# Universe controller 경계

모든 endpoint는 JWT principal을 사용한다. 공통 `ApiResponse`/`PageResponse`를 반환하고 관계 snapshot에는
`Cache-Control: private, no-store`를 적용한다. 경로와 응답 코드는 REQUIREMENTS FR-UNI-01~12를 따른다.
