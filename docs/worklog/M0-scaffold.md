# M0 — 프로젝트 스캐폴딩

- **작성 시각**: 2026-07-25 KST
- **브랜치**: `feature/M0-scaffold` (base: `dev`)
- **범위**: 백엔드 빌드·공통 API 계층·Flyway `V1__init.sql`(MVP 전체 테이블) + 프론트엔드 Vite/React/Tailwind v4 골격·라우트·AppShell. PRD §10 M0 정의 기준.
- **제외**: 도메인 엔티티/API 구현(M1~M9), JWT·AuthContext·라우트 가드(M1), TipTap·S3·LocalStack(M4), Docker Compose 파일.
- **기준 문서**: `AGENTS.md`(소스 오브 트루스 우선순위·개발 프로세스·워크로그 규약), `docs/PRD.md` §2·§3·§4·§6·§7.1·§8·§10 M0·§11·§12·§13.1, `docs/REQUIREMENTS.md` §2·§4·NFR-04/06/07/09, `docs/design/DESIGN-SYSTEM.md`, `docs/governance/README.md` §2.
- **저장소 상태**: 초기화 후 재시작 단계. `feature/M0-scaffold` 워킹트리에 소스 코드 없음(커밋은 baseline 문서 4개). 초기화 이전 구현은 `origin/feature/M4a-post-backend`에 읽기 전용 참고 자산으로 존재.

## [계획] (Codex · 2026-07-25)

- **브랜치**: `feature/M0-scaffold` (base: `dev`)
- **목표**: 이후 모든 마일스톤이 사용할 빌드·DB·공통 API·프론트 디자인 기반을 구성하되, 도메인 기능은 구현하지 않는다. M0→M1은 모든 후속 작업의 선행 조건이다. (`docs/PRD.md §10 M0`, `docs/PRD.md §10 의존성 요약`)
- **완료 원칙**: placeholder/`skip`/조건부 테스트 통과를 허용하지 않고, 백엔드·프론트 빌드와 M0 범위 테스트를 모두 실제로 통과시킨다. (`docs/PRD.md §11`, `docs/PRD.md §12`)

### 0. 초기화 이전 자산 재사용 권고

**권고: 이전 M0 커밋을 cherry-pick하지 않고, 검증된 백엔드·SQL·테스트만 파일별로 선별 이식한다. 프론트엔드는 현재 디자인 정본으로 다시 작성한다.**

| 자산 | 처리 | 근거 |
|---|---|---|
| Gradle wrapper, `settings.gradle`, 기본 의존성 좌표 | 선별 재사용 | Gradle 8.10·Spring Boot 3.5.15·Java 21 조합이 과거 M0에서 실제 빌드되었다. 단, 불필요한 Node Gradle 플러그인과 Lombok은 제거한다. (`origin/feature/M4a-post-backend:docs/worklog/M0-scaffold.md [개발 기록]`, `[이슈·결정]`) |
| `ApiResponse`, `PageResponse`, `BaseEntity`, 공통 예외 구조 | 선별 재사용 후 재검토 | 과거 리뷰에서 null 필드 누락, 보안 기본값, 401/403 응답 등의 결함이 발견·수정되었으므로 초기 버전이 아니라 최종 수정 취지만 재사용해야 한다. (`origin/feature/M4a-post-backend:docs/worklog/M0-scaffold.md [리뷰]`) |
| `V1__init.sql`, `FlywayMigrationTest` | 우선 참고·선별 이식 | MySQL 8.4에서 generated column 문법 문제까지 찾아 수정한 검증 자산이다. 현재 `DEFAULT/GENERAL/LOCKED`와 `is_default` 생략 정책을 다시 대조한 뒤 사용한다. (`origin/feature/M4a-post-backend:docs/worklog/M0-scaffold.md [이슈·결정]`, `docs/PRD.md §3.2~§3.5`) |
| 기존 `application.yml` | 구조만 참고 | JWT·AWS 설정은 각각 M1·M4 범위이며 M0에 미리 넣지 않는다. 실제 비밀값도 커밋하지 않는다. (`docs/PRD.md §10 M1`, `docs/PRD.md §10 M4`, `docs/PRD.md §12.5`, `docs/PRD.md §13.1`) |
| 기존 프론트엔드 CSS·UI | 코드 재사용 금지, 구조만 참고 | 이전 M0는 폐기된 다크 네온·골드 팔레트를 사용한다. 현재 정본은 크림 페이퍼 팔레트, `border-radius:0`, 새 AppShell 노출 규칙이다. (`docs/design/DESIGN-SYSTEM.md §1~§3`, `docs/PRD.md §6`, `docs/PRD.md §9.0`) |
| 기존 AuthContext, API client, 라우트 가드, 도메인 페이지 구현 | M0 재사용 제외 | AuthContext·401 갱신·라우트 가드는 M1, 실제 도메인 화면/API 연동은 M2 이후 범위다. (`docs/PRD.md §8`, `docs/PRD.md §10 M1~M9`) |

---

### 1. Scope Lock

| 구분 | M0 포함 | M0 제외 | 근거 |
|---|---|---|---|
| 백엔드 프로젝트 | Gradle wrapper, Spring Boot 3.x, Java 21 toolchain, `com.zeroverse` 기본 패키지와 공통 계층 | 인증·게시글 등 도메인 API 구현 | `docs/PRD.md §2.1~§2.2`, `docs/PRD.md §10 M0~M1` |
| Java 엔티티 | `BaseEntity`, `BaseSoftDeleteEntity`만 구현 | `User`, `Blog`, `Post`, `Category` 등 도메인 `@Entity`·Repository·Service·Controller 전체 | 도메인 기능은 M1~M9에서 규칙·테스트와 함께 구현한다. M0 정의에는 BaseEntity만 명시된다. (`docs/PRD.md §2.2`, `docs/PRD.md §10`) |
| DB 마이그레이션 | `V1__init.sql`에 MVP 전체 12개 테이블·FK·인덱스·제약 생성 | 데이터 seed, 도메인별 후속 변경, 운영 데이터 | 첫 Flyway 마이그레이션은 전체 MVP 테이블이어야 한다. (`docs/PRD.md §3.4`, `docs/REQUIREMENTS.md NFR-07`) |
| 카테고리 스키마 | `type=DEFAULT/GENERAL/LOCKED`; `type=DEFAULT`를 기본 카테고리 표현으로 사용 | `SERIES`, category slug, 별도 `is_default` 컬럼 | PRD 결정이 이전 REQUIREMENTS 모델을 override하며 `is_default` 생략을 권장한다. (`docs/PRD.md §3.2`, `docs/PRD.md §9-H`, `docs/PRD.md §13 확정사항`) |
| 공통 API | 성공/실패 래퍼, 페이지 래퍼, ErrorCode, BusinessException, 글로벌 예외 처리, SpringDoc | 실제 공개 도메인 엔드포인트 | `docs/PRD.md §4.1~§4.4`, `docs/PRD.md §10 M0` |
| Security/CORS | CORS와 Swagger 접근을 위한 최소 SecurityFilterChain; 아직 보호할 API가 없으므로 기본 permit-all | JWT 발급·검증, Refresh rotation, 사용자 인증, 최종 접근제어 규칙 | 전체 인증 구현은 M1이다. (`docs/PRD.md §4.3`, `docs/PRD.md §10 M1`) |
| 프론트엔드 | Vite/React/TS/Tailwind 골격, 15개 라우트, TopBar·조건부 SideNav, 공용 UI 기초 | AuthContext, API client 자동 갱신, 라우트 가드, 실제 API 데이터, 완성 화면 | `docs/PRD.md §2.3`, `docs/PRD.md §7.1`, `docs/PRD.md §8`, `docs/PRD.md §10 M0~M1` |
| 에디터·이미지 | 라우트와 일반 레이아웃 자리만 구성 | TipTap, S3 SDK, presigned URL, 이미지 업로드 | M4 범위다. (`docs/PRD.md §10 M4`, `docs/PRD.md §13.1`) |
| 로컬 DB | Testcontainers MySQL 8.4를 자동 테스트 정본으로 사용; 로컬 MySQL용 example profile 제공 | 추적되는 실제 `application-local.yml` | MySQL 8.x와 비밀 미커밋 조건을 지킨다. (`docs/REQUIREMENTS.md §2`, `docs/PRD.md §12.5`) |
| Docker/LocalStack | Testcontainers 실행에 Docker Engine 필요 | MySQL Docker Compose 및 LocalStack Compose 파일은 M0에서 만들지 않음 | LocalStack은 M4 이미지 업로드 검증용이다. (`docs/PRD.md §10 M4`, `docs/PRD.md §13.1`) |

**경계 결론**

1. M0에서는 도메인 엔티티를 만들지 않고 공통 엔티티 기반만 만든다.
2. Java 엔티티가 없어도 `V1__init.sql`은 전체 MVP 테이블을 생성한다.
3. Docker는 Testcontainers 실행 수단으로 필요하지만, 별도 Compose와 LocalStack은 M0 산출물이 아니다.
4. `application-local.yml`은 사용자가 로컬에서만 만들며 Git에 넣지 않는다.

