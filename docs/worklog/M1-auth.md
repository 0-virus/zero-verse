# M1 — 인증/인가

- **브랜치**: `feature/M1-auth` (base: `dev`)
- **범위 (PRD §10 M1)**: 백엔드 FR-AUTH-01~05 구현 + JWT/Security 인증 인프라 + 인증 도메인 엔티티(User/Blog/Category/RefreshToken) + 프론트 AuthContext/API client/인증 페이지/라우팅 가드 실연동.
- **기준 문서**: `docs/PRD.md` §4.3, §5.1, §8, §9, §10 / `docs/REQUIREMENTS.md` FR-AUTH, NFR-01~09 / `docs/worklog/M0-scaffold.md`.
- **M0 기반 상태**: Spring Boot 3.5.15, Java 21, Gradle 8.10, MySQL 8.4 Testcontainers, Flyway V1 전체 스키마, 공통 응답/예외, Security 401/403 핸들러, BCrypt(12) `PasswordEncoder`, React 19/Vite/Tailwind v4, 라우터/가드/AuthContext/apiClient 골격 존재.

## [계획] (Codex · 2026-07-01 22:50 KST)

### A. 범위 잠금

**백엔드**
- `POST /api/v1/auth/register`: email unique, password 8자 이상+영문/숫자/특수문자, nickname 2~20 unique, name/birth_date 필수. 비밀번호는 BCrypt strength 12. 가입 트랜잭션 안에서 기본 블로그(`{nickname}의 블로그`, nickname 기반 `url_slug`, `is_setup_completed=false`)와 기본 카테고리(`name="미분류"`, `type=DEFAULT`, `display_order=0`) 자동 생성.
- `POST /api/v1/auth/signin`: 이메일/비밀번호 검증, `SUSPENDED` 로그인 거부, 실패 메시지는 계정 존재 여부 비노출. Access Token은 body, Refresh Token은 HttpOnly Secure SameSite cookie.
- `POST /api/v1/auth/signout`: 현재 Refresh Token revoke(`revoked_at`) 후 cookie clear. Access Token은 서버 저장소에서 강제 폐기하지 않음.
- `POST /api/v1/auth/refresh`: Refresh rotation 필수. 기존 token은 즉시 revoke, 새 Access+Refresh 발급. DB에는 Refresh 평문 저장 금지, `token_id`(JWT `jti`)와 `token_hash`만 저장. 폐기/만료/해시 불일치 token 재사용 불가.
- `GET /api/v1/auth/me`: 현재 사용자 기본 정보와 기본 블로그 정보를 반환해 FE 세션 복구와 `SetupGuard` 판단에 사용.
- `JwtProvider`: Access 1h, Refresh 2w. `sub=userId`, `jti`, `role`, `type=ACCESS|REFRESH`, `iat`, `exp` claim 사용.
- `JwtAuthenticationFilter`: Bearer Access Token 검증 후 `SecurityContext` 세팅. `/auth/register`, `/auth/signin`, `/auth/refresh`, `/auth/signout`, Swagger/public endpoint는 필터 처리에서 제외하되 `/auth/me`는 인증 필요.
- `SecurityConfig`: `/api/v1/auth/register|signin|refresh|signout`는 permitAll, `/api/v1/auth/me`는 authenticated. 기존 401/403 공통 JSON 핸들러 유지.
- 도메인 엔티티와 repository: `User`, `Blog`, `Category`, `RefreshToken`을 V1 스키마에 맞춰 추가하고 테스트로 매핑/제약 검증.

**프론트엔드**
- `AuthContext`: Access Token은 React state와 apiClient token store에만 보관. 앱 초기화 시 `/auth/refresh` cookie로 Access 복구 후 `/auth/me`로 user+defaultBlog 로드. Refresh 실패 시 인증 상태 초기화.
- `apiClient`: 모든 요청 `credentials:'include'`, Access 존재 시 Bearer 부착. 401 발생 시 refresh single-flight로 1회만 자동 갱신하고 원 요청 1회 재시도. 갱신 실패 시 AuthContext 초기화 및 `/signin` 이동.
- `SigninPage`: `/auth/signin` 연동, 계정 존재 여부를 드러내지 않는 오류 문구, 성공 후 `/auth/me` 로드 및 `is_setup_completed=false`면 `/blog/setup`, 완료면 `/`.
- `SignupPage`: email/name/password/nickname/birth_date 입력. nickname 2~20, birth_date 필수, password 정책 클라이언트 검증. 요청 JSON 필드는 spec에 맞춰 `birth_date` 사용. 성공 후 자동 로그인 또는 로그인 페이지 이동 정책은 아래 결정 필요 항목에 따른다.
- `ProtectedRoute`, `GuestOnlyRoute`, `SetupGuard`: `isLoading` 중 대기, 미인증 보호, 인증 사용자의 signin/signup 진입 차단, 로그인했지만 기본 블로그 `is_setup_completed=false`면 `/blog/setup` 강제. `/blog/setup` 자체는 미완료 사용자만 통과.

