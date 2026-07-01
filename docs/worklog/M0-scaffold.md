# M0 — 프로젝트 스캐폴딩

- **브랜치**: `feature/M0-scaffold` (base: `dev`)
- **범위 (PRD §10 M0)**: 백엔드 Gradle/Spring Boot 스캐폴딩(공통 응답·예외·BaseEntity·Auditing·SpringDoc·CORS·Flyway `V1__init.sql`) + 프론트 Vite/React 19/Tailwind v4 골격(AppShell·라우터·§6 디자인 토큰).
- **상태**: 진행 중 (시작 2026-07-01)

## [계획] (Codex · 2026-07-01)

**버전 기준**: Spring Boot `3.5.15`, Gradle wrapper `8.x`(Java 21 toolchain), SpringDoc `2.8.17`, QueryDSL `5.1.0`(jakarta classifier), React `19`, Tailwind `v4`.

### A. 백엔드 (순서)
1. `settings.gradle`(`rootProject.name='zeroverse-server'`), Gradle 8 wrapper(gradlew/gradlew.bat).
2. `build.gradle`: Boot 3.5.15 + dependency-management. deps: web, data-jpa, security, validation, flyway-core, flyway-mysql, mysql-connector-j(runtime), springdoc 2.8.17, querydsl-jpa/apt 5.1.0:jakarta, jakarta.persistence/annotation-api, configuration-processor. test: starter-test, security-test, testcontainers(junit/mysql). Lombok compileOnly+APT(제한 사용, `@Data` 금지).
3. `application.yml`(port 8080, datasource env placeholder, `ddl-auto=validate`, `open-in-view=false`, Flyway, CORS, JWT/S3 placeholder, swagger `/swagger-ui.html`) + `application-local.example.yml`(실제 `application-local.yml`은 gitignore). 테스트는 Testcontainers MySQL 8.4.
4. `com/zeroverse/ZeroverseServerApplication.java`(@SpringBootApplication @EnableJpaAuditing) + 하위 `com/zeroverse/AGENTS.md`.
5. `common/response`: ApiResponse, PageResponse, ErrorResponse(success/data/error/timestamp, UTC ISO-8601).
6. `common/exception`: ErrorCode(AUTH_/USER_/BLOG_/POST_/CAT_/UNI_/COM_/LIKE_/NOT_/ADMIN_/UPLOAD_ prefix), BusinessException, GlobalExceptionHandler(400/401/403/404/409 매핑).
7. `common/entity`: BaseEntity(id/createdAt/updatedAt), BaseSoftDeleteEntity(+deletedAt). 컬럼 snake_case/필드 camelCase.
8. `config`: SecurityConfig(stateless, CORS, CSRF off, permitAll: swagger·`/v3/api-docs/**`·`/api/v1/auth/**`·공개 blog/feed/search 후보), CorsConfig, OpenApiConfig, QuerydslConfig, S3Properties, (JpaAuditingConfig).
9. `db/migration/V1__init.sql`: 테이블 `users, blogs, categories, posts, universes, comments, post_likes, tags, post_tags, post_images, notifications, refresh_tokens`(SQL 예약어 회피). unique: email/nickname/url_slug/tag.normalized_name/refresh_token.token_id, `universes(from,to)`, `post_likes(post,user)`, `post_tags(post,tag)`, `post_images(post,display_order)`. **category type=DEFAULT/GENERAL/LOCKED, category slug 없음.** category 유니크는 `parent_key=coalesce(parent_id,0)` generated column로 `(blog_id,parent_key,name)`·`(blog_id,parent_key,display_order)` 보장, DEFAULT 1개는 앱레벨 테스트로 보강.
10. 테스트: `ZeroverseServerApplicationTests`(@SpringBootTest smoke), `FlywayMigrationTest`(Testcontainers MySQL 8.4에서 V1 적용 검증).