---

### 2. 백엔드 작업 목록

#### 2.1 빌드 설정과 정확한 의존성

과거 MySQL 8.4 검증 조합을 기준선으로 사용한다. (`origin/feature/M4a-post-backend:docs/worklog/M0-scaffold.md [계획]`, `[개발 기록]`)

- `settings.gradle`
  - `rootProject.name = 'zeroverse-server'`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
  - Gradle `8.10`
- `build.gradle`
  - Java toolchain 21
  - Spring Boot `3.5.15`
  - dependency-management `1.1.6`
  - JUnit Platform 활성화

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.5.15'
    id 'io.spring.dependency-management' version '1.1.6'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    runtimeOnly 'com.mysql:mysql-connector-j'

    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17'

    implementation 'com.querydsl:querydsl-jpa:5.1.0:jakarta'
    annotationProcessor 'com.querydsl:querydsl-apt:5.1.0:jakarta'
    annotationProcessor 'jakarta.annotation:jakarta.annotation-api'
    annotationProcessor 'jakarta.persistence:jakarta.persistence-api'

    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:mysql'
}
```

Spring Security, JPA, QueryDSL, MySQL, Flyway, Validation과 SpringDoc은 확정 스택이다. (`docs/REQUIREMENTS.md §2`, `docs/PRD.md §2.1`)

M0에서는 다음을 추가하지 않는다.

- Lombok: 명세에 없고 필수성이 없다.
- JJWT: JWT 구현은 M1이다.
- AWS SDK/LocalStack client: M4다.
- jsoup/TipTap: 콘텐츠 처리와 에디터는 M4다.
- Node Gradle plugin: 프론트는 `frontend/package.json`과 npm으로 독립 검증한다.

#### 2.2 파일 단위 구현

| 파일 | 작업 |
|---|---|
| `src/main/java/com/zeroverse/ZeroverseServerApplication.java` | `@SpringBootApplication`, `@EnableJpaAuditing`. Base package를 `com.zeroverse`로 고정한다. (`docs/PRD.md §2.2`, `docs/REQUIREMENTS.md NFR-06`) |
| `src/main/java/com/zeroverse/AGENTS.md` | 백엔드 패키지·레이어·테스트 규칙을 루트 지침에 맞춰 기록한다. (`AGENTS.md Working In This Directory`) |
| `common/entity/BaseEntity.java` | `@MappedSuperclass`, `@EntityListeners(AuditingEntityListener.class)`, `createdAt`, `updatedAt`; Java camelCase/DB snake_case, 숫자 필드는 래퍼 타입 원칙을 유지한다. (`docs/PRD.md §3.3`, `docs/REQUIREMENTS.md NFR-06`) |
| `common/entity/BaseSoftDeleteEntity.java` | `BaseEntity` 상속 + nullable `deletedAt`. 자동 필터는 도메인별 Repository가 생기는 시점에 구현한다. (`docs/PRD.md §2.2`, `docs/PRD.md §3.2`) |
| `common/response/ApiResponse.java` | `success`, `data`, `error`, `timestamp` 네 키를 성공/실패 모두 직렬화한다. null 필드를 제거하는 `NON_NULL`은 사용하지 않는다. timestamp는 `Instant` 기반 UTC ISO-8601이다. (`docs/PRD.md §4.1`, `docs/REQUIREMENTS.md §5 공통 성공/실패 응답`) |
| `common/response/ErrorResponse.java` | `code`, `message`, `details[{field,reason}]`. |
| `common/response/PageResponse.java` | `items/page/size/totalElements/totalPages/hasNext/hasPrevious`, `Page<T>` 변환 팩토리. (`docs/PRD.md §4.1~§4.2`) |
| `common/exception/ErrorCode.java` | NFR-04에 명시된 AUTH/USER/BLOG/POST/CAT/UNI/COM/LIKE/NOT/ADMIN/UPLOAD 코드와 HTTP status를 중앙화한다. 문서에 없는 공통 validation/404/500 코드는 심의 결정 전 발명하지 않는다. (`docs/REQUIREMENTS.md NFR-04`) |
| `common/exception/BusinessException.java` | `ErrorCode`를 보유하는 런타임 예외. |
| `common/exception/GlobalExceptionHandler.java` | BusinessException과 Bean Validation을 공통 실패 래퍼로 변환한다. 400/401/403/404/409 구분을 유지하되, 비도메인 코드 이름은 심의 결과를 적용한다. (`docs/PRD.md §4.4`, `docs/REQUIREMENTS.md NFR-03~04`) |
| `config/CorsProperties.java` | 허용 origin을 환경설정으로 바인딩한다. 기본 개발 origin은 `http://localhost:5173`, 운영은 명시적 주입만 허용한다. (`docs/PRD.md §4.3`, `docs/REQUIREMENTS.md NFR-01`) |
| `config/SecurityConfig.java` | CORS 적용, CSRF/form login/httpBasic 비활성화, stateless, M0에서는 `permitAll`. JWT 필터와 최종 401/403 정책은 M1에서 추가한다. |
| `config/OpenApiConfig.java` | API title/version과 Bearer 스키마 정의 기반을 만든다. Swagger UI `/swagger-ui.html`. (`docs/REQUIREMENTS.md NFR-05`) |
| `src/main/resources/application.yml` | `ddl-auto=validate`, `open-in-view=false`, Flyway 활성화, SpringDoc 경로, datasource·CORS 환경변수 정의. JWT·AWS 설정은 넣지 않는다. |
| `src/main/resources/application-local.example.yml` | 로컬 MySQL 8.x URL/사용자명 형식만 제공한다. 실제 비밀번호 파일은 생성·추적하지 않는다. |
| `src/main/resources/db/migration/V1__init.sql` | 전체 테이블·제약 생성. 상세는 아래 스키마 항목을 따른다. |
| `src/test/resources/application-test.yml` | 테스트용 비밀이 아닌 고정 설정만 둔다. datasource URL은 Testcontainers에서 동적으로 주입한다. |

#### 2.3 패키지 골격

빈 패키지 디렉터리는 Git에 남지 않으므로, M0에서는 실제 파일이 있는 공통 패키지만 생성한다. 도메인 패키지는 각 마일스톤에서 생성한다.

```text
src/main/java/com/zeroverse/
├─ ZeroverseServerApplication.java
├─ AGENTS.md
├─ common/
│  ├─ entity/
│  ├─ exception/
│  └─ response/
└─ config/
```

최종 목표 구조는 `common/config/security/domain`과 도메인별 controller→service→repository 계층이다. (`docs/PRD.md §2.2`)

#### 2.4 `V1__init.sql`

다음 12개 테이블을 FK 의존 순서대로 생성한다.

1. `users`
2. `blogs`
3. `categories`
4. `posts`
5. `post_images`
6. `tags`
7. `post_tags`
8. `universes`
9. `comments`
10. `post_likes`
11. `notifications`
12. `refresh_tokens`

주요 규칙:

- 테이블·컬럼은 snake_case, InnoDB, `utf8mb4`. (`docs/REQUIREMENTS.md NFR-06`)
- `likes` 같은 예약어 충돌을 피하기 위해 테이블명은 `post_likes`로 정한다.
- 모든 필드는 `docs/REQUIREMENTS.md §4`를 반영하되, category는 `is_default` 없이 `type`으로 통일한다. (`docs/PRD.md §3.2`)
- enum 성격 컬럼은 `VARCHAR` + MySQL `CHECK`로 허용값을 제한한다. 특히 category type은 정확히 `DEFAULT`, `GENERAL`, `LOCKED`만 허용한다. (`docs/PRD.md §3.4`)
- category의 같은 blog/parent 아래 name·display_order 중복은 `COALESCE(parent_id,0)` generated column을 이용해 unique key로 보장한다. 이 방식은 과거 MySQL 8.4 검증에서 직접 표현식 unique 문법 오류를 해결했다. (`origin/feature/M4a-post-backend:docs/worklog/M0-scaffold.md [이슈·결정]`)
- 블로그당 `DEFAULT` 1개, 자기 자신 Universe 금지, 카테고리/댓글 최대 깊이 같은 애플리케이션 규칙은 후속 서비스에서 보장한다. M0 SQL이 지원하지 않는 규칙을 trigger로 새로 구현하지 않는다. (`docs/REQUIREMENTS.md NFR-08`, `docs/PRD.md §3.5`)
- unique/FK/not-null은 `docs/REQUIREMENTS.md NFR-08` 전체를 반영한다.

#### 2.5 로컬 DB 방식

- **자동 테스트 정본**: Testcontainers `mysql:8.4`.
- **애플리케이션 수동 실행**: 사용자가 가진 로컬 MySQL 8.x와 `application-local.yml`.
- **M0 기본값**: MySQL Docker Compose는 추가하지 않는다. Testcontainers가 격리·재현 가능한 스키마 검증을 담당한다.
- **현재 로컬 MySQL 9.6은 수용 테스트 기준으로 사용하지 않는다**. 명세가 MySQL 8.x이므로 8.4 컨테이너 통과를 필수로 한다. (`docs/REQUIREMENTS.md §2`, `docs/REQUIREMENTS.md NFR-07`)