**M1 제외**
- `/blogs/me/initial-setup`, `/users/me`, 공개 블로그 조회, 카테고리 CRUD, 게시글/업로드/유니버스/알림/관리자 구현은 M2 이후.
- OAuth, 이메일 인증, 비밀번호 재설정, Refresh Token device/session 목록 관리, token blacklist 기반 Access 강제 폐기는 MVP M1 범위 밖.

### B. 작업 순서

1. **계약 고정 및 테스트 스켈레톤 작성**
   - Register/signin/refresh/signout/me request/response DTO와 cookie 이름, `GET /auth/me` 응답 shape를 먼저 정의한다.
   - BE 단위/Controller/JPA 테스트명을 먼저 추가해 FR-AUTH/NFR 체크 항목을 명시한다.
   - FE AuthContext/apiClient/guard/page 테스트에서 기대 흐름을 먼저 작성한다.
2. **백엔드 도메인 엔티티 매핑**
   - `User`, `Blog`, `Category`, `RefreshToken` 엔티티를 V1 컬럼에 맞춰 구현한다.
   - enum: `UserRole(USER,ADMIN)`, `UserStatus(ACTIVE,SUSPENDED)`, `CategoryType(DEFAULT,GENERAL,LOCKED)`.
   - soft delete 컬럼이 있는 User/Blog/Category는 `BaseSoftDeleteEntity`; RefreshToken은 V1에 `updated_at`이 없으므로 별도 `createdAt` 매핑.
3. **Repository/JPA 검증**
   - email/nickname/url_slug/token_id unique, RefreshToken revoke/expiry 조회, User-Blog-Category 저장/조회, auditing 생성값을 Testcontainers MySQL 8.4에서 검증한다.
4. **JWT/Refresh Token 인프라**
   - `build.gradle`에 JWT 라이브러리 추가(`io.jsonwebtoken:jjwt-api` + runtime `jjwt-impl`, `jjwt-jackson`).
   - `JwtProperties`, `JwtProvider`, `RefreshTokenHasher`, cookie 유틸을 구현한다.
   - Refresh Token은 raw JWT 전체를 hash 비교 대상으로 사용하고, token lookup은 `jti`로 수행한다.
5. **AuthService 구현**
   - register는 하나의 트랜잭션으로 User/Blog/DEFAULT Category 저장.
   - signin은 generic auth failure와 SUSPENDED failure를 분리하되 사용자 탐색 여부는 응답 메시지에 노출하지 않는다.
   - refresh는 JWT signature/expiry/type 검증 → DB active token 조회 → hash match → old revoke → new token 저장/쿠키 발급 순서.
   - signout은 cookie가 없거나 이미 폐기된 경우에도 cookie clear를 반환하되, 유효 token이면 revoke한다.
6. **Controller/Security 연동**
   - `AuthController`에 5개 endpoint 추가, Bean Validation 적용, 공통 `ApiResponse` shape 보장.
   - `JwtAuthenticationFilter`를 `UsernamePasswordAuthenticationFilter` 앞에 추가.
   - `OpenApiConfig`에 Bearer/cookie auth 설명 보강.
7. **프론트 인증 상태 실제화**
   - API 타입과 response mapper를 정리하고 `AuthContext` user 타입에 `defaultBlog` 추가.
   - `apiClient`의 refresh single-flight와 AuthContext cleanup hook이 순환 의존을 만들지 않도록 token store + `onUnauthorized` callback 구조로 정리한다.
8. **인증 페이지와 가드 연결**
   - Signin/Signup 입력 검증과 서버 에러 표시를 한국어 UX로 정리한다.
   - `ProtectedRoute`, `GuestOnlyRoute`, `SetupGuard`에서 `defaultBlog.isSetupCompleted` 기준 redirect를 구현한다.
9. **검증 및 워크로그 append**
   - BE `./gradlew test`, 필요 시 `./gradlew build`.
   - FE `npm run test`, `npm run build`.
   - 실행 결과와 이슈를 이 파일의 후속 섹션에 timestamp로 append한다.

### C. 백엔드 파일 목록

**수정**
- `build.gradle`: JWT 의존성 추가.
- `src/main/resources/application.yml`: cookie 이름/secure/same-site/path/max-age 등 auth 설정 추가. secret fallback 금지 유지.
- `src/test/resources/application-test.yml`: 테스트용 auth cookie 설정과 JWT secret 보강.
- `src/main/java/com/zeroverse/config/SecurityConfig.java`: auth endpoint permit 범위 재정의, JWT filter 추가, `/auth/me` 인증 필수화.
- `src/main/java/com/zeroverse/config/OpenApiConfig.java`: Bearer Access Token과 Refresh cookie 흐름 문서화.
- `src/main/java/com/zeroverse/common/exception/ErrorCode.java`: 필요 시 `AUTH_004` refresh reuse/revoked, `AUTH_005` 인증 필요, `USER_005` email 중복 등 구체 코드 보강.