### B. 프론트엔드 (순서)
1. `frontend/` = Vite react-ts. React/ReactDOM 19, react-router-dom, Tailwind v4 + `@tailwindcss/vite`. **TipTap은 M4에서** 설치(지금 import 금지).
2. Tailwind v4: `@import "tailwindcss";` + `@theme` 토큰. 디자인 토큰(§6): bg `#02020b/#050512/#070817/#030712`, cyan `#22d3ee/#67e8f9/#38bdf8`, purple `#a855f7/#a78bfa`, danger `#fb7185`, gold `#fde047`, 하드 섀도우(card/button/search/navbar/glow), radius≈0. 폰트 `Press Start 2P`(heading)+`IBM Plex Sans KR`(본문).
3. 구조: `routes/ pages/ features/ components/layout components/ui lib/ styles/ types/` + `frontend/src/AGENTS.md`.
4. 라우터: router.tsx + ProtectedRoute/GuestOnlyRoute/AdminRoute/SetupGuard. 라우트 15종(§7.1). 페이지 stub(가짜 성공 응답 금지, **UNIVERSE 라벨은 항상 "친구"**).
5. Layout: AppShell/TopBar(64px)/SideNav(§6.6 노출 규칙). UI stub: Badge/PostCard/Button(primary/neutral/danger/gold)/Table/Tabs/Modal/Pagination/TagInput/FormField.
6. `lib/apiClient.ts`(VITE_API_BASE_URL, credentials:'include', Bearer, 401 refresh single-flight+1회 재시도, 응답 unwrap) + `lib/authContext.tsx`(인터페이스만). `.env.example`(VITE_API_BASE_URL=http://localhost:8080). vitest 라우터/AppShell smoke.

### D. 완료 기준(DoD)
`./gradlew build` 통과 · 앱 부팅+Flyway 적용 · `/swagger-ui.html` 노출 · smoke+Flyway 테스트 통과 · `frontend npm run build` 통과 · `npm run dev` 기동+주요 route/AppShell 정상 · PR에 워크로그 [개발 기록] 포함.

## [개발 기록] (Claude/Sonnet)

### 2026-07-01 · 프론트엔드 스캐폴딩 (executor#FE)
- `frontend/` Vite+React 19+TS+Tailwind v4 초기화, 31개 파일. react-router-dom 7.
- 진입점(main/App), `routes/router.tsx`(14 route)+가드 4종(Protected/GuestOnly/Admin/Setup).
- Layout: AppShell/TopBar(64px)/SideNav(활성 강조). 14개 페이지 stub(레이아웃만, **가짜 API 응답 없음**, UNIVERSE→"친구").
- `lib/apiClient.ts`(Bearer, 401 refresh single-flight+1회 재시도, 응답 unwrap), `lib/authContext.tsx`(골격), `.env.example`.
- `styles/index.css`: §6 디자인 토큰(색/폰트 Press Start 2P·IBM Plex Sans KR/하드 섀도우 blur0) 반영. `frontend/src/AGENTS.md`.
- 검증: `npm run build` ✓(dist 생성), `npm run dev` 기동 ✓(localhost:5173), TS 컴파일 ✓.
- **갭 → 팔로업**: `components/ui/` stub(Badge/Button variants/Table/Tabs/Modal/Pagination/TagInput/FormField)과 vitest smoke가 미생성이라 FE executor 재개해 보완 지시(2026-07-01).
- 경고(무시 가능): Tailwind v4 `@import` 순서.

### 2026-07-01 · 백엔드 스캐폴딩 (executor#BE)
- Gradle **8.10** wrapper + `build.gradle`(Spring Boot 3.5.15, web/data-jpa/security/validation/flyway(+mysql)/querydsl 5.1 jakarta/springdoc 2.8.17/testcontainers, Lombok 제한). 20개 파일.
- `ZeroverseServerApplication`(@EnableJpaAuditing), `common/response`(ApiResponse/PageResponse/ErrorResponse), `common/exception`(ErrorCode/BusinessException/GlobalExceptionHandler), `common/entity`(BaseEntity/BaseSoftDeleteEntity).
- `config`: SecurityConfig(stateless/CORS/CSRF off/permitAll), CorsConfig, OpenApiConfig, QuerydslConfig, S3Properties.
- `application.yml` + `application-local.example.yml`(LocalStack 예시) + `src/test/resources/application-test.yml`. `db/migration/V1__init.sql`(전체 테이블 users/blogs/categories/posts/post_images/tags/post_tags/universes/comments/post_likes/notifications/refresh_tokens, **category type=DEFAULT/GENERAL/LOCKED**, unique/FK 포함).
- 테스트: `ZeroverseServerApplicationTests`(@SpringBootTest), `FlywayMigrationTest` — Testcontainers MySQL 8.4.
- 검증: `./gradlew build` 컴파일+JAR 생성 ✓(exit 0). **⚠️ Testcontainers 테스트는 Docker 데몬 미기동으로 스킵됨 → V1 스키마의 MySQL 8.4 실제 적용은 아직 미검증.**

### 2026-07-01 · 프론트 UI stub + vitest 보완 (executor#FE 재개)
- `components/ui/`: Badge(**UNIVERSE→"친구"**)/Button(primary/neutral/danger/gold)/Table/Tabs/Modal/Pagination/TagInput/FormField + barrel `index.ts`. props 타입 정의 + §6 토큰 스타일.
- vitest 설정(`vitest.config.ts`, `src/test/setup.ts`) + smoke(router, MainPage). `npx vitest run` **4 테스트 통과** ✓, `npm run build` ✓. → **프론트 M0 완료.**

### 2026-07-01 · 백엔드 스키마 검증 (Docker)
- Docker Desktop 기동 후 `./gradlew test`(Testcontainers MySQL 8.4). **1차 실행에서 V1 문법 오류 발견**([이슈·결정] 참조) → BE executor가 `parent_key BIGINT AS (COALESCE(parent_id,0)) STORED` generated column으로 수정.
- **재검증: 5/5 테스트 통과, BUILD SUCCESSFUL.** Flyway "Successfully applied 1 migration to schema zeroverse_test, v1". 12개 테이블 생성·unique 제약·category type 검증 통과. → **백엔드 M0 완료.**

## [이슈·결정]

- **2026-07-01 MySQL 버전**: 스펙 8.x vs 로컬 9.6 → 테스트는 Testcontainers MySQL **8.4** 기준, 로컬 개발은 9.6 허용(9.x 전용 SQL 금지). 8.4에서 실패 시 M0 실패로 간주. (Codex 권장 채택)
- **2026-07-01 Lombok**: 제한 채택(`@Data` 금지, `@Getter`/`@NoArgsConstructor(PROTECTED)` 중심).
- **2026-07-01 M0 엔티티 범위**: 도메인 `@Entity`는 M0에서 만들지 않음 — Flyway 전체 스키마 + BaseEntity/BaseSoftDeleteEntity까지만. User/Blog/... 엔티티는 각 도메인 마일스톤에서 기능·테스트와 함께 추가. (Codex 권장 채택)
- **2026-07-01 테스트 DB**: H2 비권장(MySQL 동작 차이) → Testcontainers MySQL 8.4 기본, Docker 불가 시 로컬 MySQL profile 보조.
- **2026-07-01 V1 스키마 버그(검증으로 발견)**: `categories`의 `UNIQUE KEY (blog_id, COALESCE(parent_id,0), name)`처럼 유니크 컬럼 목록에 `COALESCE()` 표현식을 직접 사용 → MySQL 8.4 문법 오류(`SQLSyntaxErrorException`)로 Flyway 실패, 5개 테스트 전부 컨텍스트 로드 실패. `./gradlew build`는 통과했으나 실제 스키마 적용에서만 드러남 → **Testcontainers 검증의 가치 확인.** 수정: 계획대로 `parent_key BIGINT AS (COALESCE(parent_id,0)) STORED` generated column 추가 후 두 UNIQUE KEY에 `parent_key` 사용. BE executor 재개해 수정+`./gradlew test` 재검증.

## [리뷰] (Codex)

## [머지]