---

### 3. 프론트엔드 작업 목록

#### 3.1 프로젝트와 패키지

`frontend/`를 Vite React TypeScript 템플릿으로 생성하고 `package-lock.json`을 커밋한다. (`docs/PRD.md §2.1`, `docs/REQUIREMENTS.md §8.1`)

- 런타임: `react`, `react-dom`, `react-router-dom`
- 스타일: `tailwindcss`, `@tailwindcss/vite`
- 테스트: `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`
- 빌드/품질: `typescript`, `vite`, `@vitejs/plugin-react`, ESLint 또는 생성 템플릿의 동일 목적 lint 설정
- 제외: TipTap, TanStack Query, AuthContext/API client 관련 추가 라이브러리

과거 브랜치의 React 19·Tailwind 4 조합은 참고하되 새 `package-lock.json`으로 설치 결과를 고정한다. (`origin/feature/M4a-post-backend:frontend/package.json`, `docs/PRD.md §2.1`)

#### 3.2 파일 구조

```text
frontend/src/
├─ main.tsx
├─ App.tsx
├─ AGENTS.md
├─ routes/router.tsx
├─ pages/
├─ components/
│  ├─ layout/AppShell.tsx
│  ├─ layout/TopBar.tsx
│  ├─ layout/SideNav.tsx
│  └─ ui/
├─ features/
├─ lib/
├─ styles/index.css
└─ types/
```

`frontend/src/AGENTS.md`에는 디자인 정본 우선순위, `min-width:1440px`, 한글에 Press Start 2P 금지, `border-radius:0` 규칙을 기록한다. (`AGENTS.md Working In This Directory`, `docs/design/DESIGN-SYSTEM.md §1~§6`)

#### 3.3 TailwindCSS v4와 디자인 토큰 주입

- `vite.config.ts`에 `@tailwindcss/vite` 플러그인을 등록한다.
- `styles/index.css`에서 `@import "tailwindcss";`를 사용한다.
- Tailwind 유틸리티로 사용할 색·폰트·그림자는 `@theme`에 선언한다.
- gradient와 컴포넌트 조합값은 `:root` CSS 변수로 선언한다.
- Google Fonts는 CSS import 순서 경고를 피하도록 `index.html`의 `<link>`로 로드한다. (`docs/design/DESIGN-SYSTEM.md §4`)

```css
@import "tailwindcss";

@theme {
  --color-paper: #f6ead8;
  --color-ink: #2b1b3d;
  --color-accent: #e85d75;
  --color-accent-strong: #c73a55;
  --color-shadow: #d8c7b0;

  --color-surface: #ffffff;
  --color-surface-warm: #fff8ec;
  --color-surface-raise: #ffe9c9;
  --color-surface-soft: #fff3dd;
  --color-surface-hover: #fffdf7;
  --color-surface-inert: #f1ece2;
  --color-line: #eadbc4;

  --color-text-prose: #3d2f52;
  --color-text-body: #5c4a72;
  --color-text-nav: #6b5a80;
  --color-text-muted: #9b8aa8;
  --color-text-on-ink: #ffd9a0;

  --font-pixel: "Press Start 2P", monospace;
  --font-sans: "IBM Plex Sans KR", sans-serif;

  --shadow-sm: 3px 3px 0 #d8c7b0;
  --shadow-btn: 6px 6px 0 #d8c7b0;
  --shadow-card: 7px 7px 0 #d8c7b0;
  --shadow-panel: 8px 8px 0 #d8c7b0;
}

:root {
  --gradient-dusk:
    linear-gradient(180deg, #241b4d 0%, #4b2a7b 50%, #c86bb1 85%, #ff9d6c 100%);
}

html,
body,
#root {
  min-width: 1440px;
  min-height: 100%;
}

*,
*::before,
*::after {
  border-radius: 0;
}
```

나머지 semantic 색, shadow-on-dark, selection, 픽셀 모션까지 `docs/design/DESIGN-SYSTEM.md §2~§5`의 정확한 값을 모두 주입한다. 폐기된 네온·골드 토큰은 넣지 않는다. (`docs/PRD.md §6`, `docs/design/DESIGN-SYSTEM.md §1~§5`)

#### 3.4 라우터 골격

다음 15개 경로와 페이지 컴포넌트를 만든다. 페이지는 제목·레이아웃·빈 상태만 렌더하며 샘플 사용자나 가짜 API 데이터를 넣지 않는다. (`docs/PRD.md §7.1`)

- `/`
- `/signin`
- `/signup`
- `/blog/setup`
- `/blog/:blogSlug`
- `/blog/:blogSlug/:postId`
- `/write`
- `/edit/:postId`
- `/settings`
- `/settings/universe`
- `/settings/posts`
- `/search`
- `/notifications`
- `/admin`
- `/admin/users/:userId`

Protected/Admin/Guest/Setup 가드는 M1에서 구현한다. M0 라우터는 경로와 레이아웃 경계만 확정한다. (`docs/PRD.md §8.3`, `docs/PRD.md §10 M1`)

#### 3.5 AppShell과 공용 컴포넌트

- `TopBar`: 전 화면 공통, 높이 64px, 좌 로고·중앙 520px 검색·우 글쓰기/알림/프로필. 관리자 버튼 없음. (`docs/design/DESIGN-SYSTEM.md §6.1`)
- `AppShell`: TopBar와 페이지 콘텐츠를 조합하고 optional sidebar/right-panel slot을 제공한다.
- `SideNav`: `/`에서만 렌더한다. 블로그와 설정의 240px 패널은 이후 별도 화면 전용 컴포넌트로 만든다. (`docs/PRD.md §6.7`, `docs/design/DESIGN-SYSTEM.md §6.2`)
- `/signin`, `/signup`, `/blog/setup`: TopBar 아래 다크 온보딩 레이아웃이며 SideNav가 없다.
- 공용 UI 기초:
  - `Button`
  - `Tab`
  - `Badge`
  - `TagChip`
  - `Panel`
  - `PostCard`
  - `FormField`
  - `ListRow`
  - `Pagination`
  - `Avatar`
  - `Prose`
  - `Table`
  - `Modal`
  - `TagInput`

각 컴포넌트는 실제 variant·disabled·children API와 디자인 토큰을 구현하며 빈 파일이나 TODO 컴포넌트로 두지 않는다. 화면별 비즈니스 동작은 후속 마일스톤으로 남긴다. (`docs/PRD.md §2.3`, `docs/PRD.md §6.6`, `docs/design/DESIGN-SYSTEM.md §7`)

---

### 4. 테스트 계획

#### 4.1 백엔드

| 테스트 파일 | 실제 검증 내용 | 근거 |
|---|---|---|
| `ZeroverseServerApplicationTests` | Testcontainers MySQL 8.4를 연결한 `@SpringBootTest` 컨텍스트 로드, Flyway 적용 성공 | `docs/PRD.md §11~§12`, `docs/REQUIREMENTS.md NFR-07` |
| `FlywayMigrationTest` | 12개 테이블, FK, 주요 not-null/unique, category type CHECK, generated parent key와 중복 제약을 JDBC metadata 및 실제 insert로 검증 | `docs/PRD.md §3.4~§3.5`, `docs/REQUIREMENTS.md NFR-08~09` |
| `BaseEntityAuditingTest` | 테스트 전용 concrete entity를 저장해 `createdAt`·`updatedAt` 자동 기록을 검증 | `docs/REQUIREMENTS.md NFR-06`, `docs/REQUIREMENTS.md NFR-09` |
| `ApiResponseSerializationTest` | 성공 시 `error:null`, 실패 시 `data:null` 포함, 네 키 존재, timestamp UTC ISO-8601 | `docs/REQUIREMENTS.md §5`, `docs/PRD.md §4.1` |
| `PageResponseTest` | Page 변환 시 7개 필드와 next/previous 계산 | `docs/PRD.md §4.1~§4.2` |
| `GlobalExceptionHandlerTest` | BusinessException의 status/code/shape, Bean Validation 400와 field details; 공통 코드명은 심의 결정 반영 | `docs/REQUIREMENTS.md NFR-03~04` |
| `CorsConfigTest` | 허용된 `http://localhost:5173` preflight 성공, 미허용 origin 차단 | `docs/REQUIREMENTS.md NFR-01` |
| `OpenApiConfigTest` | `/v3/api-docs`와 `/swagger-ui.html` 접근 가능 | `docs/REQUIREMENTS.md NFR-05` |

Testcontainers는 Docker가 없으면 `skip`하지 않고 실패시킨다. 검증 전에 Docker 가용성을 별도 확인한다. (`docs/PRD.md §12.2`)

아직 작성하지 않는 테스트:

- 도메인 Repository/연관관계/soft-delete 필터
- JWT·401·403 접근제어
- 회원가입·블로그·게시글 통합 시나리오

해당 구현이 M1 이후이므로 지금 작성하면 가짜 구현이나 과도한 선행 구현이 된다. (`docs/PRD.md §10~§11`)

#### 4.2 프론트엔드

