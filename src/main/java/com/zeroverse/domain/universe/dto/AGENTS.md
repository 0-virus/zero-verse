# Universe DTO 경계

Universe API 응답은 최소 사용자 요약만 노출한다(`id`, `nickname`, `profileImageUrl`, `blogSlug`).
실명·email·birthDate·bio와 상대의 차단 방향은 포함하지 않는다. snapshot은 방향별 상태와 PENDING
requestId를 보존한다.