**신규 - domain**
- `src/main/java/com/zeroverse/domain/user/entity/User.java`
- `src/main/java/com/zeroverse/domain/user/entity/UserRole.java`
- `src/main/java/com/zeroverse/domain/user/entity/UserStatus.java`
- `src/main/java/com/zeroverse/domain/user/repository/UserRepository.java`
- `src/main/java/com/zeroverse/domain/blog/entity/Blog.java`
- `src/main/java/com/zeroverse/domain/blog/repository/BlogRepository.java`
- `src/main/java/com/zeroverse/domain/category/entity/Category.java`
- `src/main/java/com/zeroverse/domain/category/entity/CategoryType.java`
- `src/main/java/com/zeroverse/domain/category/repository/CategoryRepository.java`
- `src/main/java/com/zeroverse/domain/auth/entity/RefreshToken.java`
- `src/main/java/com/zeroverse/domain/auth/repository/RefreshTokenRepository.java`

**신규 - auth/security**
- `src/main/java/com/zeroverse/auth/controller/AuthController.java`
- `src/main/java/com/zeroverse/auth/service/AuthService.java`
- `src/main/java/com/zeroverse/auth/service/RefreshTokenService.java`
- `src/main/java/com/zeroverse/auth/security/JwtProvider.java`
- `src/main/java/com/zeroverse/auth/security/JwtAuthenticationFilter.java`
- `src/main/java/com/zeroverse/auth/security/ZeroverseUserPrincipal.java`
- `src/main/java/com/zeroverse/auth/security/RefreshTokenHasher.java`
- `src/main/java/com/zeroverse/auth/config/JwtProperties.java`
- `src/main/java/com/zeroverse/auth/config/AuthCookieProperties.java`
- `src/main/java/com/zeroverse/auth/support/RefreshTokenCookieFactory.java`
- `src/main/java/com/zeroverse/auth/dto/RegisterRequest.java`
- `src/main/java/com/zeroverse/auth/dto/SigninRequest.java`
- `src/main/java/com/zeroverse/auth/dto/AuthTokenResponse.java`
- `src/main/java/com/zeroverse/auth/dto/AuthMeResponse.java`
- `src/main/java/com/zeroverse/auth/dto/DefaultBlogResponse.java`

**신규/수정 - tests**
- `src/test/java/com/zeroverse/domain/user/UserRepositoryTest.java`
- `src/test/java/com/zeroverse/domain/blog/BlogRepositoryTest.java`
- `src/test/java/com/zeroverse/domain/category/CategoryRepositoryTest.java`
- `src/test/java/com/zeroverse/domain/auth/RefreshTokenRepositoryTest.java`
- `src/test/java/com/zeroverse/auth/service/AuthServiceTest.java`
- `src/test/java/com/zeroverse/auth/service/RefreshTokenServiceTest.java`
- `src/test/java/com/zeroverse/auth/security/JwtProviderTest.java`
- `src/test/java/com/zeroverse/auth/security/RefreshTokenHasherTest.java`
- `src/test/java/com/zeroverse/auth/controller/AuthControllerTest.java`
- `src/test/java/com/zeroverse/config/SecurityConfigAuthTest.java`
- 기존 `FlywayMigrationTest`: User/Blog/Category/RefreshToken 엔티티 매핑 검증을 별도 JPA 테스트로 옮기거나 보강.

### D. 프론트엔드 파일 목록

**수정**
- `frontend/src/lib/apiClient.ts`: typed request helper, 401 refresh single-flight, retry once, `onUnauthorized` callback, refresh endpoint 자체의 재귀 refresh 방지.
- `frontend/src/lib/apiClient.test.ts`: Bearer, credentials, single-flight, retry, failure redirect/cleanup 검증 확대.
- `frontend/src/lib/authContext.tsx`: 앱 초기 refresh→me, signin/signout/refresh cleanup, `defaultBlog` 포함 user 모델.
- `frontend/src/routes/ProtectedRoute.tsx`: 인증 상태 기준 redirect와 로딩 UI 정리.
- `frontend/src/routes/GuestOnlyRoute.tsx`: 인증 사용자는 기본 블로그 설정 여부에 따라 `/blog/setup` 또는 `/`로 이동.
- `frontend/src/routes/SetupGuard.tsx`: TODO 제거, `defaultBlog.isSetupCompleted` 기준 강제 redirect.
- `frontend/src/pages/SigninPage.tsx`: 실제 signin flow, 계정 privacy 오류, 성공 redirect.
- `frontend/src/pages/SignupPage.tsx`: nickname 2~20, birth_date 필수, password 정책, `birth_date` request 필드.
- `frontend/src/routes/router.test.tsx`: 보호/게스트/setup 라우팅 시나리오 보강.

**신규**
- `frontend/src/types/auth.ts`: `AuthUser`, `DefaultBlog`, auth request/response 타입.
- `frontend/src/lib/authContext.test.tsx`: init refresh→me, signin, signout, refresh 실패 cleanup 테스트.
- `frontend/src/pages/SigninPage.test.tsx`: 입력/submit/error/redirect 테스트.
- `frontend/src/pages/SignupPage.test.tsx`: validation, request payload(`birth_date`), success flow 테스트.
- 필요 시 `frontend/src/test/authTestUtils.tsx`: AuthProvider/router render helper.

### E. TDD 테스트 전략