| 테스트 | 검증 내용 |
|---|---|
| `router.test.tsx` | 15개 URL이 대응 페이지를 렌더하고 unknown route가 명시적 not-found 화면을 렌더 |
| `AppShell.test.tsx` | TopBar 공통 렌더, `/`에서만 SideNav 렌더, 온보딩 경로에는 SideNav 없음 |
| `TopBar.test.tsx` | 로고·검색·글쓰기·알림·프로필 존재, Admin 항목 부재 |
| `Button.test.tsx` | variant, disabled, 클릭 동작 |
| `Badge.test.tsx` | `UNIVERSE` 입력의 화면 라벨이 `친구`인지 검증 |
| `Pagination.test.tsx` | 현재 페이지·이전/다음 disabled·콜백 |
| `PostCard.test.tsx` | 전달된 제목·메타·태그·통계만 렌더하며 샘플 데이터를 내부 생성하지 않음 |
| 렌더 스모크 | 모든 공용 컴포넌트와 대표 페이지가 console error 없이 렌더 |

Vitest + RTL + jsdom을 사용하고 `skip`, snapshot만으로 끝나는 테스트, 가짜 성공 응답은 금지한다. (`docs/PRD.md §11~§12`)

CSS 픽셀 단위 일치는 jsdom으로 보증할 수 없으므로, 개발 서버에서 `/`, `/signin`, `/settings` 대표 경로를 실제 브라우저로 열어 1440px 기준으로 정본과 육안 대조한다. (`docs/PRD.md §12.4`, `docs/design/DESIGN-SYSTEM.md §6~§8`)

---

### 5. 작업 순서와 검증 명령

1. **기획 심의 완료**
   - 공통 비도메인 에러코드와 M0 기준선을 확정한다.
   - `MEDIUM/HIGH/BLOCKED`이면 사용자 승인 전 영향 작업을 시작하지 않는다. (`docs/governance/README.md §3~§5`)

2. **도구 사전 점검**
   - `java -version`
   - `node --version`
   - `npm.cmd --version`
   - `docker version`
   - Windows PowerShell에서는 실행 정책 문제를 피하려고 `npm` 대신 `npm.cmd`를 사용한다.

3. **Gradle·Spring Boot 스캐폴딩**
   - wrapper, settings, build 파일
   - main class, application 설정
   - 검증: `.\gradlew.bat compileJava`

4. **Flyway 전체 스키마와 DB 테스트 우선 작성**
   - `V1__init.sql`
   - Testcontainers/마이그레이션 테스트
   - 검증: `.\gradlew.bat test --tests "*FlywayMigrationTest"`

5. **공통 응답·예외·Auditing·CORS·SpringDoc**
   - 단위/슬라이스 테스트와 함께 구현
   - 검증: `.\gradlew.bat test`

6. **백엔드 전체 검증**
   - `.\gradlew.bat clean build`
   - 로컬 DB 정보가 준비된 경우:
     - `.\gradlew.bat bootRun --args="--spring.profiles.active=local"`
     - `Invoke-WebRequest http://localhost:8080/swagger-ui.html`

7. **Vite 프론트 스캐폴딩과 토큰**
   - 최초 설치: `npm.cmd install`
   - 이후 재현 검증: `npm.cmd ci`
   - 라우터 → AppShell → 공용 UI 순서 구현

8. **프론트 테스트·빌드**
   - `npm.cmd run test`
   - `npm.cmd run lint`
   - `npm.cmd run build`
   - `npm.cmd run dev -- --host 127.0.0.1`

9. **최종 M0 검증**
   - `.\gradlew.bat clean build`
   - `npm.cmd ci`
   - `npm.cmd run test`
   - `npm.cmd run lint`
   - `npm.cmd run build`
   - 대표 화면 브라우저 시각 대조
   - 워크로그 `[개발 기록]`과 실제 diff 일치 확인. (`AGENTS.md 개발 프로세스`, `docs/PRD.md §12`)

---

### 6. 위험과 미확정 사항

#### 사용자 결정·조치가 필요한 사항

| 항목 | 현재 확인 | 필요한 결정/조치 | 영향 |
|---|---|---|---|
| 비도메인 공통 에러코드 | NFR-04에는 도메인 prefix만 있고 Bean Validation·미매핑 경로·500 코드가 없다. 과거 구현은 문서에 없는 `VALIDATION_001`, `404`, `INTERNAL_SERVER_ERROR`를 사용했다. (`docs/REQUIREMENTS.md NFR-03~04`) | 기획 심의와 사용자 승인으로 prefix/코드 정책 확정 | `GlobalExceptionHandler`의 해당 매핑 및 공개 API 테스트만 보류 |
| Docker | Docker client 29.3.1은 있으나 2026-07-25 점검 시 daemon이 실행 중이 아니었다. | Testcontainers 검증 전에 Docker Desktop 실행 및 엔진 접근 허용 | Flyway·Auditing 통합 테스트 실행 불가 |
| 로컬 MySQL 정보 | 설치된 CLI는 MySQL 9.6이고 서버 버전·DB명·계정·비밀번호는 확인되지 않았다. | 애플리케이션 수동 부팅을 원할 때 MySQL 8.x 접속 URL, 사용자명, 로컬 비밀번호 제공 | 자동 M0 테스트는 Testcontainers로 진행 가능하므로 구현 자체는 비차단 |
| Docker 사용자 설정 접근 | Docker config 접근 경고가 확인되었다. | Docker Desktop 실행 후 `docker version`에서 server version이 반환되는지 재확인 | Testcontainers 시작 가능 여부 |

#### 기본값으로 진행할 사항

| 항목 | 기본 결정 | 근거 |
|---|---|---|
| Java | 설치된 Temurin Java `21.0.10` 사용 | Java 21 요구 충족. (`docs/REQUIREMENTS.md §2`) |
| Node/npm | Node `22.21.1`, npm `10.9.4`; PowerShell에서는 `npm.cmd` 사용 | 현재 환경 점검 결과 |
| 테스트 DB | `mysql:8.4` Testcontainers를 수용 기준으로 사용 | MySQL 8.x 정본 및 과거 green 검증. (`docs/REQUIREMENTS.md §2`, 과거 M0 `[개발 기록]`) |
| 로컬 DB명/origin | example 값은 DB `zeroverse`, FE origin `http://localhost:5173`; 실제 비밀번호 없음 | Vite 기본 포트와 CORS 명시 요구. (`docs/PRD.md §4.3`) |
| Compose/LocalStack | M0 미도입 | LocalStack은 M4 확정 범위. (`docs/PRD.md §10 M4`, `docs/PRD.md §13.1`) |
| 버전 기준선 | 과거 green 조합을 사용하되 lockfile과 전체 빌드로 재검증 | 초기화 이전 코드 전체 이식보다 재현 위험이 낮음 |
| Windows 파일 | `gradlew`와 `gradlew.bat` 모두 커밋, LF/실행권한을 Git 기준으로 확인 | Windows 개발과 CI 양쪽 지원 |
| 실제 S3 값 | 지금 요청하지 않고 M4 착수 시 받음 | `docs/PRD.md §13 잔여`, `docs/PRD.md §13.1` |

---

### 7. 기획 심의 소집 판정

#### 일반 소집 조건 해당

- 전체 MVP DB 스키마를 처음 도입한다: **DB 스키마 변경**. (`docs/governance/README.md §2`, `docs/PRD.md §3.4`)
- 공통 응답·에러 형식을 코드로 확정한다: **공개 API 계약 변경/도입**. (`docs/governance/README.md §2`, `docs/PRD.md §4.1~§4.4`)
- Spring Boot, QueryDSL, SpringDoc, Testcontainers, React, Tailwind 등 외부 라이브러리와 DB 테스트 인프라를 처음 도입한다. (`docs/governance/README.md §2`, `docs/PRD.md §2.1`)
- 비도메인 공통 에러코드가 정본에 없어 API 계약 결정을 요구한다. (`docs/REQUIREMENTS.md NFR-03~04`)

따라서 일반 조건만으로도 구현 전 심의 소집 대상이다.

#### 대형 마일스톤 조건 해당

| 조건 | 판정 | 근거 |
|---|---|---|
| 백엔드·프론트엔드·인프라 중 두 영역 이상 | 해당 | BE와 FE를 동시에 만들고 MySQL/Flyway/Testcontainers 기반도 도입한다. (`docs/PRD.md §10 M0`) |
| 세 개 이상의 도메인에 영향 | 해당 | V1이 User/Blog/Post/Category/Universe 등 전체 도메인 테이블을 확정한다. (`docs/PRD.md §3.1~§3.5`) |
| 인증 또는 접근제어 포함 | 비해당 | M0는 CORS와 임시 permit-all까지만이며 JWT·가드는 M1이다. (`docs/PRD.md §10 M1`) |
| DB 마이그레이션과 API 계약 변경 동시 포함 | 해당 | V1과 공통 응답·에러 계약을 함께 만든다. (`docs/PRD.md §4`, `docs/PRD.md §10 M0`) |
| 외부 서비스 연동 | 비해당 | S3/LocalStack 연동은 M4로 제외한다. |
| 선행 마일스톤 설계 변경 | 비해당 | M0가 첫 구현 마일스톤이다. |
| 단일 PR 분할 필요 | 아직 미판정 | 원자적 커밋으로 구분하되 현재 근거만으로 별도 PR 분할을 필수화하지 않는다. |

