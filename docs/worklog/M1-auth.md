# M1 — 인증/인가

- **작성 시각**: 2026-07-25 KST
- **브랜치**: `feature/M1-auth` (base: `dev`)
- **범위**: FR-AUTH-01~05 — 회원가입(기본 블로그·미분류 자동 생성), 로그인, JWT Access/Refresh 발급, refresh rotation, 로그아웃, `/auth/me`, SecurityConfig 교체. FE: AuthContext, apiClient(401 자동 갱신), SigninPage/SignupPage, 라우팅 가드.
- **제외**: `/users/me`·블로그 초기설정 API(M2), OAuth·이메일 인증·기기별 세션(MVP 밖), 카테고리 CRUD(M3).
- **기준 문서**: `AGENTS.md`, `docs/PRD.md` §2.2·§3·§4.3·§4.5·§5.1·§7.1~§7.2·§8·§9.4·§10 M1·§11·§12, `docs/REQUIREMENTS.md` §4·§5·FR-AUTH-01~05·NFR-01~09, `docs/design/` SCREEN: LOGIN / BLOG SETUP, `docs/governance/README.md` §2.
- **선행 상태**: M0 완료·머지(PR #6, 머지 커밋 `62e4040`). 공통 계층·V1 스키마·FE 골격이 `dev`에 있다. **도메인 엔티티는 아직 없으며 M1이 처음 만든다.**
- **확정 제약**:
  - PRD §9.4-**AA** — 회원가입 `name`(NOT NULL)·`birth_date`(nullable) **유지**(사용자 결정 2026-07-25).
  - **RISK-0002** — M0의 `anyRequest().permitAll()`을 JWT 필터·401/403으로 **반드시 교체**하고 회귀 테스트를 완료 조건에 포함한다.
  - Access=프론트 메모리, Refresh=HttpOnly Secure SameSite 쿠키 + **rotation 필수**.
  - `V1__init.sql` 수정 금지 — 필요 시 V2 forward migration(RISK-0003).
- **참고 자산**: 초기화 이전 M1 구현이 `origin/feature/M1-auth`(=`archive/M1-auth-legacy`, `3877b20`)에 읽기 전용으로 존재. 당시 Codex 리뷰 지적(블로킹 3 + 비블로킹 4)을 계획에 반영했다.

## [계획] (Codex · 2026-07-25)

### 0. 기준과 선행 판정

- 현재 브랜치 `feature/M1-auth`, 기준 브랜치 `dev`, M0 머지 커밋 `62e4040`을 확인했다.
- M1 범위는 FR-AUTH-01~05와 그 실행에 필요한 인증 도메인·보안·프론트 인증 인프라로 잠근다(`docs/PRD.md §5.1`, `docs/PRD.md §8`, `docs/PRD.md §10 M1`).
- `User.name`은 NOT NULL, `User.birth_date`는 nullable로 구현하고 두 필드를 회원가입 폼에 노출한다. 이는 `birth_date`를 필수로 적은 기존 문구보다 최신 사용자 결정이 우선한다(`docs/PRD.md §9.4-AA`, `docs/REQUIREMENTS.md §4 User`, 사용자 명시 결정).
- M0 `SecurityConfig.anyRequest().permitAll()`은 M1에서 제거하며 401/403 회귀 검증을 완료 하드 게이트로 둔다(`docs/governance/RISK-REGISTER.md RISK-0002`, `docs/REQUIREMENTS.md NFR-01·03·09`).
- `V1__init.sql`은 수정하지 않는다. 현재 JPA 매핑이 V1과 맞지 않으면 V2 이상 forward migration만 검토한다(`docs/PRD.md §3.4`, `docs/REQUIREMENTS.md NFR-07`, `docs/governance/RISK-REGISTER.md RISK-0003`).
- 구현 전 기획 심의가 필요하다. 상세 판정은 §8에 기록한다(`docs/governance/README.md §2`).

---

### 1. 초기화 이전 M1 자산 재사용 권고

원칙은 **체리픽 금지, 수정 완료 버전의 의도와 테스트 사례만 선별 이식**이다. 레거시는 현재 M0보다 오래된 공통 골격·디자인·패키지 구조를 포함하므로 파일 전체 복사는 허용하지 않는다.

#### 1.1 백엔드

| 레거시 파일 | 판정 | 재사용 범위와 주의점 |
|---|---|---|
| `build.gradle` | 참고 | JJWT 3모듈 구성만 참고한다. 레거시 `0.12.1`은 폐기하고 심의 승인 후 `jjwt-api/impl/jackson:0.13.0`으로 고정한다. 현재 공식 최신 릴리스와 Maven Central 게시 버전은 0.13.0이다([JJWT releases](https://github.com/jwtk/jjwt/releases), [Maven Central](https://repo1.maven.org/maven2/io/jsonwebtoken/jjwt-api/0.13.0/)). 현 M0 의존성은 보존한다. |
| `common/util/SlugGenerator.java` | 수정 후 취지 재사용 | 정규화·예약어 회피·충돌 suffix·**suffix 포함 30자 상한**이라는 최종 리뷰 취지를 재사용한다. 정적 timestamp fallback과 100회 제한은 폐기하고, 항상 3~30자를 보장하는 결정적 후보 생성으로 다시 작성한다(`docs/PRD.md §4.5`, 과거 `M1-auth.md [재리뷰 2차]`). |
| `auth/config/JwtProperties.java` | 참고 후 재작성 | Lombok·`jwt.*` prefix를 폐기하고 검증 가능한 immutable `@ConfigurationProperties("zeroverse.jwt")`로 작성한다. 초 단위 `Integer`보다 `Duration` 사용을 권고한다. secret 기본값은 두지 않는다(`docs/PRD.md §4.3`, `docs/REQUIREMENTS.md NFR-01`). |
| `auth/config/AuthCookieProperties.java` | 참고 후 재작성 | Lombok을 제거하고 `zeroverse.auth.cookie`로 통일한다. cookie read/write 모두 동일 설정 객체를 사용한다. |
| `auth/security/JwtProvider.java` | 참고 후 재작성 | `sub`, `jti`, `role`, `type`, `iat`, `exp` 설계는 재사용한다. JJWT 0.13 API, 주입 `Clock`, 명시적 HS256 검증, 만료/서명·형식 오류 구분, Base64 secret decoding으로 다시 작성한다. 레거시의 매번 parser 생성, 원본 예외 메시지 로깅은 폐기한다. |
| `auth/security/JwtAuthenticationFilter.java` | 참고 후 재작성 | `OncePerRequestFilter`와 Access 전용 검증 흐름은 재사용한다. 예외 전체 삼키기, stack trace 로그, 불명확한 401 원인 처리는 폐기한다. active·미삭제 사용자 확인 후 principal을 구성한다. |
| `auth/security/RefreshTokenHasher.java` | 재사용 권고 | raw refresh JWT의 SHA-256 해시 저장과 `MessageDigest.isEqual` 비교 취지는 적합하다. null 입력 방어와 테스트만 보강한다. 고엔트로피 토큰이므로 BCrypt 대상이 아니다(`docs/PRD.md §3.2·§4.3`). |
| `auth/security/ZeroverseUserPrincipal.java` | 참고 후 재작성 | `ROLE_{role}` authority와 userId 보유 구조를 재사용한다. 비밀번호를 principal에 장기 보관할 필요는 없으므로 최소 필드로 축소한다. |
| `auth/service/AuthService.java` | 로직 참고, 소스 폐기 | 가입 단일 트랜잭션, BCrypt, 기본 Blog·DEFAULT Category 생성, 비밀번호 확인 후 SUSPENDED 판정 취지를 재사용한다. 존재하지 않는 `USER_005/006` 사용, 레거시 패키지, 중복검사와 DB unique 간 불일치는 폐기한다. |
| `auth/service/RefreshTokenService.java` | 알고리즘 재작성 | hash·jti 조회·revoke 개념만 참고한다. 레거시의 “검증→별도 조회→revoke”는 동시 refresh race가 있으므로 폐기한다. 비관적 row lock을 이용한 원자적 rotation으로 바꾼다. `expires_at`은 현재 시각+상수 대신 JWT `exp`에서 얻는다. |
| `auth/controller/AuthController.java` | 계약 참고, 소스 폐기 | 5개 엔드포인트와 configurable cookie read 취지를 재사용한다. Controller가 repository/JWT orchestration을 직접 수행하는 구조, `AUTH_004`, `ApiResponse.success(null)` 사용은 폐기한다. Controller는 DTO·cookie·service 호출만 담당한다(`docs/PRD.md §2.2·§5.1`). |
| `auth/support/RefreshTokenCookieFactory.java` | 수정 재사용 | HttpOnly/Secure/SameSite/Path/Max-Age와 동일 설정 기반 clear-cookie를 재사용한다. 운영 Secure=true, local profile만 false로 분리한다. |
| `domain/user/entity/User.java`, `UserRole.java`, `UserStatus.java` | 참고 후 재작성 | 필드·enum·soft delete 구조를 V1에 맞춰 재작성한다. 현재 프로젝트는 Lombok을 사용하지 않는다. 삭제 사용자와 unique 재사용 정책은 V1의 전체-row unique와 일치시킨다. |
| `domain/blog/entity/Blog.java` | 참고 후 재작성 | M1 가입에 필요한 전체 V1 매핑과 기본 블로그 factory만 둔다. raw nickname을 slug로 쓰는 `createDefault`는 폐기한다. |
| `domain/category/entity/Category.java`, `CategoryType.java` | 수정 재사용 | `DEFAULT/GENERAL/LOCKED`, `parent_key` read-only 매핑, DEFAULT factory 취지는 재사용한다. M1에서는 CRUD 규칙을 구현하지 않는다(`docs/PRD.md §3.2·§9-H`). |
| `domain/auth/entity/RefreshToken.java` | 재작성 | 현 V1에는 `updated_at`이 있으므로 별도 createdAt 엔티티가 아니라 `BaseEntity`를 상속한다. 만료·폐기 판정은 주입된 기준 시각을 받도록 해 테스트 가능하게 한다. |
| 네 Repository | 참고 후 재작성 | 필요한 최소 조회만 둔다. RefreshToken에는 `PESSIMISTIC_WRITE` 단건 조회를 추가한다. soft-deleted 사용자의 로그인 조회는 제외하되 email/nickname/slug 중복검사는 V1 unique와 맞게 삭제 row까지 고려한다(`docs/REQUIREMENTS.md NFR-08`, `RISK-0001`). |
| DTO 5종 | 수정 재사용 | 응답 shape의 `accessToken`, `defaultBlog` 개념은 참고한다. `RegisterRequest.birth_date`의 `@NotNull`은 제거하고 nullable로 구현한다. `name`은 필수다(`docs/PRD.md §9.4-AA`). |
| `SecurityConfig.java` | 정책 참고, 소스 재작성 | public allowlist, stateless, JWT filter 위치, BCrypt strength 12는 재사용한다. 현 M0 CORS bean과 설정 prefix를 보존하고 `anyRequest().authenticated()`로 교체한다. |
| `SecurityAuthenticationEntryPoint.java` | 폐기 후 재작성 | 레거시 `AUTH_004`는 현 `ErrorCode`에 없으므로 사용 금지다. Spring 관리 `ObjectMapper`와 현 `ApiResponse`를 주입한다. 에러코드 결정은 Gate 0 심의 대상이다. |
| `SecurityAccessDeniedHandler.java` | 폐기 후 재작성 | 문자열 코드 `AUTH_FORBIDDEN`은 NFR-04 위반이다. M1에서 실제 403을 만드는 `/api/v1/admin/**`에는 기존 `ADMIN_001`을 사용하도록 정책화한다. |
| `OpenApiConfig.java` | 현 M0 유지·확장 | 레거시 파일을 덮지 않고 현재 Bearer scheme에 `refresh_token` cookie scheme과 refresh/signout 설명을 추가한다(`docs/REQUIREMENTS.md NFR-05`). |
| `application*.yml` | 값·구조만 참고 | 레거시 DB/AWS 설정과 실제 같은 secret 예시는 폐기한다. 현 M0 설정에 JWT/cookie만 최소 추가한다. |
| `V1__init.sql` | 폐기 | 현재 M0 V1이 정본이다. 레거시 V1 사용·수정 금지(`RISK-0003`). |
| 공통 응답·예외·BaseEntity·CORS 등 M0 중복 파일 | 폐기 | 현재 `dev`의 M0 구현을 유지한다. |

#### 1.2 프론트엔드

| 레거시 파일 | 판정 | 재사용 범위와 주의점 |
|---|---|---|
| `lib/apiClient.ts` | 수정 후 취지 재사용 | Bearer, `credentials:"include"`, 401 refresh single-flight, 원 요청 1회 재시도, public auth refresh 제외, unauthorized callback 취지를 재사용한다. `any`, 가짜 client error code(`AUTH_REQUIRED`, `PARSE_ERROR`), `window.location` 직접 결합은 폐기한다. |
| `lib/authContext.tsx` | 재작성 | 메모리 token, 시작 시 refresh→me, signin/signout 흐름은 재사용한다. callback 순환·StrictMode 중복 초기화·오류 삼키기 문제를 제거하고 초기 복구도 single-flight로 만든다. |
| `types/auth.ts` | 수정 재사용 | `AuthUser`, `DefaultBlog`, token response 구조를 현재 API 계약에 맞춰 사용한다. |
| `ProtectedRoute.tsx`, `GuestOnlyRoute.tsx`, `SetupGuard.tsx` | 수정 재사용 | redirect 조건은 재사용한다. 영문 `Loading...` 대신 접근 가능한 공용 로딩 상태를 사용한다. `SetupGuard`는 `/blog/setup` 자체와 그 외 보호 경로의 방향을 구분한다(`docs/PRD.md §8.3`). |
| `AdminRoute.tsx` | M1 제외·참고만 | PRD §10 M1 산출물에는 없고 관리자 구현은 M9이다. 단, 현 라우트가 이미 존재하므로 M1에서 인증 상태를 깨지 않도록 미적용 상태를 유지한다(`docs/PRD.md §10 M9`). |
| `SigninPage.tsx`, `SignupPage.tsx` | 로직 사례만 참고 | 레거시 화면은 폐기된 디자인과 M0 이전 컴포넌트 위에 있으므로 JSX/CSS는 폐기한다. 제출 중 disabled, 서버 generic 오류, 성공 redirect 테스트 취지만 재사용한다. |
| `router.tsx`와 guard 테스트 | 참고 후 현 M0에 재적용 | 레거시 라우터를 덮지 않고 현 15라우트에 wrapper를 삽입한다. |
| `App.css`, 과거 layout/UI/pages, package·lock·Vite 설정 | 폐기 | 현 M0 React 19.2/Vite 8/Tailwind v4/정본 디자인 구현을 유지한다. |
| `OnboardingScaffold`에 대응하는 레거시 없음 | 현 M0 확장 | 다크 배경·별·로켓·카드 프레임을 공유하는 shell로 리팩터링하고 Signin/Signup이 420px auth variant를 사용한다. `/blog/setup`은 560px setup variant 외형만 공유하며 실제 초기설정 폼/API는 M2에 남긴다(`docs/design/DESIGN-SYSTEM.md §8.5·§8.6`, `docs/PRD.md §10 M2`). |

#### 1.3 레거시 테스트

- 다음 테스트 파일은 **테스트 이름·경계 사례를 재사용하고 현 M0 테스트 기반 위에서 다시 작성**한다:  
  `AuthControllerTest`, `AuthServiceTest`, `RefreshTokenServiceTest`, `JwtProviderTest`, `RefreshTokenHasherTest`, `SecurityConfigAuthTest`, `SecurityAuthenticationEntryPointTest`, `UserRepositoryTest`, `BlogRepositoryTest`, `CategoryRepositoryTest`, `RefreshTokenRepositoryTest`, `SlugGeneratorTest`, `apiClient.test.ts`, `authContext.test.tsx`, `SigninPage.test.tsx`, `SignupPage.test.tsx`, `router.test.tsx`.
- 반드시 유지할 과거 수정 취지는 다음과 같다.
  - `AUTH_003`은 cookie 없음·만료·폐기·hash 불일치 등 refresh 실패 경로에서 실제 사용.
  - 30자 base slug 충돌 시 suffix 포함 총 길이 30자 이하.
  - signin/register 401은 자동 refresh를 호출하지 않음.
  - 정지 계정은 비밀번호 검증 전 상태를 노출하지 않음.
  - 401/403는 status뿐 아니라 공통 응답의 code/message까지 검증.
  - Signin submit 중 disabled 및 실제 navigation을 검증.
- 레거시 `docs/worklog/M1-auth.md`는 참고 자산으로만 유지하며 현재 worklog로 복사하지 않는다.

---

### 2. Scope Lock

| 항목 | M1 포함 | M1 제외·후속 |
|---|---|---|
| 엔티티 | `User`, `Blog`, `Category`, `RefreshToken` 전체 V1 매핑. 가입·인증에 필요한 factory/상태 메서드만 구현 | Post 이하 엔티티. Blog 수정, Category CRUD/깊이/삭제 정책은 M2/M3 |
| Repository | 위 4개 엔티티의 가입·signin·`/auth/me`·rotation용 최소 조회 | QueryDSL, 공개 블로그/카테고리 목록 쿼리 |
| 인증 API | `/api/v1/auth/register`, `/signin`, `/signout`, `/refresh`, `/me` | OAuth, 이메일 인증, 비밀번호 재설정, 기기별 세션 목록, Access blacklist |
| 사용자 조회 | **`GET /auth/me`만 M1** | `GET/PUT /users/me`, 비밀번호 변경은 M2(`docs/PRD.md §5.2·§10 M2`) |
| 블로그 | 가입 시 기본 Blog 생성과 `/auth/me`의 최소 defaultBlog 응답 | `/blogs/me`, initial-setup, 공개 slug 조회는 M2 |
| 카테고리 | 가입 시 `미분류`, `type=DEFAULT`, `displayOrder=0` 생성 | 카테고리 CRUD/order/정책 API는 M3 |
| Security | JWT filter, public allowlist, 기본 authenticated, admin role matcher, JSON 401/403, CORS/cookie 정책 | 세부 도메인 소유권 검사는 각 도메인 마일스톤 |
| FE 인프라 | AuthProvider, apiClient, ProtectedRoute, GuestOnlyRoute, SetupGuard | AdminRoute 실제 적용은 M9 |
| FE 화면 | Signin/Signup 실제 폼과 API 연동, LOGIN 정본 일치 | BlogSetup 실제 폼/API는 M2 |
| FE 검증 | 로그인 email/required, 회원가입 email·password(8자+영문+숫자+특수문자)·nickname 2~20·name required. `birth_date`는 선택이며 값이 있으면 유효한 date 형식만 확인 | 명세가 없는 name 길이, 생년월일 과거/나이 제한, 비밀번호 확인 필드는 발명하지 않음 |

---

### 3. 백엔드 작업 목록

#### 3.1 의존성과 설정

- `build.gradle`
  - 심의 승인 후 아래를 같은 버전으로 고정한다.
    - `implementation "io.jsonwebtoken:jjwt-api:0.13.0"`
    - `runtimeOnly "io.jsonwebtoken:jjwt-impl:0.13.0"`
    - `runtimeOnly "io.jsonwebtoken:jjwt-jackson:0.13.0"`
  - Lombok은 추가하지 않는다.
- `application.yml`
  - `zeroverse.jwt.secret-base64: ${JWT_SECRET_BASE64}` — fallback 없음.
  - access TTL `PT1H`, refresh TTL `P14D`.
  - cookie name `refresh_token`, path `/api/v1/auth`, HttpOnly 고정, Secure 기본 true, SameSite 기본 Strict, max-age 14일.
- `application-local.example.yml`
  - 실제 값 대신 placeholder만 둔다. `application-local.yml`에서 `secure:false` 허용.
- `application-test.yml`
  - 테스트 전용 고정 Base64 key와 짧은 TTL override 가능. 운영 비밀로 사용할 수 없음을 주석으로 명시한다.
- 운영 secret은 최소 256-bit random key를 Base64로 주입한다. 저장소, 예제 파일, 프론트 환경변수에는 넣지 않는다(`docs/REQUIREMENTS.md NFR-01`, `docs/PRD.md §4.3`).

#### 3.2 파일 단위 구현

```text
src/main/java/com/zeroverse/
├─ common/util/SlugGenerator.java
├─ security/
│  ├─ ZeroverseUserPrincipal.java
│  ├─ SecurityAuthenticationEntryPoint.java
│  ├─ SecurityAccessDeniedHandler.java
│  └─ jwt/
│     ├─ JwtProperties.java
│     ├─ JwtProvider.java
│     └─ JwtAuthenticationFilter.java
├─ domain/
│  ├─ user/entity/User.java, UserRole.java, UserStatus.java
│  ├─ user/repository/UserRepository.java
│  ├─ blog/entity/Blog.java
│  ├─ blog/repository/BlogRepository.java
│  ├─ category/entity/Category.java, CategoryType.java
│  ├─ category/repository/CategoryRepository.java
│  └─ auth/
│     ├─ controller/AuthController.java
│     ├─ service/AuthService.java, RefreshTokenService.java
│     ├─ dto/RegisterRequest.java, SigninRequest.java
│     ├─ dto/AuthTokenResponse.java, AuthMeResponse.java, DefaultBlogResponse.java
│     ├─ entity/RefreshToken.java
│     ├─ repository/RefreshTokenRepository.java
│     ├─ config/AuthCookieProperties.java
│     └─ support/RefreshTokenHasher.java, RefreshTokenCookieFactory.java
└─ config/SecurityConfig.java, OpenApiConfig.java
```

#### 3.3 JWT와 rotation 계약

- 서명: HS256, 명시적 허용 알고리즘 1개.
- 공통 claim: `sub=userId`, `jti=UUID`, `type=ACCESS|REFRESH`, `iat`, `exp`.
- Access 추가 claim: `role`.
- Refresh의 새 Access 발급 시 role/status는 refresh claim을 신뢰하지 않고 DB의 현재 User를 다시 조회한다.
- Access 1시간, Refresh 2주(`docs/PRD.md §4.3`, `FR-AUTH-02`).
- rotation은 하나의 트랜잭션에서 수행한다.
  1. cookie 존재 및 JWT 서명/type/exp 검증.
  2. `jti`로 DB row를 `PESSIMISTIC_WRITE` 조회.
  3. DB `expires_at`, `revoked_at`, user active/deleted, constant-time hash 일치 검증.
  4. 기존 row `revoked_at` 기록.
  5. 새 Access·Refresh 발급, 새 Refresh hash row 저장.
  6. 트랜잭션 성공 후 새 cookie 반환.
- 동시 refresh 두 건 중 첫 건만 성공하고 두 번째는 lock 해제 후 revoked 상태를 읽어 `AUTH_003`으로 실패해야 한다.
- 재사용 탐지 시 사용자 전체 세션 폐기 기능은 요구사항에 없으므로 추가하지 않는다. 현재 token 거부와 보안 로그만 남긴다.
- signout은 cookie가 없거나 이미 무효여도 cookie를 제거하는 idempotent 성공으로 처리하고, 유효한 DB row가 있으면 revoke한다(`FR-AUTH-03`).

#### 3.4 가입과 로그인

- register 단일 트랜잭션:
  - User: role=USER, status=ACTIVE, `name` 필수, `birthDate` nullable.
  - BCrypt strength 12.
  - Blog: `{nickname}의 블로그`, normalized unique slug, `isSetupCompleted=false`.
  - Category: `미분류`, DEFAULT, root, order 0.
  - 어느 저장 단계든 실패하면 전부 rollback(`FR-AUTH-01`, `docs/REQUIREMENTS.md §4 설계 원칙`).
- signin:
  - 없는 email·틀린 password는 동일 `AUTH_001`.
  - SUSPENDED 판정은 password 일치 후 수행하고 `USER_003`.
  - deleted user는 없는 계정과 동일하게 처리한다.
- register 응답은 빈 성공 응답으로 두고 FE가 성공 후 signin을 한 번 호출하는 것을 기본안으로 한다. register가 token을 직접 발급하는 새 계약은 만들지 않는다.

#### 3.5 SecurityConfig와 에러코드

- permitAll:
  - `/api/v1/auth/register|signin|refresh|signout`
  - `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`
  - 명세상 공개인 `/api/v1/blogs/slug/**`, `/api/v1/feed/public`, `/api/v1/search`
  - CORS preflight OPTIONS
- `/api/v1/admin/**`는 `hasRole("ADMIN")`.
- 그 외 `/api/v1/**`는 authenticated.
- JWT filter는 `UsernamePasswordAuthenticationFilter` 앞에 둔다.
- `AUTH_001`: signin 실패 및 심의가 승인하는 범위의 일반 Access 인증 실패.
- `AUTH_002`: 만료된 Access Token.
- `AUTH_003`: refresh cookie 부재·형식/서명/type 오류·만료·폐기·hash 불일치.
- `USER_001`: 인증 후 `/auth/me` 조회 시 사용자가 실제로 존재하지 않는 도메인 예외에만 사용.
- `USER_002`: nickname 중복.
- `USER_003`: 비밀번호가 맞는 SUSPENDED signin.
- `BLOG_001`: `/auth/me`에서 기본 블로그가 없는 데이터 무결성 오류.
- `BLOG_002/003`: slug 생성 충돌/형식 정책 위반에 해당할 때만 사용.
- 관리자 권한 부족은 기존 `ADMIN_001`.
- **차단점**: email 중복에 대응하는 409 코드가 현 `ErrorCode`/NFR-04에 없다. 과거 레거시의 `USER_005/006`, `AUTH_004`, `AUTH_FORBIDDEN`은 사용 금지다. 구현 전 심의와 REQUIREMENTS NFR-04 개정이 필요하다(`docs/REQUIREMENTS.md NFR-04`, 사용자 제약).

---

### 4. 프론트엔드 작업 목록

#### 4.1 인증 타입과 상태

- `frontend/src/types/auth.ts`
  - `AuthUser`, `DefaultBlog`, Register/Signin/Token/Me DTO.
  - register payload는 `birth_date?: string | null`로 계약을 명시한다.
- `frontend/src/lib/authContext.tsx`
  - `accessToken`은 React/module memory에만 보관.
  - 초기화: refresh single-flight → 성공 시 `/auth/me`; 실패는 비로그인 정상 상태.
  - signin 성공 후 `/auth/me`를 로드.
  - signout은 서버 호출 결과와 관계없이 로컬 token/user를 제거.
  - localStorage/sessionStorage/document.cookie 사용 금지(`docs/PRD.md §8.1`).

#### 4.2 apiClient

- `credentials:"include"`를 모든 요청에 적용한다.
- Access가 있을 때만 Bearer를 붙인다.
- 보호 요청의 401에만 refresh single-flight를 실행한다.
- `/auth/register`, `/auth/signin`, `/auth/refresh`, `/auth/signout`의 401은 재귀 refresh 대상이 아니다.
- refresh 성공 시 대기 요청 모두 새 token으로 각 1회 재시도한다.
- 재시도도 401이거나 refresh 실패면 상태를 1회 정리하고 `/signin`으로 이동한다.
- 서버 공통 래퍼를 typed unwrap하며 서버에 없는 가짜 공개 에러코드를 만들지 않는다(`docs/PRD.md §8.2`, `docs/REQUIREMENTS.md §8.3`).

#### 4.3 라우팅 가드

- `ProtectedRoute`: loading 종료 후 미인증이면 `/signin`.
- `GuestOnlyRoute`: 인증 사용자는 미설정이면 `/blog/setup`, 설정 완료면 `/`.
- `SetupGuard`:
  - 미인증 → `/signin`.
  - `/blog/setup`에서 이미 설정 완료 → `/`.
  - 미설정 사용자가 다른 보호 페이지로 가면 `/blog/setup`.
- M1 보호 대상: `/blog/setup`, `/write`, `/edit/:postId`, `/settings*`, `/notifications`.
- 공개·선택 인증 경로 `/`, 공개 blog/post, `/search`는 강제 로그인하지 않는다.
- 관리자 FE 가드의 실제 적용은 M9로 남긴다(`docs/PRD.md §7.1·§8.3·§10 M9`).

#### 4.4 SigninPage / SignupPage

- 현 `OnboardingScaffold`를 다크 배경·별 14개·픽셀 로켓·card shell로 확장한다.
- auth variant는 width 420px, `#fff8ec`, 3px ink border, dark shadow를 사용한다.
- `/signin`과 `/signup`은 동일 카드의 2분할 탭이며 탭 클릭으로 route를 바꾼다.
- Signin:
  - 이메일/비밀번호, `접속하기 ✦`, generic 오류, 제출 중 disabled.
- Signup:
  - 닉네임/name/email/password/birth_date.
  - `name`은 필수, `birth_date`는 선택.
  - 모든 필드는 `FormField` §7.7 스타일.
  - CTA `나의 별 만들기 ✦`.
  - 성공 후 자동 signin하고 defaultBlog 미설정 상태에 따라 `/blog/setup`.
- 디자인 원본의 비밀번호 placeholder가 영문+숫자만 언급하지만 실제 요구사항은 특수문자까지 필수이므로 힌트는 `8자 이상, 영문·숫자·특수문자 포함`으로 명확히 한다. 이는 저장·검증 요구가 시각 카피보다 우선하는 경계다(`docs/PRD.md §5.1·§7.2·§9.0`, `FR-AUTH-01`).
- `/blog/setup`은 M1에서 guard 도착점과 정본 shell만 유지한다. 실제 form submit이나 가짜 성공 동작은 추가하지 않는다(`docs/PRD.md §10 M2`).

---

### 5. 테스트 계획

placeholder, `skip`, `@Disabled`, stub 성공 응답을 금지한다(`docs/PRD.md §11·§12`, `docs/REQUIREMENTS.md NFR-09`).

#### 5.1 BE 단위

- RegisterRequest: email, password 8자+영문+숫자+특수문자, nickname 2~20, name required, birth_date null 허용.
- BCrypt strength 12 사용 및 raw password 미저장.
- register 성공 시 User 1 + Blog 1 + DEFAULT Category 1.
- 중간 실패 시 전체 rollback.
- signin: 없는 email/오류 password 동일 `AUTH_001`; suspended+오류 password도 동일; suspended+정상 password만 `USER_003`.
- slug: 한글/공백/대문자/특수문자/예약어/2자/빈 정규화/충돌/30자 base 충돌.
- JWT: claim, 서명 알고리즘, access 1h, refresh 2w, wrong type, malformed, wrong signature, expiry.
- hash: raw 미저장, SHA-256, constant-time match, 장문 JWT.
- rotation: 정상 회전, 이전 token 폐기, 만료·폐기·hash 불일치·token type 불일치, 동시 2요청 중 1건만 성공.
- signout revoke와 idempotent cookie clear.

#### 5.2 Repository/JPA — Testcontainers MySQL 8.4

- User/Blog/Category/RefreshToken 전체가 `ddl-auto=validate` 통과.
- email/nickname/url_slug/token_id unique.
- User→Blog→Category 및 User→RefreshToken 연관 저장/조회.
- createdAt/updatedAt auditing.
- User/Blog/Category soft-deleted row의 로그인·활성 조회 제외.
- unique 검사와 삭제 row 정책이 실제 V1 unique와 일치.
- RefreshToken row lock 및 revoke update.
- V1은 수정하지 않고 테스트로 불일치만 검출한다(`RISK-0001·0003`).

#### 5.3 Controller/Security

- register validation 400 + `VALIDATION_001` field details.
- signin 성공 body + refresh cookie의 HttpOnly/Secure/SameSite/Path/Max-Age.
- signin 실패 code/message 비열거.
- `/auth/me`:
  - token 없음 401.
  - malformed/invalid Access 401.
  - expired Access 401 + `AUTH_002`.
  - valid Access 200.
  - Refresh token을 Bearer로 사용 시 401.
- `/api/v1/admin/**`:
  - 미인증 401.
  - USER Access 403 + `ADMIN_001`.
  - ADMIN Access는 보안 체인을 통과한 뒤 미구현 경로 `COMMON_404`.
- refresh cookie 없음/무효/폐기/만료 401 + `AUTH_003`.
- 공통 응답 4키와 UTC timestamp.
- Swagger Bearer·refresh cookie scheme.
- **위 401/403 검사는 RISK-0002 종료의 명시적 회귀 게이트다. `anyRequest().permitAll()` 또는 보호 API 무토큰 2xx가 한 건이라도 나오면 M1 미완료다.**

#### 5.4 BE 통합

- register → DB 기본 Blog/Category 확인 → signin → `/auth/me`.
- signin → refresh → 이전 refresh 재사용 실패.
- signin → refresh → signout → 마지막 refresh 재사용 실패.
- M2/M4 API가 필요한 “초기설정→글 작성→공개 조회” 전체 시나리오는 M1에서 가짜 구현하지 않고 해당 마일스톤으로 남긴다(`docs/PRD.md §11`, `docs/PRD.md §10 M2·M4`).

#### 5.5 FE

- AuthContext: 초기 refresh 성공→me, 실패 비로그인, signin, signout, cleanup.
- apiClient: Bearer/credentials, 보호 요청 401 refresh+retry, concurrent 401 single-flight, public signin/register 401 미갱신, refresh 실패 redirect, retry 최대 1회.
- Guards: loading, guest, setup incomplete, setup completed 경로별 redirect.
- Signin: validation, generic error, disabled/중복 submit 방지, 성공 navigation.
- Signup: name 필수, birth_date 빈 값 허용, password 정책, `birth_date` payload, register→signin→setup redirect.
- 시각 구조: 420px 카드, 탭, 정본 카피, FormField, 별/로켓, radius 0, dark shadow.
- 브라우저 1440px에서 LOGIN 원본과 나란히 대조하고 screenshot 근거를 worklog `[개발 기록]`에 남긴다(`docs/design/DESIGN-SYSTEM.md §8.5`, `docs/PRD.md §12.4`).

---

### 6. 작업 순서와 게이트

| Gate | 작업 | 통과 조건·검증 |
|---|---|---|
| 0. 심의·계약 | JJWT 도입, 에러코드 gap, cookie/CSRF·register 성공 흐름 승인 | 회의록 `APPROVED` 또는 `AUTO_APPROVED`; 필요한 NFR-04 개정 선행 |
| 1. 도메인/JPA | User·Blog·Category·RefreshToken, Repository, slug | `.\gradlew.bat test --tests "*RepositoryTest" --tests "*SlugGeneratorTest"` |
| 2. JWT/Rotation | properties, provider, hasher, row-lock rotation, cookie | `.\gradlew.bat test --tests "*JwtProviderTest" --tests "*RefreshToken*Test"` |
| 3. Auth API | DTO, service, controller, register/signin/refresh/signout/me | `.\gradlew.bat test --tests "*AuthServiceTest" --tests "*AuthControllerTest"` |
| 4. Security | `permitAll` 제거, JWT filter, 401/403, OpenAPI | `.\gradlew.bat test --tests "*Security*Test" --tests "*OpenApiConfigTest"`; RISK-0002 케이스 전부 green |
| 5. FE 인프라 | auth types/context/apiClient/guards | `cd frontend; npm.cmd run test -- apiClient authContext router` |
| 6. FE 화면 | OnboardingScaffold auth variant, signin/signup | `cd frontend; npm.cmd run test -- SigninPage SignupPage`; 1440px 정본 시각 대조 |
| 7. 종합 | 전체 회귀·빌드·린트·금지 패턴 검사 | `.\gradlew.bat clean test build`; `cd frontend; npm.cmd run test; npm.cmd run build; npm.cmd run lint`; `rg -n "@Disabled|\\.skip\\(|\\.todo\\(|TODO|FIXME" src frontend/src` 후 M1 범위 잔여 0건 |

각 Gate의 명령 결과·테스트 수·skip 0건을 `docs/worklog/M1-auth.md [개발 기록]`에 append한다. V1 diff가 발생하면 즉시 중단한다.

---

### 7. 위험과 미확정 사항

#### 7.1 사용자 결정·심의가 필요한 사항

| 항목 | 필요한 정보·결정 | 영향 |
|---|---|---|
| email 중복 코드 | 현재 `ErrorCode`에는 email 중복 409가 없다. `USER_004` 등 새 코드를 NFR-04에 추가할지 승인 필요 | 승인 전 register 중복 계약 구현 불가 |
| 일반 Access 401 의미 | `AUTH_001`을 signin 실패뿐 아니라 token 없음/서명 오류에도 사용할지, 별도 인증필요 코드를 추가할지 결정 필요 | Security entry point의 공개 API 계약 |
| JJWT 0.13.0 | 새 외부 라이브러리 도입 승인 필요 | Gate 2 선행 |
| CSRF/cookie posture | Bearer API는 CSRF off, refresh/signout은 SameSite=Strict+narrow path+CORS credentials로 보호하는 기본안을 승인할지 검토 필요 | 인증 보안 정책 |
| 자동 slug fallback | nickname 정규화 결과가 비거나 예약어이면 `blog`, `blog-2`…로 생성하는 기본안 승인 필요 | FR-AUTH-01 기본 블로그 생성 |
| 가입 후 UX | register 빈 성공 후 FE가 signin을 호출해 `/blog/setup`으로 이동하는 기본안 승인 필요 | 공개 응답 shape를 변경하지 않고 PRD 화면 흐름 충족 |

#### 7.2 별도 질문 없이 기본값으로 진행 가능한 사항

- 운영 cookie `Secure=true`, local profile만 false, `SameSite=Strict`, path `/api/v1/auth`.
- Access 1시간, Refresh 2주.
- refresh token raw JWT 전체를 SHA-256 hash하고 jti로 row 조회.
- rotation row lock을 사용해 동시 갱신을 단일 성공으로 제한.
- `birth_date`는 nullable이며 폼에는 선택 필드로 노출.
- register 응답에는 token을 넣지 않음.
- `/users/me`와 BlogSetup 실제 API는 M2.
- V1 변경 없음.
- 과거 레거시에서 사용한 `AUTH_004`, `AUTH_FORBIDDEN`, `USER_005`, `USER_006`은 폐기.

---

### 8. 기획 심의 소집 판정

#### 일반 소집 조건

| 조건 (`docs/governance/README.md §2`) | 해당 |
|---|---:|
| 인증·권한·개인정보·보안 정책 변경 | **해당** — JWT, cookie, rotation, 401/403 도입 |
| DB 스키마 또는 공개 API 계약 변경 | **해당** — FR-AUTH 5개 계약을 처음 구현하며 NFR-04 코드 추가 가능성 |
| 새 외부 라이브러리 도입 | **해당** — JJWT |
| PRD/REQUIREMENTS 변경 | **조건부 해당** — email 중복 에러코드 추가 시 필수 |
| 선행 마일스톤 설계 수정 | **해당** — M0 임시 `permitAll()` 교체 및 RISK-0002 종료 |
| 디자인과 기능 요구 충돌 | 해결됨 — `name`·`birth_date`는 §9.4-AA로 확정 |

따라서 구현 전 심의를 반드시 소집한다.

#### 대형 마일스톤 조건

- 백엔드·프론트엔드 두 영역 동시 변경: 해당.
- User·Blog·Category·RefreshToken 4개 도메인 영향: 해당.
- 인증·접근제어 포함: 해당.
- 선행 M0 보안 설정 교체: 해당.

2개 이상 기준을 넘어 **4개 조건에 해당하므로 대형 마일스톤**이다(`docs/governance/README.md §2`).

#### 심의 핵심 질문 — 한 문장

> **M1을 선택지 A(권고: JJWT 0.13.0·HS256, row-lock refresh rotation, SameSite=Strict cookie, register 후 FE 자동 signin을 채택하고 NFR-04에 email 중복 및 필요한 Access 인증 코드를 최소 추가) 또는 선택지 B(새 에러코드 없이 AUTH_001~003과 기존 USER_/ADMIN_ 코드만 재사용) 중 어느 계약으로 구현할지 결정하되, `AUTH_001~003`의 확정 의미·V1 불변·`name` 필수/`birth_date` nullable·RISK-0002 401/403 회귀 게이트를 제약으로 하고 OAuth·이메일 인증·기기별 세션·재사용 시 전체 세션 폐기·M2 `/users/me`/초기설정 API는 제외할 것인가?**

선택지 B는 email 중복 409를 정확하게 표현할 기존 코드가 없어 공개 오류 계약의 의미 왜곡 위험이 있으므로 권고하지 않는다. 심의 결과가 `LOW`가 아니면 사용자 승인 전 Gate 1 구현을 시작하지 않는다(`docs/governance/README.md §3·§5`).

---

## [개발 기록]

- 아직 없음. 기획 심의 승인 후 착수한다.

## [이슈·결정]

- 2026-07-25 · PRD §9.4-AA 확정 — 회원가입 `name`·`birth_date` 유지(사용자 결정). §9.3-① 종결.
- 2026-07-25 · 기획 심의 소집: 대형 마일스톤 **4개 조건** 해당(BE·FE 동시 / User·Blog·Category·RefreshToken 4개 도메인 / 인증·접근제어 포함 / M0 보안 설정 교체). `docs/governance/README.md` §2.
- 2026-07-25 · 기획 심의 `M1-20260725-auth` 종료 — 회의록 [`docs/governance/meetings/M1-20260725-auth.md`](../governance/meetings/M1-20260725-auth.md). 독립 검토 3인 전원 `APPROVE_WITH_CHANGES`(확신도 92·93·유사), 제안 등급 전원 `HIGH`. 진행자 최종 등급 **`HIGH`**, 상태 **`USER_DECISION_REQUIRED`**.
  - 1차 결정 질문: 인증 오류 계약 A(신규 코드 추가) / B(기존 코드 재사용) / C. 검토자·진행자 **전원 A 권고** — B는 email 중복 409를 표현할 기존 코드가 없어 공개 계약 의미가 왜곡된다.
  - 구현 전 필수 변경 **15건**, 사용자 결정 항목 **8건**은 회의록 §7~§8 참조.
  - **미제공 정보**: 운영 FE/API의 scheme·host·site·HTTPS 관계. `docs/` 전수 검색 결과 배포 환경 정보가 문서에 없다. cross-site로 확인되면 `SameSite=Strict` 계약은 별도 재심의 대상이다.
  - ADR-0003 `M1 인증 토큰·세션 및 오류 계약` — 번호만 예약, 사용자 승인 후 작성.

## [리뷰]

- 아직 없음.

## [머지]

- 아직 없음.