**User 도메인**
- 회원가입 DTO validation: email 형식, password 정책, nickname 2~20, name blank 금지, birth_date null 금지.
- `PasswordEncoder` strength 12 사용과 raw password 미저장 검증.
- email/nickname unique 충돌 시 409와 USER/AUTH 계열 에러코드 검증.
- `SUSPENDED` 사용자는 signin 불가. 실패 응답은 email 존재 여부를 추론할 수 없는 동일 메시지.

**Blog/Category 도메인**
- register 성공 시 기본 Blog 1개 생성: title=`{nickname}의 블로그`, slug nickname 기반, 중복 suffix `-2`, `isSetupCompleted=false`.
- 기본 Category 생성: `name="미분류"`, `type=DEFAULT`, `displayOrder=0`, 같은 blog에 연결.
- User+Blog+Category 저장은 한 트랜잭션으로 묶여 중간 실패 시 모두 rollback.
- Category enum은 `DEFAULT/GENERAL/LOCKED`만 사용하고 `SERIES` 또는 category slug 필드를 만들지 않음.

**RefreshToken/JWT 도메인**
- Access Token 만료 1h, Refresh Token 만료 2w claim 검증.
- Refresh Token DB에는 raw token 미저장, `token_id` unique와 `token_hash` 저장.
- refresh 성공 시 기존 token `revokedAt` 세팅, 새 RefreshToken row 생성, 새 cookie 반환.
- 폐기/만료/해시 불일치/타입 불일치 token은 refresh 실패.
- 같은 refresh token 재사용 시 새 token 발급 없이 401 공통 오류.

**Controller/Security**
- 모든 auth endpoint는 `ApiResponse` success/data/error/timestamp shape 유지.
- Bean Validation 실패는 400 details 포함.
- `/auth/me`는 Access Token 없으면 401, 유효 Access Token이면 user+defaultBlog 반환.
- `/api/v1/auth/register|signin|refresh|signout`는 Access Token 없이 접근 가능하되 refresh/signout은 cookie 정책으로 처리.
- 인증은 되었지만 권한 부족인 API는 기존 403 handler shape 유지.
- Swagger/OpenAPI에 Bearer와 refresh cookie 설명 노출.

**Frontend AuthContext**
- 앱 mount 시 refresh 성공 → token store 세팅 → `/auth/me` 호출 → user/defaultBlog 저장.
- refresh 실패 → user/accessToken/token store null, 로그인 필요 상태.
- signin 성공 → Access 메모리 저장, `/auth/me` 후 setup 여부에 따른 redirect.
- signout → 서버 signout 호출, 실패해도 로컬 상태 clear.

**Frontend apiClient**
- Access Token 존재 시 Bearer 부착, 모든 요청 `credentials:'include'`.
- 401 다발 시 refresh 요청은 1회(single-flight), 대기 요청은 같은 결과 사용.
- refresh 성공 후 원 요청 1회 재시도하고 새 Access Token을 사용.
- refresh endpoint 자체에서 401이 나도 재귀적으로 refresh를 다시 호출하지 않음.
- refresh 실패 시 `onUnauthorized` 실행 및 `/signin` 이동.

**Frontend Routes/Pages**
- `ProtectedRoute`: loading, unauthenticated redirect, authenticated pass.
- `GuestOnlyRoute`: authenticated user는 signin/signup 접근 불가.
- `SetupGuard`: `isSetupCompleted=false`인 authenticated user는 `/blog/setup`, true인 user는 setup 페이지 접근 시 `/`.
- Signup form은 nickname 2~20과 birth_date 필수, payload key는 `birth_date`.
- Signin form은 generic auth failure message를 표시한다.

### F. API 계약 초안

**Register request**
```json
{
  "email": "user@example.com",
  "password": "Password!1",
  "nickname": "zero",
  "name": "홍길동",
  "birth_date": "1995-01-01"
}
```