**결론: 3개 조건에 해당하므로 대형 마일스톤이며, 계획 직후 기획 심의를 소집해야 한다.** (`docs/governance/README.md §2`)

#### 심의 핵심 질문 — 한 문장

> 공통 예외 처리의 비도메인 오류(Bean Validation·미매핑 경로·500)는 **(A)** `COMMON_*`/`VALIDATION_*` prefix를 NFR-04에 추가, **(B)** 요청 도메인의 기존 prefix로 매핑, **(C)** M0에서는 BusinessException만 확정하고 나머지를 보류 중 어느 방식으로 정할 것인가—성공/실패 래퍼와 HTTP 400/404/500 구분은 유지하며 도메인 기능 오류와 JWT 401/403 정책은 이번 결정 범위에서 제외한다?

---

## [개발 기록]

### 2026-07-25 · Gate 1 — BE 공통/API (Claude)

승인된 검토 단위 3개 중 1번째(PRD §9.4-X).

- **빌드**: `settings.gradle`, `build.gradle`(Spring Boot 3.5.15 / dependency-management 1.1.6 / Java toolchain 21). Lombok·JJWT·AWS SDK·jsoup은 계획대로 제외.
- **Gradle wrapper 이식**: `gradlew`, `gradlew.bat`, `gradle/wrapper/*`(Gradle 8.10)를 `origin/feature/M4a-post-backend`(**source commit `a6f0407`**)에서 그대로 가져옴. 바이너리 wrapper라 정본 대조 대상 없음, 배포 URL만 확인.
- **공통 응답**: `ApiResponse`(4키 항상 직렬화), `ErrorResponse`(+ `FieldError`), `PageResponse.from(Page<T>)`.
- **예외**: `ErrorCode`(NFR-04 도메인 코드 + ADR-0002 비도메인 3종), `BusinessException`, `GlobalExceptionHandler`.
- **엔티티 기반**: `BaseEntity`(createdAt/updatedAt), `BaseSoftDeleteEntity`(deletedAt + softDelete/restore).
- **설정**: `SecurityConfig`(CORS·CSRF off·stateless·M0 permitAll + RISK-0002 주석), `CorsProperties`, `OpenApiConfig`(Bearer 스키마, `/swagger-ui.html`), `application.yml`(ddl-auto=validate, open-in-view=false), `application-local.example.yml`(비밀값 미커밋).
- **이슈**: record 컴포넌트 `success`와 무인자 정적 팩토리 `success()`가 Java record 접근자 규칙과 충돌해 컴파일 실패 → `ApiResponse.empty()`로 개명.

### 2026-07-25 · Gate 2 — V1·DB 테스트 (Claude)

- **`V1__init.sql`** 12테이블(users → blogs → categories → posts → post_images → tags → post_tags → universes → comments → post_likes → notifications → refresh_tokens).
  - **재사용**(source `a6f0407`의 V1): `categories.parent_key` STORED generated column 기반 unique key(MySQL 8.4 표현식 unique 문법 오류 회피), `post_likes` 명명(예약어 `likes` 회피), utf8mb4/InnoDB.
  - **변경**: category type에 `DEFAULT` 추가 + `SERIES` 제거(PRD §9-H 대조), 모든 enum 성격 컬럼에 CHECK 제약 추가, `is_default` 컬럼 미도입(PRD §3.2), FK/인덱스 명명 규칙 정리, `ck_universes_not_self` 추가.
  - 애플리케이션 레벨 규칙(블로그당 DEFAULT 1개, 깊이 제한 등)은 SQL로 강제하지 않음(NFR-08 / PRD §3.5).
- **테스트 인프라**: `MySqlTestSupport`(Testcontainers `mysql:8.4`, JVM당 1회 기동, `@DynamicPropertySource` 주입). Docker 미가용 시 skip하지 않고 실패한다.
- **이슈**: `categoryTypeIsRestricted`에서 `"DEFAULT"`·`"GENERAL"`이 모두 7글자라 `display_order`가 충돌 → `uk_categories_blog_parent_order`가 정상 동작한 것이며 테스트 데이터 버그. 인덱스 기반 순번으로 교체.
- **이슈**: `BaseEntityAuditingTest`가 `@EnableJpaAuditing`을 중복 선언해 `jpaAuditingHandler` 빈 정의 충돌 → 메인 앱의 선언만 사용하도록 제거(NFR-06은 `ZeroverseServerApplication`에만 선언하도록 요구).

**BE 테스트 결과 — 28 tests / 0 skipped / 0 failures** (`./gradlew test`, Docker daemon 29.3.1 기동 상태)

| 테스트 | 수 | 검증 |
|---|---|---|
| `FlywayMigrationTest` | 10 | 12테이블 생성, Flyway 성공 기록, 실제 INSERT, category type `SERIES` 거부, visibility `FRIENDS` 거부, email·nickname·slug unique, parent_key 기반 이름 중복 거부, self-universe 거부, FK 위반, utf8mb4 |
| `BaseEntityAuditingTest` | 4 | createdAt/updatedAt 자동 기록, 수정 시 createdAt 불변, soft delete/restore, 격리 확인 |
| `GlobalExceptionHandlerTest` | 4 | `VALIDATION_001` 400 + 필드 상세, BusinessException status/code, `COMMON_404`, `COMMON_500` **내부정보 비노출** |
| `ApiResponseTest` | 4 | 성공·실패 모두 4키 직렬화, details 기본 빈 배열 |
| `PageResponseTest` | 4 | 첫/중간/마지막/빈 페이지 hasNext·hasPrevious |
| `ZeroverseServerApplicationTests` | 2 | 컨텍스트 로드, 실제 DB가 MySQL 8.4 |
| `CorsConfigTest` | 2 | 허용 origin preflight 통과, 미허용 origin 403 |
| `OpenApiConfigTest` | 2 | `/v3/api-docs` Bearer 스키마, `/swagger-ui.html` |

### 2026-07-25 · Gate 3 — FE 골격 (Claude)

- **프로젝트**: `frontend/` — React 19.2 / Vite 8 / TS 6 / Tailwind v4 + `@tailwindcss/vite` / Vitest 4 + RTL + jsdom. `package-lock.json` 커밋. 구 브랜치 `frontend/package.json`의 버전 조합만 참고하고 코드는 재사용하지 않음(폐기된 다크 네온 팔레트 사용).
- **디자인 토큰**: `styles/index.css`에 `@theme`로 코어·표면·텍스트·시맨틱 6종·폰트·그림자 6종 주입, `:root`에 그라디언트 3종. 전역 `border-radius: 0`, `min-width: 1440px`, `tw`/`floaty` 키프레임, `::selection`. 폰트는 `index.html` `<link>`로 로드. 폐기 토큰(네온·골드) 미포함.
- **공용 UI 14종**: Button(7 variant) · Tabs · Badge(+CountBadge) · TagChip · Panel · PostCard · FormField · ListRow · Pagination · Avatar · Prose(+Callout·CodeBlock) · Table · Modal · TagInput. 전부 실제 variant·disabled·children API 구현(빈 파일·TODO 없음).
- **레이아웃**: `TopBar`(전 화면 공통, 관리자 버튼 없음), `SideNav`(`/`에서만, Admin 항목 없음), `AppShell`.
- **라우터**: 15경로 + `*` not-found. 가드는 M1로 미룸. 페이지는 제목·레이아웃·빈 상태만 렌더(가짜 데이터 없음).
- **하위 지침**: `src/main/java/com/zeroverse/AGENTS.md`, `frontend/src/AGENTS.md` 작성.
- **이슈**: `tsc -b`에서 `./styles/index.css` side-effect import의 타입 선언 누락(TS2882) → `types/vite-env.d.ts` 추가 + tsconfig `types`에 `vite/client` 추가.

**FE 결과** — `npx vitest run`: **8 파일 / 52 tests 전부 통과**. `npm run build`: `tsc -b && vite build` 성공(45 modules, CSS 17.97 kB / JS 238.07 kB).

| 테스트 | 검증 |
|---|---|
| `router.test.tsx` | 15개 URL이 각 페이지를 렌더 + 알 수 없는 경로의 not-found |
| `AppShell.test.tsx` | TopBar 공통, SideNav는 `/`에서만(온보딩·검색·설정에는 없음) |
| `TopBar.test.tsx` | 로고·검색·글쓰기·알림·프로필 존재, 관리자 항목 부재 |
| `Button.test.tsx` | 클릭, disabled 시 미호출, 7 variant |
| `Badge.test.tsx` | **enum `UNIVERSE` → 화면 라벨 "친구"**, 상태 배지 4종, count 0 미렌더 |
| `Pagination.test.tsx` | aria-current, 양끝 disabled, 0-base 콜백, totalPages 0 미렌더 |
| `PostCard.test.tsx` | 전달값만 렌더, 태그 없을 때 칩 없음, 클릭 |
| `smoke.test.tsx` | 공용 14종 + 빈 Table·닫힌 Modal, console.error 0건 |

