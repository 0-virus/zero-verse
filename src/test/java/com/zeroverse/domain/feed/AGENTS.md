# Feed test 경계

피드 테스트는 실제 MySQL Testcontainers와 MockMvc/FeedService를 사용해 ACL, 페이징, batch metadata,
optional/protected bearer 및 공통 응답 헤더를 검증한다. skip으로 환경 실패를 숨기지 않는다.
