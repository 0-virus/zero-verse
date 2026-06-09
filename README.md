### 실행 구성 편집에서 .env.local 추가해야 함.

Repository 생성
- 이건 이제 들어가도 됩니다.
- 우선순위는 UserRepository, BlogRepository, PostRepository, CategoryRepository, CommentRepository, LikeRepository, TagRepository, PostTagRepository, UniverseRepository 순서
가 자연스럽습니다.

- 유니크 제약이 있는 필드부터 existsByEmail, existsByNickname, existsByUrlSlug, findBy... 메서드가 필요합니다.