## [이슈·결정]

- 2026-07-25 · 기획 심의 소집: 대형 마일스톤 조건 3건 해당(BE·FE·인프라 동시 / 3개 이상 도메인 / DB 마이그레이션+API 계약 동시). `docs/governance/README.md` §2.
- 2026-07-25 · 기획 심의 `M0-20260725-scaffold` 종료 — 회의록 [`docs/governance/meetings/M0-20260725-scaffold.md`](../governance/meetings/M0-20260725-scaffold.md). 세 독립 검토자 전원 `APPROVE_WITH_CHANGES`(확신도 92·93·유사), 제안 등급 전원 `HIGH`. 진행자 최종 등급 **`HIGH`**(LOW 자동 승인 8개 조건 중 7개 불충족), 상태 **`USER_DECISION_REQUIRED`**.
  - 1차 결정 질문: 비도메인 오류(Bean Validation·미매핑 경로·500)의 에러코드 분류 A/B/C. 진행자·검토자 3인 모두 **A 권고**(`VALIDATION_*` = 400, `COMMON_*` = 404/500). A는 `docs/REQUIREMENTS.md` NFR-04 정본 개정 + 공개 API 계약 변경이므로 사용자 승인 전 구현 금지.
  - 구현 전 필수 변경 14건, 사용자 결정 항목 6건은 회의록 §7 참조.
  - 위험 레지스터 등록: `RISK-0001`(V1 ↔ 후속 JPA 엔티티 불일치), `RISK-0002`(임시 `permitAll()` 잔존), `RISK-0003`(V1 checksum 분기), `RISK-0004`(공용 UI API 조기 고정). 모두 `OPEN`.
  - ADR-0002 `비도메인 API 오류 코드 분류` — 번호만 예약, 사용자 승인 후 작성.
  - Docker daemon 미가용은 마일스톤을 넘지 않는 **하드 게이트**로 분류(미해결 시 M0 미완료).
- 2026-07-25 · **사용자 승인 — 회의 상태 `APPROVED`**. 결정: (1) 에러코드 **A 채택**(`VALIDATION_001`/`COMMON_404`/`COMMON_500`), (2) M0는 **단일 PR + 독립 리뷰 게이트 3개**(BE 공통/API → V1·DB 테스트 → FE 골격), (3) Docker Desktop 사용자 기동 후 Testcontainers `mysql:8.4` 전체 green이 완료 하드 게이트, (4) 필수 변경 14건 반영 전제로 착수 승인, (5) V1은 공유 환경 미적용 확인.
  - 정본 반영: `docs/REQUIREMENTS.md` NFR-04 "비도메인 공통 오류 코드" 표 추가, `docs/PRD.md` §4.4 요약 + §9.4(W 오류코드 / X 검토 단위 / Y 완료 하드 게이트), `docs/governance/decisions/ADR-0002-non-domain-error-taxonomy.md` ACCEPTED.

## [M0 적용 DoD 매트릭스] (심의 필수 변경 #10)

PRD §12 DoD와 §11 테스트 전략의 각 항목에 대해 **M0에 적용되는지**, 적용된다면 **무엇으로 검증했는지**를 구분한다.
"비적용"은 `skip`이 아니라 **해당 기능이 M0 범위에 없어 검증 대상이 아니라는 뜻**이며, M0에 적용되는 항목에는 placeholder·`skip`·stub을 허용하지 않는다.

### PRD §12 DoD

| # | DoD 항목 | M0 적용 | 검증 근거 | 결과 |
|---|---|---|---|---|
| 1 | 해당 FR/NFR 규칙 전부 구현 | **적용**(M0는 FR 없음, NFR-01·04·05·06·07·08 해당) | CorsConfigTest(NFR-01), ErrorCode+GlobalExceptionHandlerTest(NFR-04), OpenApiConfigTest(NFR-05), BaseEntityAuditingTest(NFR-06), FlywayMigrationTest(NFR-07·08) | ✅ |
| 2 | §11 해당 테스트 존재·통과, placeholder/skip/stub 금지 | **적용** | **BE 36 tests / FE 152 tests(13 파일)**, 전부 `skipped="0"`. `@Disabled`·`it.skip`·`todo` 0건 | ✅ |
| 3 | 공통 응답·에러코드·페이징 규약 준수, Swagger 문서화 | **적용** | ApiResponseTest(4키 항상 직렬화), PageResponseTest, OpenApiConfigTest(`/v3/api-docs` + Bearer 스키마) | ✅ |
| 4 | FE가 §6 토큰 적용 + `docs/design/` 정본과 시각 일치 | **적용** | 토큰 정본 대조 + 구조 DOM 테스트 + 수치 고정 테스트 + **1440px Playwright 시각 대조 3회**(3·4차 리뷰 지적 반영 포함, 아래 `[시각 대조 기록]`) | ✅ |
| 4b | 연동 API 실제 동작 확인 | **비적용** — M0에 연동할 도메인 API가 없다(M1~M9) | — | — |
| 5 | 보안: 비밀 미커밋, sanitize, 권한 가드 | **부분 적용** — 비밀 미커밋만 해당. sanitize는 M4, 권한 가드는 M1 | `application-local.example.yml`은 placeholder만, `application-local.yml`은 gitignore. `git ls-files`에 비밀값 없음 | ✅(해당 범위) |
| 6 | 빌드·린트 통과 + 저자와 다른 패스의 검증 | **적용** | `./gradlew test` BUILD SUCCESSFUL, `npm run build`(tsc -b && vite build) exit 0, `npm run lint`(oxlint) exit 0. Codex 리뷰(3b) — 저자 Claude와 분리 | ✅ |

### PRD §11 테스트 범주

| 범주 | M0 적용 | 근거 |
|---|---|---|
| BE 단위 — 가입/로그인/토큰/접근제어/카테고리 정책 | **비적용** | 해당 도메인이 M1~M9. 지금 작성하면 가짜 구현이 된다 |
| NFR-01 CORS | **부분 적용** | 허용 origin preflight만 M0. JWT·Refresh 쿠키 정책은 M1(PRD §4.3) |
| BE 단위 — 공통 응답·예외·에러코드 | **적용** | ApiResponseTest(4), PageResponseTest(4), GlobalExceptionHandlerTest(4) |
| Repository/JPA — unique 제약 | **적용**(스키마 레벨) | FlywayMigrationTest — email·nickname·url_slug·universe·post_like·post_tag·post_image·refresh_token unique 실제 위반 검증 |
| Repository/JPA — Auditing 자동 생성 | **적용** | BaseEntityAuditingTest(4) |
| Repository/JPA — soft delete 조회 제외, 연관관계 | **비적용** | 도메인 Repository가 M1 이후. 스키마의 `deleted_at` 존재만 확인 |
| Controller — 400 공통 응답 | **적용** | GlobalExceptionHandlerTest |
| Controller — 401/403 | **비적용** | 인증이 M1. M0는 permitAll(RISK-0002로 추적) |
| BE 통합 시나리오 | **비적용** | 가입→로그인→작성 흐름이 M1~M4 |
| FE — AuthContext / apiClient 401 갱신 / 라우터 보호 | **비적용** | 전부 M1(PRD §8) |
| FE — 라우팅·레이아웃·공용 컴포넌트 | **적용** | router / AppShell / SideNav / TopBar / Button / Badge / Pagination / PostCard / components / smoke |

### 잔여 (M0 머지 전 처리 필요)

- **DoD 4 브라우저 시각 대조 — 2026-07-25 완료.** 아래 `[시각 대조 기록]` 참조.

---

## [시각 대조 기록] (2026-07-25 · DoD 4)

### 환경 문제와 해결

1차 시도가 `EACCES listen ::1:5173`으로 실패했다. 원인은 샌드박스가 아니라 **Windows 예약 포트**였다 — `netsh interface ipv4 show excludedportrange protocol=tcp`의 `5141–5240` 구간에 5173이 포함된다(Hyper-V/Docker 동적 예약). `4173`·`3000`·`8080`은 정상 바인딩된다.

- 서버: `npx vite --port 4173 --strictPort` (IPv6 `[::1]`에 바인딩되므로 `localhost:4173`으로 접근)
- 렌더: Playwright + `channel: 'chrome'`(설치된 Chrome 구동, 별도 브라우저 다운로드 없음), viewport **1440×1200**, `fullPage` 캡처
- 정본: `docs/design/*.dc.html`을 `file://`로 직접 렌더(support.js 런타임이 동작함을 확인)

### 대조 결과

| 화면 | 정본 | 판정 |
|---|---|---|
| `/` | `ZeroVerse Main Feed v2.dc.html` | ✅ 히어로 240px(그라디언트·픽셀 별·로켓·구름 클립), 3열 `210px 1fr 300px`, SideNav, 우측 3패널, 탭 3개 |
| `/signin` | `Pages.dc.html` LOGIN | ✅ 전폭 다크 그라디언트 + 중앙 420px 카드, `shadow-on-dark` |
| `/settings` | `Pages.dc.html` SETTINGS shell | ✅ `max-width:1240`, `240px 1fr`, SETTINGS 패널 3항목 |

