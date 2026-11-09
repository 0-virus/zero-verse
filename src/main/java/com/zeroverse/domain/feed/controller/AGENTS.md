# Feed controller 경계

피드 query string은 누락과 명시적 빈 값을 구분해 `VALIDATION_001`로 검증한다. public은 optional
auth, universe는 bearer auth이며 모든 응답은 공통 `ApiResponse`와 `private, no-store` 정책을 따른다.