**Signin/refresh response data**
```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**GET /auth/me response data**
```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "zero",
  "name": "홍길동",
  "birthDate": "1995-01-01",
  "role": "USER",
  "status": "ACTIVE",
  "profileImageUrl": null,
  "defaultBlog": {
    "id": 1,
    "title": "zero의 블로그",
    "urlSlug": "zero",
    "isSetupCompleted": false
  }
}
```

**Refresh cookie**
- 이름 후보: `refresh_token`
- 속성: `HttpOnly`, `Secure`(운영 true, local/test configurable), `SameSite=Strict` 또는 `Lax`, `Path=/api/v1/auth`, `Max-Age=1209600`.
- clear 시 같은 name/path/sameSite/secure 조합으로 `Max-Age=0`.

### G. 완료 기준(DoD)

- FR-AUTH-01~05가 모두 공통 응답 래퍼와 NFR-01 인증 보안 정책을 따른다.
- Register는 BCrypt(12), User/Blog/DEFAULT Category 자동 생성, slug 중복 suffix, `is_setup_completed=false`를 테스트로 증명한다.
- Signin은 SUSPENDED 거부, generic failure message, Access body/Refresh HttpOnly cookie를 테스트로 증명한다.
- Refresh rotation은 revoke/hash/no-reuse/expiry 실패 케이스까지 테스트한다.
- Signout은 Refresh revoke와 cookie clear를 테스트한다.
- `/auth/me`는 Access 인증 필수이며 user+defaultBlog를 반환한다.
- JWT secret은 env/test 설정으로만 제공되고 커밋된 운영 비밀이 없다.
- Swagger UI(`/swagger-ui.html`)에서 auth API와 Bearer/cookie 흐름이 확인된다.
- BE `./gradlew test` 통과(Testcontainers MySQL 8.4 포함), 가능하면 `./gradlew build` 통과.
- FE `npm run test`와 `npm run build` 통과.
- FE Access Token은 localStorage/sessionStorage/cookie에 직접 저장하지 않고 메모리만 사용한다.
- `apiClient` 401 자동 refresh single-flight와 실패 cleanup이 테스트로 보장된다.
- Signin/Signup 페이지는 PRD §6 디자인 토큰과 데스크톱 온보딩 레이아웃을 유지하고, 가짜 성공 응답이나 하드코딩 샘플 데이터를 추가하지 않는다.
- 워크로그 `[개발 기록]`, `[이슈·결정]`, `[리뷰]`, `[머지]`는 해당 단계가 실제 발생할 때 timestamp와 함께 append한다.

### H. 결정 필요

1. **회원가입 직후 인증 상태**: FR-AUTH-01은 token 반환을 요구하지 않는다. 권장안은 register 성공 후 FE가 같은 email/password로 signin을 호출해 Access/Refresh를 확보하고 `/blog/setup`으로 이동하는 방식이다. 대안은 register가 signin과 동일한 token response를 반환하는 방식이며 API 계약이 넓어진다.
2. **Refresh cookie SameSite 값**: 요구사항은 HttpOnly Secure SameSite cookie만 명시한다. 권장안은 동일 사이트 SPA 기준 `SameSite=Strict`, 로컬 개발 이슈가 있으면 `Lax`로 조정 가능한 property를 둔다.
3. **로컬 개발 Secure cookie**: NFR은 개발 환경에서 Secure 완화를 허용한다. 권장안은 `auth.cookie.secure=true`를 기본으로 두고 `application-test.yml`/local example에서 false를 명시한다.
4. **`birth_date` DB NOT NULL 여부**: FR-AUTH와 PRD §9-A는 회원가입 입력 필수이나 V1 `users.birth_date`는 nullable이다. 권장안은 M1에서 DTO validation으로 필수를 보장하고, DB NOT NULL은 별도 V2 마이그레이션 여부를 사용자 확인 후 결정한다.
5. **Refresh Token reuse 대응 강도**: 스펙은 재사용 실패를 요구한다. 권장안은 재사용 요청을 401로 거부하고 새 token을 발급하지 않는다. 탈취 감지 시 해당 사용자의 모든 active refresh token revoke까지 할지는 MVP 범위 결정이 필요하다.
6. **`/auth/me` defaultBlog shape**: FE `SetupGuard`가 필요로 하는 최소 필드는 `id/title/urlSlug/isSetupCompleted`이다. M2 `/blogs/me`와 중복되지 않도록 M1은 이 최소 shape만 반환하는 것을 권장한다.

## [이슈·결정] (사용자 확정 · 2026-07-01)

Codex 계획 §H 결정 필요 6건에 대한 사용자 확정:

1. **회원가입 직후 인증(§H-1)**: **register 후 FE가 별도 signin 호출**로 확정. register는 User/Blog/DEFAULT Category 생성만 담당하고 토큰을 반환하지 않는다(FR-AUTH-01 준수, API 계약 최소화). FE는 register 성공 → 동일 email/password로 signin → `/blog/setup` 이동.
2. **SameSite(§H-2)**: 권장안 채택 — `SameSite=Strict` 기본 + property로 조정 가능.
3. **로컬 Secure(§H-3)**: 권장안 채택 — `auth.cookie.secure=true` 기본, `application-test.yml`/local example에서 false.
4. **birth_date DB 제약(§H-4)**: **DTO validation만**으로 확정. V1 `users.birth_date` nullable 유지, M1은 `@NotNull` DTO 검증으로 가입 필수 보장. DB NOT NULL 마이그레이션(V2)은 추가하지 않는다.
5. **Refresh reuse 대응(§H-5)**: **단순 401 거부**로 확정 — 폐기/만료/해시불일치/재사용 token은 401, 새 token 미발급. ⚠️ **추후 확장 필요(명시 요청)**: 재사용(탈취) 감지 시 해당 사용자의 **모든 active refresh token을 revoke**하는 강화 대응은 MVP 이후 별도 작업으로 도입한다. 이번 구현에서 reuse 감지 지점에 확장 포인트(주석/구조)를 남긴다.
6. **`/auth/me` defaultBlog(§H-6)**: 권장안 채택 — 최소 shape(`id/title/urlSlug/isSetupCompleted`)만 반환.

## [개발 기록] (Claude)

### 2026-07-01 · 백엔드 인증 구현 (executor#BE, Sonnet)
- 도메인 엔티티/repo(User/Blog/Category/RefreshToken + enum), JWT 인프라(JwtProvider/JwtAuthenticationFilter/RefreshTokenHasher/쿠키), AuthService/RefreshTokenService, DTO 5종, AuthController 5 엔드포인트, SecurityConfig 갱신, ErrorCode 보강. jjwt 0.12.1 추가. 총 56개 파일 + 테스트 10개 클래스(90 케이스).
- executor 자체 보고: 컴파일·assemble ✓, 그러나 **테스트 미실행**("infrastructure-related")로 마감 → 오케스트레이터가 Docker 기동 후 실제 `./gradlew test` 검증 착수.

### 2026-07-01~02 · 오케스트레이터 검증 및 수정 (직접, TDD green까지)
executor 코드를 Testcontainers MySQL 8.4로 실제 실행 → **초기 72/90 실패**. 원인을 순차 진단·수정:
1. **테스트 datasource 부재(72 실패)**: 신규 테스트 8개가 `@DataJpaTest`/`@SpringBootTest`인데 Testcontainers 연결 정의 없이 `application-test.yml`의 `localhost:3306`을 참조(M0는 클래스별 `@Container`로 우회). → `support/IntegrationTestSupport` **싱글톤 컨테이너 base class** 신설 + 8개 테스트 상속(`@DynamicPropertySource`로 datasource 주입). 72→37.
2. **WeakKeyException**: `application-test.yml`의 `jwt.secret`이 15바이트로 JJWT HS256 최소 256비트 미만 → `@SpringBootTest` 2개 컨텍스트 로드 실패. → test secret을 32바이트+로 확장.
3. **테스트 격리 오염(unique 충돌)**: `@SpringBootTest` 2개가 커밋한 `test@example.com` 등이 공유 DB를 오염시켜 후속 `@DataJpaTest` 전멸. → 두 `@SpringBootTest`에 `@Transactional` 추가(메서드 롤백). 46→12.
4. **RefreshTokenHasher BCrypt 72바이트 한계(설계 결함)**: refresh token(JWT 원문 200자+)을 BCrypt로 해싱 → `password cannot be more than 72 bytes`. → **SHA-256 결정적 해시 + constant-time 비교**로 전환(고엔트로피 토큰엔 BCrypt salt 불필요). token_hash 컬럼 VARCHAR(500)로 64자 수용 OK. 12→2.
5. **JwtProvider.validateToken 예외 누락(보안 결함)**: catch의 `SecurityException`이 import 미비로 `java.lang.SecurityException`으로 해석 → 실제 서명 오류 `io.jsonwebtoken.security.SignatureException` 미포착·전파(위조 토큰 시 500 위험). → 모든 JJWT 예외 부모 `JwtException`으로 catch 통일.
6. **findActiveByUserId 쿼리 결함**: `Optional` 반환인데 limit 없어 다중 active 시 `NonUniqueResultException`; `CURRENT_TIMESTAMP`(DB=UTC) vs 저장값(앱=KST) timezone 불일치. 서비스 미사용 메서드였음. → `List` + `Pageable` limit + `:now` 파라미터화 + `createdAt DESC, id DESC` tie-break. 테스트 호출부 조정.
7. **swagger 테스트 기대 오류**: `/swagger-ui.html`은 springdoc이 302 리다이렉트(익명 허용의 증거) → 기대를 `is3xxRedirection()`으로 정정.
- **최종: `./gradlew test` BUILD SUCCESSFUL, 91/91 통과**(RefreshTokenHasherTest에 장문 토큰 케이스 1건 추가). Testcontainers MySQL 8.4 실검증.

### 2026-07-02 · 프론트엔드 인증 구현 (executor#FE, Sonnet)
- M0 골격을 실동작으로 전환: `types/auth.ts` 신설, `lib/apiClient.ts`(Bearer, credentials:'include', 401 single-flight refresh + 원요청 1회 재시도, refresh 재귀 방지, onUnauthorized 콜백), `lib/authContext.tsx`(accessToken 메모리, mount 시 refresh→me 복구, defaultBlog 포함), `SigninPage`(generic 에러 "이메일 또는 비밀번호가 올바르지 않습니다.", setup 여부 redirect), `SignupPage`(nickname 2~20·birth_date 필수·password 정책 클라 검증, payload `birth_date`, 성공→자동 signin→/blog/setup), `SetupGuard`/`GuestOnlyRoute`(isSetupCompleted 기준).
- 확정 결정 준수: register는 토큰 미반환 → FE 자동 signin, Access Token 메모리 전용(스토리지 미저장).
- 테스트: authContext(6)/apiClient(10)/SigninPage(5)/SignupPage(7)/router(6).
- **오케스트레이터 직접 검증: `npm run test` 34/34 통과, `npm run build` 성공**(gzip 96.47kB). BE 전례(테스트 미실행) 감안해 재실행 확인.

### 검증 종합
- BE `./gradlew test` 91/91 ✓ (Testcontainers MySQL 8.4) · FE `npm run test` 34/34 ✓ · FE `npm run build` ✓ · BE 컴파일/assemble ✓.
- 저자(executor)와 검증(오케스트레이터 직접 실행) 분리. 최종 리뷰는 Codex(3b) 예정.

## [리뷰] (Codex · 2026-07-02 09:32 KST)

Verdict: **blocking**

### Blocking issues

1. **High · spec violation/security · AUTH error-code contract regressed and unauthenticated 401 body is wrong**
   - Location: `src/main/java/com/zeroverse/common/exception/ErrorCode.java:7-11`, `src/main/java/com/zeroverse/config/SecurityAuthenticationEntryPoint.java:26-29`; spec: `docs/REQUIREMENTS.md:883-888`, `docs/PRD.md:204-206`.
   - What code does: `AUTH_001` is now password-policy `400`, `AUTH_002` is login failure, and `AUTH_003` is suspended-account `403`, while NFR-04 defines `AUTH_001`=login failure, `AUTH_002`=token expired, `AUTH_003`=Refresh Token invalid. The Spring Security entry point still emits `AUTH_001`, so unauthenticated `/api/v1/auth/me` returns the password-policy error body.
   - Fix requirement: restore the NFR-04 meanings or add non-conflicting new codes without reusing them; update all auth call sites. The security entry point must emit the authentication-required/failure code and message, and controller/security tests must assert the error code/message body, not only HTTP status.

2. **High · security/spec violation · suspended signin leaks account existence/status**
   - Location: `src/main/java/com/zeroverse/auth/service/AuthService.java:92-101`, `src/main/java/com/zeroverse/common/exception/ErrorCode.java:8-10`; spec: `docs/REQUIREMENTS.md:399-406`, `docs/PRD.md:199-201`, `docs/PRD.md:230-231`.
   - What code does: `findByEmail()` succeeds, then `user.isSuspended()` is checked before password verification. Any password for a suspended email returns `403 정지된 계정입니다.`, exposing that the account exists and is suspended. FR-AUTH-02 requires login failure responses to avoid exposing account existence.
   - Fix requirement: make nonexistent email, wrong password, and suspended login follow the approved non-enumerating response policy unless a new explicit decision overrides it. Add service/controller tests for suspended users with wrong and correct passwords.

3. **Medium · spec violation · default blog `urlSlug` is raw nickname, not a valid slug**
   - Location: `src/main/java/com/zeroverse/auth/service/AuthService.java:65-82`, `src/main/java/com/zeroverse/auth/dto/RegisterRequest.java:16-18`; spec: `docs/PRD.md:208-210`.
   - What code does: nickname only has length validation, then `urlSlug = request.nickname()` is stored directly. Nicknames can produce invalid/reserved slugs, including Korean text, uppercase, spaces/special characters, `admin`, or 2-character values that violate the 3-character slug minimum. The duplicate suffix loop also works on the raw nickname rather than normalized slug candidates.
   - Fix requirement: generate the default blog slug through the PRD §4.5 slug rules: lowercase alphanumeric/hyphen, invalid chars normalized, reserved words avoided, 3-30 chars enforced, and `-2`, `-3` suffixes applied after normalization. Add tests for non-ASCII/special/reserved/short/collision inputs.

### Non-blocking issues

1. **Medium · security/test gap · public auth endpoints trigger refresh-on-401**
   - Location: `frontend/src/lib/apiClient.ts:68-75`, `frontend/src/lib/authContext.tsx:73-85`.
   - What code does: every `401` except `/auth/refresh` starts refresh single-flight. Because `signin()` uses `apiClient('/auth/signin')`, a failed public signin can call `/auth/refresh`, potentially rehydrate an old cookie-backed session, then retry signin.
   - Fix requirement: restrict auto-refresh to authenticated requests, or add a `skipAuthRefresh` option/exclusion list for `/auth/signin`, `/auth/register`, and other public endpoints. Add a test that `/auth/signin` 401 does not call refresh.

2. **Medium · test gap · M1 tests miss required auth/security assertions**
   - Location: `src/test/java/com/zeroverse/config/SecurityConfigAuthTest.java:111-154`, `src/test/java/com/zeroverse/auth/security/JwtProviderTest.java:36-148`, `src/test/java/com/zeroverse/auth/service/RefreshTokenServiceTest.java:73-99`, `frontend/src/routes/router.test.tsx:79-91`, `frontend/src/pages/SigninPage.test.tsx:84-117`.
   - Gaps: 401 tests assert status only, not NFR-04 error code/body; no backend 403/access-denied handler coverage was found; JWT expiry is not asserted in `JwtProviderTest`; refresh service tests cover revoked/hash mismatch but not expired-token validation through the service/controller path; router protection test does not `await` `waitFor`; the signin redirect test does not submit or assert navigation.
   - Fix requirement: add focused assertions for 401/403 body, expired access/refresh tokens, refresh reuse rejection through `/auth/refresh`, suspended signin, and the FE route/signin flows.

3. **Low · spec/docs gap · refresh cookie auth is absent from OpenAPI config**
   - Location: `src/main/java/com/zeroverse/config/OpenApiConfig.java:20-25`.
   - What code does: OpenAPI registers only `bearerAuth`; the PRD/worklog require documenting Bearer access token and refresh cookie flow.
   - Fix requirement: add an API key cookie security scheme for `refresh_token` and describe the refresh/signout cookie flow.

4. **Low · config consistency · refresh cookie name is configurable on write but hard-coded on read**
   - Location: `src/main/java/com/zeroverse/auth/controller/AuthController.java:79-99`, `src/main/java/com/zeroverse/auth/support/RefreshTokenCookieFactory.java:15-34`.
   - What code does: cookies are written with `AuthCookieProperties.name`, but `@CookieValue(name = "refresh_token")` hard-codes the read side. Changing `auth.cookie.name` silently breaks refresh/signout.
   - Fix requirement: use the same property for reading and writing cookies, or remove the configurability.

### Implementation notes

- Worklog `[계획]`/`[이슈·결정]`/`[개발 기록]` generally matches the auth implementation. Extra non-auth metadata/config files in the PR diff (`.claude/**`, root `CLAUDE.md`, `AGENTS.md`) are not described in the M1 worklog; confirm they are intentional PR contents or split/document them.
- Decision overrides checked: register returns no token (`AuthController.java:51-55`); FE does register then signin (`SignupPage.tsx:99-103`); `birth_date` is DTO-required while entity remains nullable (`RegisterRequest.java:23-24`, `User.java:44-45`); refresh reuse is simple 401 with the requested future revoke-all TODO (`RefreshTokenService.java:56-60`); `defaultBlog` response is minimal (`DefaultBlogResponse.java:6-20`).
- Refresh cookie attributes are set through `ResponseCookie` with `HttpOnly`, `Secure`, `SameSite`, path, and max-age (`RefreshTokenCookieFactory.java:15-34`). FE access token storage is module/React memory only; no `localStorage`, `sessionStorage`, or `document.cookie` usage was found for auth tokens.
- `RefreshTokenHasher` uses SHA-256 and constant-time comparison via `MessageDigest.isEqual` (`RefreshTokenHasher.java:18-37`). `JwtProvider.validateToken()` catches `JwtException | IllegalArgumentException` (`JwtProvider.java:56-68`); no JwtProvider exception-handling TODO/stub remains.
- TODO/stub/skip scan: no `@Disabled`/`test.skip`/`it.skip` found. The only M1 auth TODO is the required refresh-reuse expansion point in `RefreshTokenService.java:59`; an existing `WritePage.tsx` TODO is outside M1 auth scope.
- Secret scan: no committed runtime JWT/AWS secrets found. Runtime config uses env vars in `application.yml`; concrete values are limited to `application-test.yml` and `application-local.example.yml`.
- Verification attempted during review: `git diff --check dev...feature/M1-auth` passed; `npm.cmd run test -- --run` passed 34/34; `npm.cmd run build` passed with existing CSS `@import` ordering warnings. Backend tests were not reproducible in this sandbox: `./gradlew test` attempted to download Gradle and network was denied; direct cached Gradle 8.10 started but could not resolve uncached Spring Boot Gradle plugin `3.5.15` under network restriction.

### 수정 (fix pass · Claude executor+오케스트레이터 검증 · 2026-07-02)

Codex [리뷰] blocking 3건 + 비블로킹 4건 반영:

**블로킹**
- **B1 ErrorCode NFR-04 복원**: `AUTH_001`=로그인 실패(401)/`AUTH_002`=토큰 만료/`AUTH_003`=Refresh 무효, 미인증 진입점은 신규 비충돌 `AUTH_004`=인증 필요(401) emit. 비밀번호 정책은 `RegisterRequest` @Pattern → Bean Validation 400. `SecurityConfigAuthTest`가 401 body의 error.code/message까지 assert.
- **B2 정지계정 열거 누출 제거**: `AuthService.signin`을 비밀번호 검증 우선으로 변경 — 없는 email/틀린 비번/정지+틀린비번은 모두 generic `AUTH_001`, 비밀번호 일치 후에만 정지 확인 → `USER_003`(403). 서비스+컨트롤러 테스트 추가.
- **B3 slug §4.5 규칙**: `common/util/SlugGenerator` 신설(영소문자/비허용문자→하이픈/앞뒤·연속 하이픈 제거/3~30자/예약어 회피/중복 -2,-3). `AuthService.register`가 사용. `SlugGeneratorTest`(한글/예약어/특수문자/짧음/충돌).

**비블로킹**
- N1: `apiClient` PUBLIC_AUTH_ENDPOINTS(`/auth/signin`,`/auth/register`) 401은 refresh 미트리거 + 테스트.
- N3: `OpenApiConfig` 인증 흐름/토큰 라이프사이클 문서 보강.
- N4: `AuthController` cookie 읽기를 `getRefreshTokenFromCookie()`로 일원화(cookieProperties.name 사용).
- N2: BE 401/403 error.code·정지 signin 케이스 보강. **FE router await/signin submit 보강은 이번 패스 미반영(잔여, 재리뷰 확인 대상).**

**재검증(오케스트레이터 직접 실행)**: BE `./gradlew test` **116/116 통과**(failures=0/errors=0, Testcontainers MySQL 8.4) · FE `npm run test` **36/36** · `npm run build` ✓.

**노트**: 리뷰 지적된 `.claude/**`·`CLAUDE.md`·`AGENTS.md`는 M1 인증과 무관한 별도 커밋(chore/docs)으로 이미 분리됨 — 의도된 PR 내용.