계측값(모든 화면 공통): `border-radius != 0`인 요소 **0개**, `scrollWidth` 1440(가로 오버플로 없음), `body` 배경 `rgb(246,234,216)`(`#f6ead8`), 폰트 `IBM Plex Sans KR`.

### 대조로 발견해 수정한 불일치

| # | 불일치 | 수정 |
|---|---|---|
| 1 | 로고가 잉크 단색이고 마크가 없음 | 14px accent 정사각 마크(`4px 4px 0 shadow, -3px 3px 0 ink`) + `ZERO`(잉크)`VERSE`(accent) 2색 |
| 2 | 검색바가 좌측에 붙고 "검색" 버튼이 줄바꿈됨 | `flex-1 justify-center`로 중앙 정렬, 버튼 `shrink-0` + `padding:0 18px` |
| 3 | 검색 플레이스홀더가 임의 문구 | 정본 `유니버스 전체 검색 — 글 · 블로그 · 사용자 · 태그` |
| 4 | 알림·프로필이 텍스트 링크 | 중립 버튼(3px 보더 + `shadow-btn`), 라벨 `제로별`, 글쓰기 `✎ 글쓰기` |
| 5 | 상단바 padding/gap 임의값 | `height:64px`, `padding:0 28px`, `gap:20px`, `z-index:10` |
| 6 | **`/`에 히어로가 없음** | `Hero` 신설 — 그라디언트, 2레이어 픽셀 별(`steps(2)` 2.6s/3.4s), 8단 픽셀 로켓(`floaty 5s`), 구름 clip-path |
| 7 | **`/`의 우측 300px 열이 비어 있음** | `RightPanel` 신설 — `내 블로그` / `최근 알림` / `유니버스 현황` 3패널(빈 상태) |
| 8 | **`/settings`의 240px 열이 비어 `main`이 잘못된 칸에 들어감** | `ScreenPanel` 신설 — SETTINGS 헤더 + 프로필/유니버스/글·카테고리. `/blog/:slug`용 blog 패널도 포함 |
| 9 | 컨테이너 폭이 전 화면 동일 | `lib/layout.ts` 신설 — DESIGN-SYSTEM §6.2의 화면별 max-width·grid·패딩을 단일 출처로 |
| 10 | `/signin`이 paper AppShell 안에서 렌더 | `OnboardingScaffold` + AppShell 온보딩 분기 — 전폭 다크, 중앙 카드(420/560px) |
| 11 | MainPage가 히어로와 h1 중복 | 중앙 열을 정본 구조(탭 3개 + 피드 영역)로 교체 |

**남은 차이(의도된 것)**: 알림 카운트 배지와 각 패널의 실제 데이터는 M0에 데이터가 없어 렌더하지 않는다. 가짜 데이터를 넣지 않는다는 원칙(PRD §12)에 따른다.

---

### 2026-07-25 · Codex 재리뷰 3차 — Verdict: 블로킹(4건 중 3건 해소)

| 2차 지적 | 판정 |
|---|---|
| #13 게이트 커밋 분리 | **해소** — `fe675a2`에 V1·FE 없음, `32afc11`에 V1·DB만. 백업 대비 트리 동일 |
| build/lint 증거 | **해소** — lint 직접 실행 exit 0, `dist`·BE XML 시각·수치 대조 |
| SideNav 정본·테스트 | **해소** |
| DoD 4 시각 일치 | **미해소** — 아래 7건 |

**리뷰가 옳았다.** 2차 반영 때 워크로그에 `✅ 정본 시각 일치`로 단정한 것은 과장이었다.
당시 대조는 **구조 수준**이었고 픽셀 수준 대조가 아니었는데 ✅로 적었다.

### 2026-07-25 · 3차 리뷰 반영 (수정 7건 + 사용자 결정 1건)

| # | 지적 | 처리 |
|---|---|---|
| 1 | `/blog/:slug`에 190px 히어로 누락 | 반영 — layout spec에 `hero`(190px) 추가. `BlogPage`는 히어로가 h1을 담당하므로 본문 열만 렌더하도록 재작성 |
| 2 | 구름 기본값이 정본과 다름(`showClouds` 기본 `false`) | **사용자 결정으로 현행 유지** — PRD §9.4-Z에 결정 기록. 다크 히어로 → 크림 페이퍼 전환을 픽셀 스카이라인이 담당한다 |
| 3 | 온보딩에 별·로켓 누락 | 반영 — `StarField.tsx` 신설(`PixelStars`·`PixelRocket`·정본 별 좌표 3종). 온보딩에 `AUTH_STARS` + 로켓(`floaty 6s`, `left:120 bottom:120`) 적용 |
| 4 | 온보딩 그라디언트가 정본과 정지점 다름 | 반영 — `--gradient-auth`(55%/90%) 토큰 신설. dusk(50%/85%)와 구분 |
| 5 | RightPanel 간격 24px ↔ 정본 18px | 반영 — `gap: 18` |
| 6 | Panel 헤더 13px/2px ↔ 정본 12px/3px | 반영 — `px-3.5 py-[11px] text-xs tracking-[3px]`. 정본 인스턴스(RIGHT RAIL·블로그 카테고리) 기준 |
| 7 | 블로그 ScreenPanel이 단일 빈 박스 ↔ 정본 2패널 | 반영 — `■ 카테고리` / `■ 블로그 통계` 두 패널, `gap:18px` |
| 8 | TopBar 검색 입력 padding 10px ↔ 정본 9px | 반영 — `py-[9px]` |
| 비블로킹 | `startsWith('/settings')`로 미등록 경로가 설정 셸로 렌더 | 반영 — 등록된 3개 경로 정확 매칭으로 교체 + 회귀 테스트 |

**대조 중 발견한 환경 이슈**: 장시간 떠 있던 Vite dev 서버의 Tailwind 스캔이 상해
유틸리티 클래스가 생성되지 않은 채 서빙됐다(토큰은 정상). 서버 재기동으로 해소.
CSS가 빠진 스크린샷을 정본 대조 결과로 오인할 뻔했으므로, 이후 대조 전에는
`bg-paper` 등 대표 유틸리티가 서빙 CSS에 있는지 먼저 확인한다.

**검증**: FE **130 tests**(12 파일), BE **36 tests**, `npm run build`·`npm run lint` exit 0.

### 2026-07-25 · Codex 재리뷰 4차 — Verdict: 블로킹 2건

3차 지적 7건 중 6건(구름은 사용자 결정으로 제외)을 해소로 판정하고 2건을 새로 냈다.

| # | 지적 | 처리 |
|---|---|---|
| 1 | `/blog/:slug` 히어로가 높이만 맞고 정본 구조와 다름 — 전용 별 좌표, 로켓 `right:170/top:34`, 하단 정렬, 76px 아바타, 우측 액션 슬롯이 없음 | 반영 — `Hero`에 `variant`(`feed`/`blog`) 도입. blog는 `BLOG_STARS`(11개), 로켓 `right:170/top:34`, 하단 정렬, `HeroAvatar`(76px) + `actions` 슬롯 |
| 2 | **내가 만든 회귀** — 로그인 장식을 온보딩 3경로에 일괄 적용해 `/blog/setup` 정본을 깨뜨림(정본은 별 11개, 로켓 없음) | 반영 — `SETUP_STARS` 추가, layout spec에 `onboardingDecor`(별 종류 + 로켓 여부)를 두어 경로별로 분기 |
| 비블로킹 | `/settings/` 후행 슬래시가 fallback으로 떨어짐 | 반영 — `normalize()`로 후행 슬래시 제거 후 매칭 |
| 비블로킹 | 수정 수치가 테스트로 고정되지 않음 | 반영 — `design-tokens.test.tsx` 신설. 별 개수·로켓 좌표·Panel 헤더·RightPanel/ScreenPanel gap·TopBar 규격을 고정 |
| 비블로킹 | PR 본문이 최신 수치를 반영하지 않음 | 반영 — 갱신 |

**액션 버튼**(`✦ 유니버스 신청`·`RSS`)은 슬롯만 제공하고 M0에서 렌더하지 않는다.
동작이 M5(유니버스)·M2(블로그 설정)에 있어 지금 그리면 눌러도 아무 일이 없는 가짜 UI가 된다.
아바타는 데이터가 아니라 디자인 크롬이므로 M0에서 렌더한다.

**검증**: FE **144 tests**(13 파일) / BE **36 tests**, 전부 0 skipped·0 failures.
build·lint exit 0. `/`, `/signin`, `/settings`, `/blog/:slug`, `/blog/setup` 1440px 재대조 완료.

## [리뷰]

### 2026-07-25 · Codex 리뷰 1차 — Verdict: 블로킹

blocking 6건(HIGH 4 · MEDIUM 1 · LOW 1) + 비블로킹 3건. 전문은 리뷰 출력 참조.

| # | 심각도 | 지적 | 처리 |
|---|---|---|---|
| 1 | HIGH | 심의 필수 변경 #10 M0 DoD 매트릭스 미작성 | 반영 — 위 `[M0 적용 DoD 매트릭스]` 절 신설 |
| 2 | HIGH | #12 공용 UI 14종 중 4종만 동작·variant 테스트, 나머지는 smoke뿐 | 반영 — `components.test.tsx` 신설로 Tabs·TagChip·Panel·FormField·ListRow·Avatar·Prose·Table·Modal·TagInput 10종에 상태·동작·variant·접근성 테스트 추가 |
| 3 | HIGH | #13 Gate 1·2가 한 커밋에 묶였고 독립 리뷰 증거 없음. PR의 "12건 반영" 주장 부정확 | 반영 — 아래 `[게이트별 독립 검증 증거]` 기록 + PR 본문 정정 |
| 4 | HIGH | SideNav가 정본과 불일치(240px·자체 메뉴·헤더/아이콘/캡션 없음) | 반영 — 210px, `NAVIGATION` 헤더, Home▲/My Blog■/Search◎/Universe✦/Settings▤, 토큰 캡션으로 교체. `SideNav.test.tsx` 구조 테스트 추가 |
| 5 | MEDIUM | `updatedAt` assertion이 `isAfterOrEqualTo`라 값이 안 바뀌어도 통과(fake-pass) | 반영 — 프로브 테이블을 `DATETIME(6)`으로 바꾸고 `isAfter`로 강화 |
| 6 | LOW | `frontend/tsconfig.tsbuildinfo` 빌드 산출물 추적 | 반영 — 추적 해제 + `*.tsbuildinfo` ignore |

비블로킹:
- V1 제약 회귀 테스트 확대 → **반영**(universe status·unique, post_likes/post_tags/post_images unique, notification enum, refresh_token unique·not-null 추가).
- `V1__init.sql` 주석이 self-universe를 "애플리케이션 레벨"로 잘못 분류 → **반영**(DB CHECK로 강제됨을 명시).
- 브라우저 시각 대조 미수행 → **미해결**. DoD 매트릭스의 "잔여"로 명시했다.

### [게이트별 독립 검증 증거] (심의 필수 변경 #13)

사용자 결정(PRD §9.4-X)은 "단일 PR + **원자적 커밋**·독립 리뷰 게이트 3개"였다.

1차 구현에서 Gate 1과 Gate 2가 커밋 `a3c2b5c` 하나로 합쳐졌고, Codex 재리뷰가 이를
**HIGH 블로킹**으로 판정했다("사후 테스트 기록은 독립 리뷰 게이트 증거가 아니다").
2026-07-25 사용자 승인을 받아 **rebase로 커밋을 분리하고 force-push**했다.

- 백업: `backup/M0-before-split`(분리 전 `f9e7787`)
- 분리 후 최종 트리는 분리 전과 **바이트 동일**함을 `git diff --stat backup/M0-before-split HEAD`(출력 없음)로 확인했다.

| 게이트 | 커밋 | 범위 | 검증 |
|---|---|---|---|
| Gate 1 | `fe675a2` | 빌드 설정, 공통 응답·예외·엔티티 기반, config | `./gradlew test` **12 tests / 0 skipped / 0 failures**. 이 시점에 V1·FE 없음 |
| Gate 2 | `32afc11` | `V1__init.sql` 12테이블, Testcontainers 인프라, DB 통합 테스트 | **28 tests / 0 / 0**. 중간 실패 2건(display_order 충돌, `jpaAuditingHandler` 중복) 해소 후 green |
| Gate 3 | `2cc1901` | FE 골격·디자인 토큰·공용 UI·라우터 | `npx vitest run` **52 tests**, `tsc -b && vite build` 성공 |
| 리뷰 반영 | `58977e7`, `8dcb95d` | 1차·2차 리뷰 blocking 해소 | BE **36 tests**, FE **126 tests**, build·lint exit 0 |

### 환경 이슈 (2026-07-25, 코드 회귀 아님)

rebase 직후 BE 테스트가 `NoClassDefFoundError: MySqlTestSupport`로 20건 실패했다.
원인은 **Docker Desktop 종료**였고 Testcontainers가 컨테이너를 띄우지 못한 것이다.
트리가 36/36 통과 시점과 동일했으므로 코드 회귀가 아니다. Docker 재기동 후
`./gradlew test` **36 tests / 0 skipped / 0 failures**로 복구됐다.

이는 PRD §9.4-Y가 정한 하드 게이트가 실제로 작동함을 보여준다 — Docker 없이는 skip하지 않고 **실패한다**.

### 2026-07-25 · Codex 재리뷰 5차 — **Verdict: MERGEABLE**

**남은 blocking 없음. 심의 필수 변경 14/14 충족.** 진행자 판정: PR #6을 `dev`로 머지 권고.

- 4차 blocking 2건(블로그 히어로 variant, `/blog/setup` 장식 회귀) 모두 해소 확인.
- 구름 유지는 PRD §9.4-Z 사용자 결정으로 적절히 기록됐다고 판정 — 재지적 대상 아님.
- 액션 버튼을 슬롯만 두고 렌더하지 않은 판단도 **타당**하다고 판정 — "테스트 가능한 동작 없는
  버튼을 추가하지 않는 것이 placeholder/stub 금지 원칙에 부합"(PRD §12).
- 새 회귀 없음: `feed` variant가 기존 별 2레이어·로켓 `right:150/top:16`·240px·중앙 정렬 유지.

**비블로킹 지적 반영(머지 전 처리)**

| 지적 | 처리 |
|---|---|
| 별 좌표가 개수 검사뿐이라 다른 값으로 바꿔도 통과 | 반영 — 정본에서 옮긴 **독립 기대 문자열**과 5종 전부 일치 검증 |
| 로켓 8단 폭·색·높이 미고정 | 반영 — 8단 폭/색/7px 높이 + 4번째 단에만 날개 그림자 검증 |
| 블로그 히어로 padding·gap·타이포·아바타 수치 미고정 | 반영 — `0 60px 24px`/`gap:18`/하단정렬, eyebrow 9px·h1 28px tracking 2px, 아바타 76px·3px 보더·다크 그림자 |
| DoD 매트릭스 FE 수치 stale | 반영 — **152 tests / 13 파일**로 최신화 |

**최종 검증**: FE **152 tests**(13 파일) / BE **36 tests**, 전부 0 skipped·0 failures.
`npm run build`·`npm run lint` exit 0.

---

## [머지]

### 2026-07-25 07:32 UTC · PR #6 → `dev` 머지 완료

- **머지 커밋**: `62e4040` — `M0: 프로젝트 스캐폴딩 (백엔드 공통 인프라 + V1 스키마 + 프론트 골격) (#6)`
- **방식**: **merge commit**(squash 아님). 심의 필수 변경 #13이 게이트별 원자적 커밋을 요구했으므로
  squash하면 그 히스토리가 `dev`에서 사라진다. PR #1~#4의 squash 관행과 다른 선택이며 이 사유로 정당화된다.
- **게이트 커밋 보존 확인**: `fe675a2`(Gate 1) · `32afc11`(Gate 2) · `2cc1901`(Gate 3) 모두
  `git merge-base --is-ancestor <c> dev`로 `dev`에 포함됨을 검증했다.

**최종 상태**

| 항목 | 결과 |
|---|---|
| 기획 심의 | `M0-20260725-scaffold` HIGH / APPROVED, 필수 변경 **14/14 충족** |
| Codex 리뷰 | 5회 — blocking 6 → 4 → 1 → 2 → **0**, 최종 `MERGEABLE` |
| BE 테스트 | **36 tests** / 0 skipped / 0 failures (Testcontainers mysql:8.4) |
| FE 테스트 | **152 tests**(13 파일) / 0 skipped / 0 failures |
| 빌드·린트 | `./gradlew test`, `npm run build`, `npm run lint` 전부 exit 0 |
| 시각 대조 | `/`, `/signin`, `/settings`, `/blog/:slug`, `/blog/setup` 1440px 완료 |

**M1로 넘기는 항목**

- `RISK-0002` — M0의 임시 `anyRequest().permitAll()`을 JWT 필터·401/403으로 **반드시 교체**.
  M1 완료 조건에 401/403 회귀 테스트가 포함된다.
- `RISK-0001`(V1 ↔ 후속 JPA 엔티티 정합), `RISK-0003`(V1 checksum), `RISK-0004`(공용 UI API 조기 고정) OPEN 유지.
- PRD §9.3 미확정 4건 중 ①`birth_date`는 M1 회원가입 폼에서 확인 필요.
- 5차 리뷰 후속 권고: FE 테스트 결과·1440px 비교 이미지를 CI 아티팩트로 보존하는 방안 검토.

**기록 정정**: 이 두 절(`5차 리뷰`·`[머지]`)은 원래 머지 전에 기록됐어야 했으나, 앞선 편집에서
`[게이트별 독립 검증 증거]` 이후 내용을 통째로 잘라내는 실수로 유실됐다가 머지 직후 복구됐다.
`[리뷰]` 절의 1~4차 기록은 영향 없다.
