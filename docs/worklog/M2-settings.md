# M2 — 사용자/블로그 설정 + 초기 설정

- **작성 시각**: 2026-07-26 KST
- **브랜치**: `feature/M2-settings` (base: `dev`)
- **범위**: FR-SETTINGS-01~04, FR-BLOG-01 — `/users/me`(GET/PUT), 비밀번호 변경, `/blogs/me`(GET/PUT), 블로그 초기설정, slug 검증, 공개 블로그 조회. FE: BlogInitialSetupPage, SettingsProfilePage, BlogPage(히어로 실연동).
- **제외**: FR-BLOG-02(게시글 목록·필터·페이징 → M4/M7), 카테고리 CRUD(M3), 이미지 업로드(M4), 회원 탈퇴(대응 FR/API 없음), 유니버스·댓글·피드·알림·관리자.
- **기준 문서**: `AGENTS.md`, `docs/PRD.md` §2.2·§3·§4·§5·§7·§9·§10 M2·§11·§12, `docs/REQUIREMENTS.md` §4·§5·§6.2·§6.3·FR-SETTINGS-01~04·FR-BLOG-01·NFR-04, `docs/design/DESIGN-SYSTEM.md` §2~§4·§6·§8.2·§8.6·§8.7 + 해당 `.dc.html`, `docs/governance/README.md` §2.
- **선행 상태**: M1 완료·머지(PR #7, 머지 커밋 `094fa37`). `dev` 최신은 `f9fdc5b`. RISK-0002 CLOSED. 기준선 BE **138 tests** / FE **186 tests**, lint·build green — M2 완료 시 전부 회귀 통과해야 한다.
- **참고 자산**: 초기화 이전 M2 구현이 `origin/feature/M2-settings`(과거 PR #3 머지분)에 읽기 전용으로 존재. 재사용 판정은 §4 참조 — **체리픽 금지**.

## [계획] (Codex · 2026-07-26)

### 0. 기준 상태와 선행 판정

- 현재 `feature/M2-settings`는 최신 `dev`의 `f9fdc5b`에서 분기된 clean tree이며, M1 squash merge `094fa37`과 M1 머지 기록 커밋을 포함한다.
- M1 기준선은 BE 138 tests, FE 186 tests, lint/build green이며 M2 완료 시 전부 회귀 통과해야 한다.
- 구현 우선순위는 사용자 결정·PRD §9 → `docs/design/` → REQUIREMENTS → PRD 본문 → worklog/ADR 순이다. (PRD §9, governance/README §7)
- M2 명시 범위는 `FR-SETTINGS-01~04`, `FR-BLOG-01`이다. `FR-BLOG-02`는 Post·Category·Universe에 의존하므로 M4/M7까지 이관한다. (REQUIREMENTS §6.2·§6.3, PRD §10)
- **현재 ErrorCode에는 잘못된 현재 비밀번호와 초기 설정 중복을 나타낼 코드가 없다.** `USER_005` 또는 별도 사용자 코드, `BLOG_004` 추가 여부는 공개 계약 변경으로 심의 후 확정한다. (REQUIREMENTS NFR-04, PRD §4.4)
- **디자인의 "slug는 나중에 변경할 수 없음" 문구는 `PUT /blogs/me`의 slug 변경 허용과 충돌한다.** 현 요구사항 기준으로 변경 가능하되, 구현 전 심의에서 카피 또는 정책을 확정한다. (REQUIREMENTS §5·§6.2, PRD §9.0)

### 1. M2 Scope Lock

#### IN SCOPE — Backend

- `FR-SETTINGS-01`: `GET/PUT /api/v1/users/me` — name, nickname, bio, birthDate, profileImageUrl 조회·수정, nickname unique 검증, **nickname 변경 시 slug 유지**. (REQUIREMENTS §6.2)
- `FR-SETTINGS-02`: `PUT /api/v1/users/me/password` — 현재 비밀번호 확인, 새 비밀번호 8~64자·영문·숫자·특수문자 정책, BCrypt strength 12 재해시. (REQUIREMENTS §6.2, PRD §3.2)
- `FR-SETTINGS-03`: `GET/PUT /api/v1/blogs/me` — title, urlSlug, description 조회·수정, slug 형식·예약어·unique 검증. (REQUIREMENTS §5·§6.2)
- `FR-SETTINGS-04`: `PUT /api/v1/blogs/me/initial-setup` — 기본 title/slug 생성, **1회성 상태 전이**, 성공 시 `isSetupCompleted=true`, 재호출 409. (REQUIREMENTS §4·§6.2)
- `FR-BLOG-01`: `GET /api/v1/blogs/slug/{urlSlug}` — 공개 블로그와 소유자 기본 정보, 삭제된 Blog/User 제외, **SUSPENDED 소유자는 공개 유지**. (REQUIREMENTS §6.3)
- `/me` 소유권은 요청의 userId를 받지 않고 **인증 principal에서만** 결정하며, 공개 API는 GET method만 permitAll로 유지한다. (REQUIREMENTS §5, PRD §4.3)
- User/Blog 도메인 변경 메서드, repository soft-delete 조회, DTO·service·controller·Swagger·보안 회귀 테스트를 포함한다.

#### IN SCOPE — Frontend

- `/blog/setup`: 실제 초기 설정 폼, API 검증 오류 표시, 완료 후 `/blog/{urlSlug}` 이동, AuthContext 사용자 재조회. (PRD §7)
- `/settings`: 좌측 SETTINGS 메뉴와 프로필·블로그·비밀번호 카드, email 비활성, birthDate/profileImageUrl 필드 포함. (PRD §7·§9-F)
- `/blog/:blogSlug`: 공개 API로 히어로의 title/description/slug/owner를 실연동하고 loading·404·오류 상태를 제공한다. (PRD §7)
- M1의 `SetupGuard`, `apiClient`, AuthContext single-flight refresh를 그대로 사용하며 **M2 전용 interceptor를 추가하지 않는다**. (PRD §8)
- `BlogSetupPage`는 PRD 명칭에 맞춰 `BlogInitialSetupPage`로 정리하되 기존 라우트·가드 동작은 보존한다.
- 프로필/블로그 저장과 비밀번호 변경은 **서로 독립된** 요청·성공·실패 상태를 갖는다.

#### OUT OF SCOPE

- `FR-BLOG-02` 게시글 목록·공개범위·카테고리/태그 필터·페이징 → Post/Category/Universe 이후. (REQUIREMENTS §6.3, PRD §10)
- 초기 설정 디자인의 `시작 카테고리` 편집은 `FR-CAT-*`·M3 범위다. **M2에서 가짜 칩이나 stub API를 만들지 않는다.**
- BlogPage의 카테고리 패널·글 수·통계·정렬·유니버스 신청·RSS 실제 동작은 M3/M4/M5 범위다.
- `/settings` 위험 구역의 회원 탈퇴는 대응 FR/API가 없으므로 **비활성 장식도 구현하지 않는다**.
- 이미지 파일 선택·업로드·presigned URL은 `FR-UPLOAD-*`·M4 범위다. M2는 `profileImageUrl` 문자열 저장만 담당한다.
- `OwnerProfileCard`는 PRD §9-O에서 폐기 — 이전 M2 구현을 복구하지 않는다. 소유자 정보는 히어로에 반영한다.
- 카테고리 CRUD, 게시글 CRUD, 유니버스, 댓글/좋아요, 피드/검색, 알림, 관리자 기능 제외.

### 2. 명세 요약

#### REQUIREMENTS §4/§5/§6

- User와 Blog는 `BaseSoftDeleteEntity` 기반이며, user email/nickname과 blog urlSlug의 unique 제약을 유지한다.
- Blog는 사용자당 기본 블로그 1개를 앱 레벨에서 보장하고 초기 설정 전후를 `is_setup_completed`로 구분한다.
- slug는 소문자·숫자·하이픈 3~30자, 앞뒤/연속 하이픈 금지, 예약어 금지, unique가 계약이다.
- **nickname 변경은 기존 slug를 자동 변경하지 않는다.** 자동 생성 충돌은 suffix `-2`, `-3`으로 해결한다.
- 성공·실패 모두 `success/data/error/timestamp` 래퍼를 사용하고 timestamp는 UTC ISO-8601이다.
- 보호 API는 401/403을 구분하고, `/users/me`와 `/blogs/me`는 principal 기반으로 **타 사용자 대상을 표현할 수 없게** 한다.
- 공개 블로그는 Blog와 소유자 User 모두 soft delete되지 않은 경우만 반환한다.

#### PRD §3/§4/§5/§7/§9/§10/§11/§12

- 패키지는 `domain/{user,blog}/{entity,repository,service,controller,dto}`를 따르고 Controller→Service→Repository 방향을 유지한다.
- ErrorCode와 응답 계약을 확장하되 기존 `AUTH_001~004`, `USER_004`, 공통 오류 **의미를 변경하지 않는다**. (PRD §4.4·§9.4-AB)
- `/settings`는 탭이 아니라 `240px 1fr` 좌측 메뉴 구조이며, 프로필·블로그·비밀번호를 실제 API에 연결한다. (PRD §7·§9-F)
- BlogPage의 우측 OwnerProfileCard는 폐기되고 블로그/소유자 정보는 히어로에 표현한다. (PRD §9-O)
- M2는 BE API 선완성 후 FE 연동 순서이며, M3 카테고리와 M4 게시글을 선행 구현하지 않는다.
- TDD로 domain/repository/service/controller/integration/FE 범위를 추적하고 placeholder·stub·skip을 금지한다. (PRD §11·§12)
- PRD §11에 수치형 line/branch coverage 기준은 없다. 완료 기준은 **M2 관련 테스트 항목·FR·분기의 요구사항 추적률 100%**로 해석한다.

#### design/DESIGN-SYSTEM.md 및 `.dc.html`

- 색상 paper `#f6ead8` / ink `#2b1b3d` / accent `#e85d75` / shadow `#d8c7b0`. 폐기된 네온 팔레트 사용 금지.
- radius 0, 주 보더 3px, 보조 2px, 그림자는 blur 0의 3~8px 하드 오프셋.
- 한글·본문은 IBM Plex Sans KR, Press Start 2P는 영문 로고·아이브로우 전용.
- `/blog/setup`은 다크 배경의 560px 카드, `/settings*`는 max-width 1240px의 `240px 1fr`, BlogPage는 `240px 1fr` + 190px 히어로.
- 초기 설정의 실시간 "사용 가능" 표시는 **별도 availability API가 없다**. submit 전에는 형식만 검증하고 unique 확정은 API 409로 표시한다.
- `시작 카테고리`는 M3, "나중에 변경할 수 없어요" 카피는 FR-SETTINGS-03과 충돌 → 심의 결과에 따라 제거·개정.
- Settings의 회원 탈퇴와 BlogPage의 카테고리·통계·글 목록은 **시각 슬롯만 존재** — M2에서 가짜 데이터로 채우지 않는다.

#### governance

- M2는 공개 API 계약, 비밀번호·개인정보, 인증된 자기 정보 접근제어를 변경하므로 **일반 소집 조건에 해당**한다.
- 디자인 slug 불변 카피와 요구사항의 slug 수정 API가 충돌하므로 **별도 소집 조건에도 해당**한다.
- BE·FE 동시 변경과 인증/접근제어 포함으로 **대형 마일스톤 기준 2개를 충족**한다.
- `RISK-0001`(JPA/V1 정합), `RISK-0004`(공용 UI 조기 고정), `RISK-0005`(실제 브라우저 쿠키 검증 이월)를 적용한다.
- 신규 에러코드와 slug 변경 정책은 승인 후 `ADR-0004` 후보로 기록하며, **승인 전 번호·의미를 구현에 고정하지 않는다**.

### 3. M1 재사용 자산

- `SlugGenerator.fromNickname/withSuffix/isValid`가 생성·검증·예약어 처리를 이미 제공 → **별도 `SlugValidator` 클래스를 만들지 않는다**. 필요 시 의미가 드러나는 service wrapper만.
- `User`, `Blog`, `Category`, `RefreshToken` 매핑과 `UserRegistrar`의 기본 Blog/Category 생성 트랜잭션을 보존한다.
- `SetupGuard`의 미완료 사용자 강제 이동과 완료 사용자의 `/blog/setup` 진입 차단을 그대로 사용한다.
- `ErrorCode`, `BusinessException`, `GlobalExceptionHandler` 재사용. M2 신규 코드 2건은 심의·명세 반영 후 추가.
- `ApiResponse`/`ErrorResponse`는 변경하지 않는다. M2 응답 DTO만 `data`에 넣으며 **password/hash를 포함하지 않는다**.
- `BaseEntity`/`BaseSoftDeleteEntity`와 `@EnableJpaAuditing` 그대로. **V1 스키마 변경 없이** 엔티티 메서드만 확장한다.
- `MySqlTestSupport`·`DatabaseCleaner`, 실제 MySQL 8.4 제약 테스트, MockMvc 보안 테스트 패턴을 재사용한다.
- `SetupGuard`·`ProtectedRoute`, `apiClient` Bearer 주입·credentials·single-flight·1회 재시도 재사용. **신규 interceptor 금지**.
- AuthContext에는 초기 설정/프로필 저장 후 `/auth/me`를 다시 읽는 공개 `refreshUser` 또는 동등한 **단일 갱신 경로만** 최소 확장한다.
- **M1 리뷰에서 도입한 뮤테이션 확인**을 unique 분기, soft-delete 조건, 현재 비밀번호 검증, setup 재호출 방지 테스트에 적용한다.

### 4. `origin/feature/M2-settings` 재사용 판정

- 원격 브랜치는 현재 `dev`와 merge-base `0dabaf4`에서 갈라진 별도 계보다. **커밋/cherry-pick 단위 재사용 금지.**
- **개념 재사용 가능**: User/Blog 변경 메서드 이름, `/users/me`·`/blogs/me`·공개 블로그 DTO 필드, service/controller 시나리오 목록.
- **fixture 재사용 가능**: 정상 User/Blog, 중복 nickname/slug, setup 완료/미완료, soft-deleted owner, SUSPENDED owner 입력값.
- **재작성 필요**: controller/DTO가 `com.zeroverse.controller`·`com.zeroverse.dto`에 있어 현재 도메인 패키지 구조와 불일치.
- **재작성 필요**: 과거 service는 soft-delete 필터 없는 `findById`, 구식 repository 메서드, 직접 `save`, 중복 경쟁 처리 부재를 사용한다.
- **재작성 필요**: `USER_005~007`, `BLOG_004`의 옛 의미는 현재 NFR-04/ADR-0003과 합의되지 않았으므로 복사할 수 없다.
- **재작성 필요**: FE의 탭형 Settings, 네온/dark 토큰, OwnerProfileCard, 별도 페이지 가드 파일은 PRD §9-F/O 및 현재 골격과 충돌.
- **재작성 필요**: 과거 hook 테스트는 hook을 렌더하지 않고 mocked `apiClient`를 직접 호출하는 **fake-pass 구조**다.
- **재사용 금지**: 과거 `IntegrationTestSupport` — 현재의 공유 MySQL 8.4 지원·DatabaseCleaner·M1 동시성/보안 패턴으로 다시 작성한다.

### 5. 게이트 계획 (원자적 커밋)

#### Gate 1: BE domain — User/Blog 상태 전이와 검증 + 단위 테스트

- 변경: `domain/user/entity/User.java`, `domain/blog/entity/Blog.java`. 검증 보강: `common/util/SlugGenerator.java`.
- 신규 테스트: `UserTest`, `BlogTest`.
- User `updateProfile`·`changePassword`, Blog `updateInfo`·`initialSetup`을 **엔티티 불변식 단위**로 구현한다.
- service/controller/repository/FE 파일 미포함.

#### Gate 2: BE repository — soft delete·unique 조회 계약 + JPA 테스트

- 변경: `UserRepository`, `BlogRepository`. 신규 테스트: `UserRepositoryTest`, `BlogRepositoryTest`.
- **자기 자신을 제외한** nickname/slug 중복 조회, 기본 블로그 조회, 공개 slug + Blog/User soft-delete 제외 쿼리를 고정한다.
- MySQL unique 제약과 auditing을 실제 컨테이너에서 검증한다.

#### Gate 3: BE service — 설정 유스케이스·경쟁 처리 + 테스트

- 신규: `UserSettingsService`, `BlogSettingsService`, `UserSettingsDtos`, `BlogSettingsDtos`.
- 변경 후보: `ErrorCode` — **심의에서 승인된 M2 코드만** 추가.
- 신규 테스트: 서비스 단위 + nickname/slug 동시 변경 경쟁 테스트.
- principal userId → soft-delete 안전 조회, current password 검증, BCrypt 재해시, setup 1회성, 공개 Blog 조회.

#### Gate 4: BE controller — HTTP 계약·Swagger·보안·통합 테스트

- 신규: `UserSettingsController`, `BlogSettingsController`, `BlogPublicController`.
- `SecurityConfig`는 공개 GET allowlist가 충분하면 **수정하지 않고 테스트만** 추가한다.
- 변경: `SecurityAccessControlTest` + 신규 controller tests + `SettingsFlowTest`.
- Validation 400, 무토큰 401/`AUTH_004`, 공개 GET 200, **공개 경로 쓰기 401**, 404/409, 래퍼·password 비노출 검증.

#### Gate 5: FE data — 타입·API 모듈·AuthContext 갱신 + 단위 테스트

- 신규: `features/settings/settingsApi.ts`·`types.ts`, `features/blog/blogApi.ts`. 변경: `lib/authContext.tsx`.
- `lib/apiClient.ts`는 **M2 결함이 발견되지 않는 한 수정하지 않는다**.
- profile/blog/password 상태·오류를 분리하고 **password를 상태·로그·응답 객체에 잔존시키지 않는다**.

#### Gate 6: FE screens — 초기설정·Settings·BlogPage 연동과 화면 테스트

- `BlogSetupPage` → `BlogInitialSetupPage`, `SettingsProfilePage`, `BlogPage`, `router.tsx`.
- 신규 테스트: 각 페이지, 가드 전환, loading/error/404, 독립 저장, 디자인 토큰·1440px 레이아웃·접근성.
- **1440px `.dc.html` 시각 대조를 PR 전에** 수행하고 기록한다(M0·M1에서 리뷰 지적으로 되돌아간 항목).

### 6. 테스트 계획 (PRD §11, TDD)

#### BE domain·service (예상 45~60)

- User profile: 전체/부분 변경, nickname 유지·변경, null/blank/길이 경계, **slug 불변**, soft-deleted user 거부.
- Password: 현재 비밀번호 성공/실패, 8/64자 경계, 영문·숫자·특수문자 각각 누락, BCrypt strength 12, 응답·로그 비노출.
- Blog: title/description 변경, slug 정상·예약어·대문자·앞뒤/연속 하이픈·2/3/30/31자, **자신의 기존 slug 허용**, 타 blog slug 충돌.
- Initial setup: 제공값/빈 title/빈 slug, nickname→fallback, suffix 충돌, 상태 전이, **두 번째 호출 409**, nickname 변경과 slug 비연동.
- Public lookup: 정상·없는 slug·deleted Blog·deleted User·**SUSPENDED owner 공개 유지**.
- 동시성: nickname/slug check-then-write 경쟁이 DB unique에서 `USER_002`/`BLOG_002`로 매핑되는지.

#### BE Repository/Controller (예상 35~50)

- Repository: unique, self-exclusion 쿼리, auditing, Blog/User soft-delete 제외, 기본 Blog 연관관계.
- Controller: validation details, 무토큰 401, malformed/expired token, 공통 래퍼, 404/409 매핑.
- 공개 경로: `GET /blogs/slug/**`만 무인증 허용하고 **같은 경로 POST/PUT/DELETE는 401**.
- DTO: User 응답에 password 없음, 공개 응답에 owner의 비공개 필드 없음.
- Swagger: 보호 endpoint Bearer 스키마, 공개 endpoint 표기를 OpenAPI JSON smoke로 확인.
- **총 예상: 138 + 80~110 = 약 218~248 tests, 0 skipped / 0 failures.**

#### BE 통합 시나리오

1. 가입 → signin → `/users/me` 조회 → 프로필 변경 → `/auth/me`에 반영.
2. 가입 → 초기 설정 → 완료된 slug 공개 조회 → 초기 설정 재호출 409.
3. 가입 A/B → A가 B의 nickname/slug로 변경 시 409 → 기존 데이터 보존.
4. 초기 설정 전 `/blogs/me` 조회 → setup → 일반 수정 → 공개 조회.
5. 비밀번호 변경 → 기존 비밀번호 signin 실패 → 새 비밀번호 signin 성공, **응답·로그에 두 비밀번호 미출현**.
6. User 또는 Blog soft delete → 공개 slug 조회 404. SUSPENDED만 적용하면 공개 유지.
7. 무토큰 보호 API 401, 공개 블로그 GET 200, 공개 경로 쓰기 401.

#### FE (예상 45~65, 총 231~251)

- settings/blog API의 method/path/body와 타입 안전한 반환값.
- AuthContext 갱신 후 nickname/title/urlSlug/isSetupCompleted가 가드·히어로에 즉시 반영.
- apiClient Bearer·credentials·401 single-flight·1회 재시도 **M1 회귀 유지**.
- 초기 설정: 필드 검증, 서버 409·slug 오류, 중복 제출 방지, 성공 후 이동.
- Settings: 초기 로드, **독립 저장**, validation/409 표시, email disabled, password 입력 초기화·DOM 잔존 방지.
- BlogPage: 무인증 조회, loading, 404, 일반 오류, 동적 히어로.
- Guard/router: 미인증→signin, setup 미완료→setup, 완료 사용자의 setup 접근 차단.

#### E2E·시각 검증

- 실제 E2E runner가 `package.json`에 없다. **Playwright/Cypress 추가는 외부 라이브러리 도입으로 심의 대상**이다.
- runner 승인 전에는 **skipped E2E 파일을 만들지 않고** MockMvc 전체 흐름 + Testing Library routed integration을 실행 가능한 게이트로 쓴다.
- Secure/Strict Refresh 쿠키의 실제 브라우저 검증은 M1 승인대로 최초 HTTPS 배포 전 `RISK-0005` 게이트에 유지한다.
- 1440×1200에서 `/blog/setup`, `/settings`, `/blog/{slug}`를 `.dc.html`과 대조하고 토큰·폭·보더·그림자·폰트·카피를 기록한다.
- `it.skip`/`describe.skip`/placeholder mock/fake-pass **0건**을 하드 게이트로 둔다.

### 7. 기획 심의 소집 판정

**판정: 소집 필요.** (governance/README §2)

- 일반 조건 ①: 신규 설정/공개 조회 API와 M2 에러코드는 **공개 API 계약 변경**이다.
- 일반 조건 ②: 현재 비밀번호 검증·프로필 개인정보·principal 기반 소유권은 **인증·권한·개인정보 정책**에 해당한다.
- 일반 조건 ③: 디자인의 slug 불변 카피와 `FR-SETTINGS-03`의 slug 수정 허용이 **충돌**한다.
- 대형 조건: BE+FE 동시 변경, 인증/접근제어 포함 **2개 충족**.

**심의 결정 항목**

1. 잘못된 현재 비밀번호와 setup 완료 중복의 코드/HTTP — `USER_*` 400과 `BLOG_*` 409 후보.
2. slug 변경 허용을 유지하고 디자인 카피를 고칠지, API를 불변으로 바꿀지. **현 정본 우선순위상 요구사항 유지·카피 개정 권고.**
3. E2E runner를 M2에 도입할지, 실행 가능한 integration + 최초 배포 전 `RISK-0005` 브라우저 게이트를 유지할지.

결정이 공개 계약·정책을 고정하면 회의록, REQUIREMENTS/PRD §9, Decision Register와 `ADR-0004` 후보에 반영한 뒤 Gate 1을 시작한다. **`LOW`가 아닌 결정은 사용자 승인 전 구현하지 않는다.**

### 8. DoD 체크리스트

- [ ] `FR-SETTINGS-01` 사용자 조회/수정과 nickname unique·slug 비연동 구현·테스트.
- [ ] `FR-SETTINGS-02` 현재 비밀번호 확인, 신규 비밀번호 정책, BCrypt 재해시 구현·테스트.
- [ ] `FR-SETTINGS-03` Blog 조회/수정과 slug 형식·예약어·unique 정책 구현·테스트.
- [ ] `FR-SETTINGS-04` 기본값 생성, `isSetupCompleted` 전이, 재호출 409 구현·테스트.
- [ ] `FR-BLOG-01` 공개 정보·소유자 기본 정보, soft-delete 제외, SUSPENDED 공개 유지 구현·테스트.
- [ ] `FR-BLOG-02`, 카테고리, 게시글, 업로드, 회원 탈퇴, 유니버스가 M2에 섞이지 않았다.
- [ ] 보호 API는 principal 기반 자기 정보만 다루고 무토큰 401/`AUTH_004`; 공개 GET만 permitAll.
- [ ] User 응답·오류·로그·FE 상태에 password 원문/해시가 echo되지 않는다.
- [ ] 공통 응답 래퍼·UTC timestamp·ErrorCode 계약 준수, 불필요한 래퍼 변경 없음.
- [ ] Swagger UI에 M2 endpoint, Bearer 인증, 응답·오류 계약 문서화.
- [ ] `/blog/setup`, `/settings`, `/blog/:slug`가 DESIGN-SYSTEM 색상·보더·그림자·폰트·1440px 레이아웃 적용.
- [ ] `.dc.html`의 M3+ 요소는 가짜 데이터·stub 없이 경계 처리됐고 **시각 대조 기록**이 남았다.
- [ ] BE 약 218~248, FE 약 231~251 테스트 통과, skip·placeholder mock·fake-pass 0건.
- [ ] M2 관련 PRD §11 테스트 항목과 FR 분기 추적률 100%.
- [ ] M1 BE 138·FE 186 회귀, BE/FE build, FE lint 통과.
- [ ] 신규 secret·평문 credential·password 미커밋.
- [ ] 신규 migration 불필요함을 확인했고 V1을 수정하지 않았다.
- [ ] 기획 심의 결정, ADR/위험, Gate별 커밋, 테스트 수치와 실제 산출물이 워크로그와 일치한다.

## [개발 기록]

### 2026-07-26 · Gate 1 — BE 도메인 (User/Blog 상태 전이)

**구현**

| 대상 | 불변식 |
|---|---|
| `ErrorCode` | 승인된 `USER_005`(400) · `BLOG_004`(409) **2개만** 추가 |
| `User.updateProfile` | name·nickname 필수, **nickname 2~20자**, bio·birthDate·profileImageUrl nullable. **nickname을 바꿔도 Blog slug는 건드리지 않는다** |
| `User.changePassword` | 이미 인코딩된 해시만 받는다 — 엔티티가 `PasswordEncoder`에 의존하지 않게 했다. 현재 비밀번호 대조는 Gate 3 서비스 책임 |
| `Blog.updateInfo` | title 필수·200자 이하, slug는 `SlugGenerator.isValid`로 형식·예약어 검증(위반 시 `BLOG_003`), description nullable |
| `Blog.initialSetup` | **1회성 전이**. 이미 완료면 `BLOG_004`(409), 성공 시 `isSetupCompleted=true` |

**1차 구현에서 잡은 결함 3건**(자체 검토)

executor의 1차 산출물에 승인 계약을 어긴 결함이 있어 되돌려 고쳤다. 셋 다 **잘못된 HTTP 응답**으로 이어지는 것이었다.

| # | 결함 | 왜 문제인가 | 수정 |
|---|---|---|---|
| 1 | `initialSetup` 재호출이 `IllegalStateException` | `GlobalExceptionHandler`에 해당 핸들러가 없어 `Exception` 폴백 → **500 `COMMON_500`**. ADR-0004가 승인한 409 `BLOG_004`는 정의만 되고 **아무도 던지지 않는 죽은 코드**였다 | `BusinessException(BLOG_004)`. 테스트도 status가 아니라 **ErrorCode까지** 단정하도록 교정 |
| 2 | nickname 상한 **100자** | 요구사항은 `2~20자`(REQUIREMENTS §4). DB `VARCHAR(100)`을 API 규칙으로 착각한 것 — **M1에서 같은 혼동으로 blocking 지적을 받았던 실수의 반복**(당시 `@Size(2,100)`→`@Size(2,20)`). 하한 검증도 없었다 | 2~20자로 교정, 1/2/20/21자 경계 전부 테스트. 가입(`AuthDtos`)과 규칙 일치 확인 |
| 3 | 모든 도메인 검증이 `IllegalArgumentException` | #1과 같은 이유로 **사용자 입력 오류가 전부 500**이 된다. 공통 응답 계약(NFR-03·04) 위반 | `BusinessException`으로 전환. 필드 검증 → `VALIDATION_001`(400), slug 형식·예약어 → `BLOG_003` |

**뮤테이션 확인**(M1에서 도입한 절차 — 테스트가 결함을 실제로 잡는지)

| 무력화한 분기 | 결과 |
|---|---|
| `isSetupCompleted` 상태 전이 제거 | FAILED — 재호출 거부가 뚫린다 |
| nickname 상한 `> 20` 검증 제거 | FAILED — 21자가 통과한다 |
| nickname 하한 `< 2` 검증 제거 | FAILED — 1자가 통과한다 |
| `SlugGenerator.isValid` 호출 제거 | FAILED — 예약어 `admin`이 통과한다 |

**판단 근거를 남기는 항목**

- `name` 100자 / `title` 200자 상한은 REQUIREMENTS에 명시 규칙이 없어 **V1 스키마의 컬럼 폭**(`VARCHAR(100)`/`VARCHAR(200)`)을 상한으로 삼았다. 초과 입력이 DB 오류(500)로 새는 것을 막기 위한 방어이며, nickname과 달리 **정본에 별도 규칙이 없어 컬럼 폭이 유일한 근거**다. 명시 규칙이 생기면 그쪽을 따른다.
- 엔티티 검증은 **최후 방어선**이다. 1차 방어선은 Gate 3~4의 DTO Bean Validation이며, 거기서 필드별 `details`를 제공한다.

**검증**: BE **200 tests** / 24 클래스 · 0 skipped · 0 failures · 0 errors (기준선 138 + 신규 62).

### 2026-07-26 · Gate 2 — BE repository (soft delete·unique 조회 계약)

**추가한 조회 메서드와 각각이 막는 실패**

| 메서드 | 없으면 무슨 일이 나는가 |
|---|---|
| `UserRepository.existsByNicknameAndIdNotAndDeletedAtIsNull` | 사용자가 **자기 닉네임을 그대로 둔 채** bio만 바꿔도 "이미 사용 중"으로 409가 난다. 자기 자신을 제외하지 않으면 모든 프로필 수정이 막힌다 |
| `BlogRepository.existsByUrlSlugAndIdNotAndDeletedAtIsNull` | 같은 이유로 slug를 유지한 채 title만 바꾸는 수정이 막힌다 |
| `BlogRepository.findByUrlSlugAndDeletedAtIsNullAndUserDeletedAtIsNull` | 소유자가 탈퇴(soft delete)한 블로그가 계속 공개된다 |

기존 메서드로 충분한 것(`findFirstByUserIdAndDeletedAtIsNullOrderByIdAsc` 등)은 새로 만들지 않았다.

**핵심 구분 — soft delete ≠ SUSPENDED**

공개 조회에서 세 상태를 분리해 검증한다. 정상 → 공개 / **SUSPENDED → 공개 유지** / soft deleted → 비공개.
정지는 계정 제재이지 삭제가 아니므로 이미 공개된 블로그를 내리지 않는다(REQUIREMENTS §6.3).

**범위 이탈 1건 되돌림**

1차 산출물이 SUSPENDED 상태를 만들려고 `User.suspend()`를 **프로덕션 엔티티에 추가**했다. 소비처는 테스트 2곳뿐이고 사용자 정지는 M9(FR-ADMIN) 범위다 — 이 저장소가 `RISK-0004`("소비처 없는 API 조기 고정")로 추적 중인 패턴 그대로다. M9가 실제 정지 정책(사유·시각·복구)을 구현할 때 시그니처가 먼저 굳어 있게 된다.

→ 메서드를 제거하고, 테스트에서 `EntityManager` native update로 실제 행 상태를 만든 뒤 `flush()`+`clear()`로 1차 캐시를 비우고 재조회하도록 바꿨다. 프로덕션 코드를 오염시키지 않고 같은 상황을 재현한다.

**테스트 클래스 정리**: `MutationTests`라는 nested 클래스가 있었다. 뮤테이션은 **검증 절차이지 영구 테스트가 아니고**, 내용도 기존 케이스와 겹쳤다. 중복을 지우고 `QueryConditionInteractionTests`·`PublicLookupOwnerStatusTests`처럼 무엇을 검증하는지 드러나는 이름으로 바꿨다.

**뮤테이션 확인**(직접 수행) — `findByUrlSlug...UserDeletedAtIsNull`에 `@Query`를 붙여 **소유자 soft delete 조건만 제거** → `BlogRepositoryTest` **4건 FAILED**(soft delete된 소유자의 블로그가 공개 조회됨). 확인 후 원복했다.

**검증**: BE **237 tests** / 37 클래스 · 0 skipped · 0 failures · 0 errors (Gate 1의 200 + 신규 37). 수치는 `build/test-results/test/*.xml` 합산값이다.

### 2026-07-26 · Gate 3 — BE 서비스 (설정 유스케이스·동시성)

`UserSettingsService`(프로필 조회·수정, 비밀번호 변경), `BlogSettingsService`(블로그 조회·수정,
초기 설정, 공개 조회)와 각 DTO. 소유권은 **principal userId로만** 결정하며, 서비스 시그니처에
다른 사용자를 지정할 표현 수단이 없다. 응답 DTO에 password 원문·해시가 없고 공개 블로그 응답에
email도 없다.

**동시성 — 심의 필수 조건 #5**

`initialSetup`은 `@Lock(PESSIMISTIC_WRITE)` 잠금 조회로 한 스레드만 상태를 확인·전이한다
(M1 `RefreshTokenService.rotate`와 같은 패턴). `DataIntegrityViolationException`은 M1의
`RegisterConstraintMapper`를 **재사용**해 제약별로 매핑한다.

**바로잡은 결함 4건**

| # | 결함 | 원인과 수정 |
|---|---|---|
| 1 | 동시 initial-setup이 **둘 다 성공** | 처음엔 "재조회"로 막으려 했다. 두 스레드가 거의 동시에 읽으면 둘 다 `isSetupCompleted=false`를 보고, 이후는 같은 row에 대한 단순 UPDATE라 충돌조차 없다. `saveAndFlush()`도 flush 시점만 앞당길 뿐 원자성을 주지 않는다 → 비관적 잠금으로 교체 |
| 2 | 잠금을 걸었는데도 **여전히 둘 다 성공** | 잠금 조회 **앞에 비잠금 조회**가 있었다. 먼저 읽은 인스턴스가 영속성 컨텍스트에 올라가면, 뒤이은 잠금 조회는 DB 락만 잡고 **1차 캐시의 오래된 엔티티**를 돌려준다. 두 번째 스레드는 자기 트랜잭션 초반의 `false`를 계속 본다 → 사전 조회 제거, 조회는 잠금 조회 한 번뿐 |
| 3 | `DataIntegrityViolationException`을 전부 `BLOG_002`로 뭉갬 | 코드 주석에 "구분할 수 있지만 하지 않는다"고 적혀 있었다. M1에서 blocking으로 고친 오분류와 같은 결함 → `RegisterConstraintMapper` 재사용 |
| 4 | **`initialSetup`이 description을 버림** | `Blog.initialSetup(title, urlSlug)` 시그니처 자체가 틀렸다. REQUIREMENTS FR-SETTINGS-04는 "필드: title, url_slug, **description**"이고 DESIGN-SYSTEM §8.6 초기 설정 화면에도 `한 줄 소개` 입력이 있다. **Gate 1 작업 지시에서 이 필드를 누락한 것이 원인**이다 → 3-arg로 정정하고 호출부 15곳 수정, "받은 소개를 저장한다" 테스트 추가 |

**중복 방어 제거 — 뮤테이션이 살아남아 발견**

self-exclusion 쿼리 앞에 `!기존값.equals(새값)` 비교가 있었다. 닉네임이 그대로면 **단락 평가로
쿼리 자체를 타지 않으므로**, self-exclusion을 일반 `exists`로 바꿔도 **어떤 테스트도 실패하지
않았다**(뮤테이션 생존). 방어가 두 겹이면 정작 쿼리가 자기 자신을 제외하는지는 아무도 검증하지
못한다.

→ 앞단 비교를 제거해 self-exclusion 쿼리를 **유일한 방어선**으로 만들고, 누락돼 있던
"닉네임을 그대로 두고 소개만 바꾼다" 테스트를 추가했다. 재뮤테이션 결과 **2건 FAILED**로 전환.

**뮤테이션 확인**(전부 실제 실행)

| 무력화한 것 | 결과 |
|---|---|
| 잠금 조회 → 비잠금 조회 | `ConcurrentSetupTest` **1건 FAILED**(expected 1, but was 2) |
| 현재 비밀번호 대조 제거 | `UserSettingsServiceTest` **1건 FAILED** |
| self-exclusion → 일반 exists (수정 전) | **0건 FAILED — 생존**. 위 중복 방어 제거의 근거 |
| self-exclusion → 일반 exists (수정 후) | **2건 FAILED** |

**테스트 단정 강화**: 동시 initial-setup 테스트가 "하나만 성공"만 보고 **실패 사유를 확인하지
않았다** — 실패가 `BLOG_002`나 `COMMON_500`이어도 통과했다. `BLOG_004`를 단정하도록 고쳤다.

**검증**: BE **281 tests** / 48 클래스 · 0 skipped · 0 failures · 0 errors (Gate 2의 237 + 신규 44).

### 2026-07-26 · Gate 4 — BE 컨트롤러·보안·통합 시나리오

`UserSettingsController`(`/users/me` GET·PUT, `/users/me/password` PUT), `BlogSettingsController`
(`/blogs/me` GET·PUT, `/blogs/me/initial-setup` PUT), `BlogPublicController`
(`/blogs/slug/{urlSlug}` GET, 공개). userId는 `@AuthenticationPrincipal`에서만 꺼내며 경로·쿼리·
본문 어디에도 대상 사용자를 지정할 수단이 없다.

**`SecurityConfig`는 수정하지 않았다.** M1의 공개 allowlist가 `GET /api/v1/blogs/slug/**`를 이미
method 제한과 함께 열어 두었다. 확인만 하고 테스트(`SecurityAccessControlTest`)에 M2 경로를 추가했다.

**테스트 픽스처 결함 2건**

| # | 증상 | 원인과 수정 |
|---|---|---|
| 1 | 블로그 API 테스트 **18건이 404** | `persistUser`가 **User만** 만들었다. 실제 가입(`UserRegistrar`)은 User·Blog·미분류 Category를 한 트랜잭션에서 만드는데, 사용자만 있으면 `/blogs/me`가 정당하게 `BLOG_001`(404)을 돌려준다 → 픽스처가 기본 블로그도 만들도록 수정 |
| 2 | soft delete·SUSPENDED 테스트 4건이 `TransactionRequiredException` | MockMvc 요청은 각자 트랜잭션을 열고 테스트 메서드에는 트랜잭션이 없다. native update를 그냥 실행하면 예외가 난다 → `TransactionTemplate`으로 감쌌다. 테이블명도 `zeroverse_user`/`zeroverse_blog`로 잘못 적혀 있어 `users`/`blogs`로 정정(트랜잭션 예외가 먼저 나서 가려져 있었다) |

**무력한 단정 제거**: 응답에 password가 없는지 확인하는 두 테스트가 JVM `assert` 문을 썼다.
`-ea` 없이는 **아무것도 검증하지 않는다**. AssertJ로 교체했다.

**접근제어 커버리지 구멍 — 뮤테이션이 살아남아 발견**

`principal.userId()`를 **상수 `1L`로 바꿔도 어떤 테스트도 실패하지 않았다.** 테스트가 사용자를
한 명만 만드는데, `DatabaseCleaner`가 TRUNCATE로 auto_increment를 리셋하므로 그 한 명이 항상
id 1이다. 즉 "소유권이 principal에서 나온다"를 아무도 검증하지 않고 있었다.

→ 두 컨트롤러에 **사용자 2명을 만들고 두 번째 사용자의 토큰으로 조회**하는 테스트를 추가했다.
재뮤테이션에서 **2건 FAILED**로 전환.

**뮤테이션 확인**(전부 실제 실행)

| 무력화한 것 | 결과 |
|---|---|
| 공개 allowlist의 `HttpMethod.GET` 제한 제거 | **5건 FAILED**(공개 경로 쓰기 요청이 401이 아니게 됨) |
| `changePassword`의 `@Valid` 제거 | **1건 FAILED**(비밀번호 정책 위반이 통과) |
| `principal.userId()` → 상수 `1L` (수정 전) | **0건 FAILED — 생존**. 위 커버리지 구멍의 근거 |
| `principal.userId()` → 상수 `1L` (수정 후) | **2건 FAILED** |

**검증**: BE **322 tests** / 52 클래스 · 0 skipped · 0 failures · 0 errors (Gate 3의 281 + 신규 41).

### 2026-07-26 · Gate 5 — FE 데이터 계층

`features/settings/types.ts`(백엔드 DTO와 필드명·nullable까지 1:1), `features/settings/settingsApi.ts`
(`getProfile`·`updateProfile`·`changePassword`), `features/blog/blogApi.ts`(`getBlog`·`updateBlog`·
`initialSetup`·`getPublicBlog`).

**`apiClient`·`authContext`는 수정하지 않았다.** `refreshUser`가 이미 `authContext`에 있어
(`authContext.tsx:43·162`) 저장 후 세션 갱신은 그것을 쓴다. 신규 interceptor도 만들지 않았다.

**되돌린 판단 1건 — 공개 조회의 apiClient 우회**

1차 구현이 `getPublicBlog`만 `apiClient`를 우회해 `fetch`를 직접 불렀다. "Authorization 헤더 없이
나가는지 검증하라"는 **내 지시 문구가 그 제약을 만든 원인**이다.

- 실제로는 우회할 이유가 없다. `apiClient`는 **토큰이 있을 때만** Authorization을 붙이므로
  (`apiClient.ts:107`) 비로그인 상태에서는 그대로 무인증 요청이 나가고, 로그인 상태에서 헤더가
  붙어도 서버가 이 경로를 permitAll GET으로 열어 두어 결과가 같다. **헤더 유무는 계약이 아니다.**
- 우회의 대가는 envelope 파싱·오류 변환·자격증명 처리를 한 벌 더 갖는 것이고, 그 사본이
  `apiClient`와 어긋나는 순간이 버그가 된다. 계획 §3의 "apiClient 재사용" 원칙과도 어긋난다.
- → `apiClient.get`으로 교체(slug는 `encodeURIComponent`). 테스트도 "토큰이 있어도 헤더를 안
  붙인다"에서 **"비로그인에서 무인증으로 호출된다" + "로그인 상태에서도 조회가 성립한다"**로
  의도에 맞게 고쳤다.

**뮤테이션 확인**(전부 실제 실행)

| 무력화한 것 | 결과 |
|---|---|
| 공개 조회에 Authorization 강제 부착 | **2건 FAILED**(1차 구현 기준) |
| `updateProfile`의 `put` → `get` | **1건 FAILED** |
| `initialSetup` URL을 `/blogs/me/setup`으로 변경 | **1건 FAILED** |
| `changePassword`의 오류를 일반 `Error`로 삼킴 | **2건 FAILED** — 호출자가 `USER_005`로 분기할 수 있어야 한다 |

**검증**: FE **209 tests** / 19 파일 · 0 failures (기준선 186 + 신규 23) · lint exit 0 · build exit 0.

### 2026-07-26 · Gate 6 — FE 화면 연동

`BlogSetupPage` → **`BlogInitialSetupPage`**(라우트·가드 동작 보존), `SettingsProfilePage`,
`BlogPage` 실연동. 히어로에 공개 블로그 데이터를 흘리기 위해 `lib/heroBlogContext.tsx`를 추가했다.

| 화면 | 구현 |
|---|---|
| `/blog/setup` | 블로그 이름·주소·한 줄 소개 → `initialSetup` → `refreshUser()` → `/blog/{urlSlug}` 이동. `BLOG_002`·`BLOG_003`·`BLOG_004` 안내 |
| `/settings` | 프로필·비밀번호 카드. email 비활성. **저장과 비밀번호 변경이 독립된 상태** |
| `/blog/:slug` | `getPublicBlog` → 히어로 title·description·owner 반영. loading·404·오류 |

**되돌린 범위 위반 1건 — 회원 탈퇴**

1차 산출물이 디자인 §8.7의 위험 구역과 `회원 탈퇴` 버튼을 비활성으로 구현했다. 계획 §1
OUT OF SCOPE는 "대응 FR/API가 없으므로 **비활성 장식도 구현하지 않는다**"이다. 비활성이어도
사용자에게는 곧 되는 기능으로 보이고, 이는 `RISK-0004`가 경고하는 조기 UI 고정이다.
→ 블록을 제거했다. 디자인에 그려져 있다는 것은 근거가 되지 않는다 — 디자인은 최종 화면이고
M2는 그 일부만 만든다. 제거 사유를 컴포넌트 주석에 남겨 다음 사람이 다시 넣지 않게 했다.

**행동 테스트 부재 — 뮤테이션 3건이 두 차례 살아남았다**

1차·2차 산출물 모두 `refreshUser()` 제거 / `setProfileSuccess(true)` 제거 / `setHeroBlog(data)`
제거를 **어떤 테스트도 잡지 못했다**. 신규 테스트가 "폼 필드가 있다 / 접두사가 보인다 / 버튼
글자가 맞다"는 **구조 확인**뿐이었기 때문이다. 2차 보고는 이를 "E2E가 필요한 갭, DoD상 허용"으로
정리했으나 그것은 판정이 아니다 — DoD는 테스트 통과를 요구하고, M1이 이미 jsdom만으로 세션
복구·401 경쟁을 검증한 선례가 있다.

→ `test/settingsBehavior.test.tsx`(9케이스)를 직접 작성해 **행동**을 검증한다.

| 검증 | 잡는 결함 |
|---|---|
| 설정 성공 → `/auth/me` 재호출 + 라우트 이동 | `refreshUser()`나 `navigate()` 누락. 세션을 갱신하지 않으면 `SetupGuard`가 낡은 상태를 보고 사용자를 setup으로 되돌린다 |
| 프로필 저장 성공 표시 | 성공 상태 누락 |
| **비밀번호 실패 후에도 프로필 성공 표시 유지** | 상태 결합. 계획이 명시한 요구인데 검증이 없었다 |
| 히어로에 조회 결과 반영 | `setHeroBlog` 누락 |
| `BLOG_002`·`BLOG_004`·`USER_002`·`USER_005` 안내, email 비활성, 404 | 오류 분기 |

**뮤테이션 재확인**: 세 지점을 동시에 무력화 → **4건 FAILED**. 확인 후 원복했다.

**시각 대조**(1440px 기준, 코드 ↔ 정본 값 대조)

| 항목 | 정본 | 구현 | 결과 |
|---|---|---|---|
| `/blog/setup` 카드 폭 | 560px | `layout.ts` `cardWidth: 560` | 일치 |
| `/settings` max-width·컬럼 | 1240px · `240px 1fr` | 동일 | 일치 |
| `/blog/:slug` 컬럼·히어로 | `240px 1fr` · 190px | 동일 | 일치 |
| slug 힌트 카피 | `영문 소문자·숫자·하이픈, 3~30자. 설정에서 나중에 변경할 수 있어요.` | 동일 | 일치(ADR-0004 개정 문구) |
| `border-radius` | 0 예외 없음 | 세 페이지에 `rounded-*` 없음 | 일치 |
| `Press Start 2P` | 영문 대문자 라벨 전용 | 세 페이지의 한글에 미사용 | 일치 |

**라이브 브라우저 렌더링은 확인하지 못했다** — 코드 값과 정본 값의 대조까지만 수행했다.
`RISK-0005`의 배포 전 게이트와는 별개 항목이므로 `[이슈·결정]`에 남긴다.

**검증**: FE **226 tests** / 21 파일 · 0 failures (Gate 5의 209 + 신규 17) · lint exit 0 · build exit 0.

## [이슈·결정]

- 2026-07-26 · Codex 계획 수립 완료. 기획 심의 **소집 필요** 판정(일반 조건 3 + 대형 조건 2).
- 2026-07-26 · 기획 심의 `M2-20260726-settings` 완료 — 회의록 [`docs/governance/meetings/M2-20260726-settings.md`](../governance/meetings/M2-20260726-settings.md). 독립 검토 3인 전원 `APPROVE_WITH_CHANGES`, 확신도 94, 제안 등급 `HIGH`, 공통 권고 **Q1=A(`USER_005`/400·`BLOG_004`/409), Q2=A(slug 변경 허용 유지·카피 개정), Q3=B(M2 runner 미도입·RISK-0005 배포 전 게이트 유지)**. 진행자 최종 등급 `HIGH`, 상태 `USER_DECISION_REQUIRED`. `ADR-0004`는 번호만 예약했으며 사용자 승인 전 문서는 작성하지 않는다. RISK-0007(slug 변경 후 기존 URL 단절) 등록.
- 2026-07-26 · **위임 승인** — Q1=`A`, Q2=`A`, Q3=`B`. **사용자의 개별 명시 승인이 아니라, "중대하지 않은 결정은 권장안대로 진행하라"는 사용자 포괄 위임에 따라 Claude가 채택했다**(회의록 [승인 절](../governance/meetings/M2-20260726-settings.md#승인) 참조). 세 검토자 권고가 일치했고 세 결정 모두 되돌림 비용이 낮다는 판단이다 — Q1은 외부 공개 전 철회 가능한 신규 코드, Q2는 요구사항 무변경·카피만 개정, Q3은 외부 의존성을 늘리지 않는 현상 유지. **사용자가 다른 판단을 내리면 되돌린다.** 회의록 상태를 `APPROVED`로 갱신하고 [승인 섹션](../governance/meetings/M2-20260726-settings.md#승인)을 append했다. `USER_005`(400, 현재 비밀번호 불일치)·`BLOG_004`(409, initial-setup 완료 후 재호출)를 신설하고 기존 `AUTH_001~004`·`USER_004`·`BLOG_002/003` 의미를 보존한다. slug 변경 허용과 nickname 변경 시 slug 자동 비연동을 유지하며 초기 설정 카피를 “설정에서 나중에 변경할 수 있어요.”로 개정한다. M2에는 E2E runner를 도입하지 않고 RISK-0005의 최초 배포 전 실제 HTTPS 브라우저 게이트를 유지한다. 결정 근거: [`ADR-0004`](../governance/decisions/ADR-0004-error-codes-and-slug-policy.md), RISK-0007.
- 2026-07-26 · 회의록의 **구현 선행 조건** 전수 목록:
  1. 승인 — Q1=A/Q2=A/Q3=B. **위임 승인으로 완료**(사용자 명시 승인 미수령).
  2. 오류 계약 동기화 — REQUIREMENTS NFR-04·PRD §4.4·§5.2·§9에 `USER_005`·`BLOG_004` 반영. **완료**.
  3. slug 정책·카피 동기화 — slug 변경 허용·nickname 비연동 유지, PRD §7·DESIGN-SYSTEM §8.6·`.dc.html`·화면 테스트 동기화. **문서 정본 완료, 화면 테스트는 Gate 6에서 수행**.
  4. 결정 추적성 확보 — ADR-0004와 회의록·worklog 연결. **완료**. `DECISION-REGISTER.md`도 갱신했다(M2 회의록 `APPROVED(위임)`, ADR-0004 `ACCEPTED(위임)`, 승인자 Claude).
  5. initial-setup 단일 성공 보장 — 잠금/조건부 update와 실제 MySQL 독립 트랜잭션 테스트. **Gate 3에서 수행**.
  6. availability 오인 방지 — 별도 availability API 없이 서버 확인 전 unique 확정 표현 금지, 형식 검증과 submit 409 분리. **Gate 6에서 수행**.
  7. M2 검증 증적 — 여섯 게이트의 자동 테스트·FR 추적·1440px 대조·회귀·build/lint·skip/stub/fake-pass 0건 기록. **각 Gate 및 M2 리뷰 전 수행**.
  8. 이월 게이트 유지 — RISK-0005 OPEN 유지, 실제 HTTPS browser smoke를 최초 배포 체크리스트 차단 조건에 연결. **최초 배포 전 수행**.
- 2026-07-26 · 새 문제 및 처리: 승인 요청의 `FR-BLOG-03`은 현 REQUIREMENTS에 존재하지 않으며 slug 수정 정책의 실제 정본 번호는 `FR-SETTINGS-03`이다. ADR-0004에 이 식별자 불일치와 실제 근거를 명시했다. 또한 PRD의 기존 표 항목 `J`(Base package)·`K`(관리자 시각 강조)가 신규 `§9-J`·`§9-K` 요청과 겹쳐, 기존 참조를 `§9.1-J`·`§9.2-K`로 한정하고 신규 결정 소제목을 `§9-J`·`§9-K`로 기록했다.

- 2026-07-26 · **공개 블로그 응답의 소유자 필드 확인 필요** — `PublicBlogResponse.OwnerInfo`에 현재 `name`(실명)이 포함돼 있다. REQUIREMENTS FR-BLOG-01은 "소유자 기본 정보"라고만 하고 필드를 특정하지 않으며, 디자인의 블로그 히어로가 닉네임만 노출한다면 실명은 불필요한 개인정보 노출이다. **Gate 6 시각 대조에서 확정**한다.
- 2026-07-26 · **라이브 브라우저 렌더링 미확인** — Gate 6 시각 대조는 구현 코드 값과 `docs/design/` 정본 값의 항목별 대조까지 수행했고, 실제 브라우저에서 띄워 본 확인은 하지 못했다. `RISK-0005`(HTTPS 쿠키)와는 **별개 항목**이므로 그쪽에 묻어 넘기지 않는다. PR 리뷰나 최초 배포 전에 `npm run dev`로 1440×1200에서 육안 확인이 필요하다.
- 2026-07-26 · **Gate 1 지시 오류 정정** — `Blog.initialSetup`을 `(title, urlSlug)` 2-arg로 지시했으나 FR-SETTINGS-04의 필드는 `title, url_slug, description`이고 디자인 §8.6에도 `한 줄 소개` 입력이 있다. Gate 3에서 3-arg로 정정했다. 엔티티 시그니처를 정할 때 **FR의 필드 목록과 디자인 화면의 입력 필드를 함께** 확인해야 한다.

## [리뷰]

### 2026-07-26 19:00 · Codex 1차 리뷰 (PR #8, `origin/dev...feature/M2-settings`)

**판정: `needs-attention` — blocking 3건 + non-blocking 1건.**

리뷰 실행 자체가 4번 중단됐다. 원인은 Codex도 `gradlew`도 아니고 **호출 측이 프로세스를 끊은 것**이었다 —
`--background`가 detach되지 않고 스트리밍하는데 도구 타임아웃(2분)이 걸려 `exit 143`으로 죽었다.
1·2차 실패를 `gradlew test`의 `exit 124` 탓으로 지목했던 최초 판단은 틀렸고, 그 124도 같은 타임아웃의 증상이었다.
harness 백그라운드로 완전히 분리하고 타임아웃을 늘린 4차에서 완주했다.

리뷰어가 테스트를 재실행하다 죽는 것을 막기 위해 실측 결과를 미리 확보해 전달했다(아래 [개발 기록] 참조).

| # | 등급 | 항목 | 근거 |
|---|---|---|---|
| B1 | high · blocking | `/settings`가 블로그 설정과 일부 프로필 필드를 구현하지 않음 | `SettingsProfilePage.tsx:5` |
| B2 | high · blocking | 공개 블로그 응답이 실명을 노출 | `BlogSettingsDtos.java:73` |
| B3 | medium · blocking | 1440px 실제 렌더 대조 미수행 | `docs/worklog/M2-settings.md:473` |
| N1 | low · non-blocking | soft-delete 제외 테스트에 assertion이 없어 fake-pass | `BlogRepositoryTest.java:298` |

**리뷰어가 통과로 확인한 항목**: initial-setup의 `@Transactional` + `PESSIMISTIC_WRITE` 잠금과 실제 MySQL
독립 트랜잭션 테스트(1 성공·1 `BLOG_004`), soft-delete 조건·unique 자기제외 쿼리·principal 기반 두 사용자
소유권 테스트.

N1은 등급상 non-blocking이지만 내용은 워크로그의 "fake-pass 0건" 주장을 직접 반증한다. 322개 통과라는
수치가 최소 한 곳에서는 아무것도 보장하지 않았다.

## [개발 기록] — 리뷰 반영

### 2026-07-26 19:20 · 1차 리뷰 반영 (B1·B2·B3·N1 전부)

**사전 조치 — 테스트 실측 확보.** 리뷰어가 빌드 명령에서 죽는 것을 막으려고 먼저 돌려 근거로 넘겼다.
이 과정에서 이 저장소의 Gradle 실행에 두 가지 함정이 확인됐다:

- `./gradlew`(bash 래퍼)는 `--no-daemon`·`--rerun-tasks`·`--tests` 등 **모든 `--` 옵션을 삼킨다**
  (`gradlew: unknown option`). `gradlew.bat`을 쓰면 정상 전달된다.
- 플래그 없이 `test`만 돌리면 `:test UP-TO-DATE`로 **조용히 건너뛴다**. 첫 두 번의 "BUILD SUCCESSFUL"은
  테스트를 한 건도 실행하지 않은 캐시였다. `cleanTest test`가 필요하다.

**B2 — 공개 응답 실명 제거.** `PublicBlogResponse.OwnerInfo`에서 `name`을 뺐다(`BlogSettingsDtos.java`,
`BlogSettingsService.java`, FE `types.ts`). 인증 없이 열리는 경로에 가입·설정에서 수집한 실명을 실을 근거가
정본에 없고, 디자인 히어로도 소유자를 nickname으로만 표시한다. 회귀 방어로 **실명을 실제로 채운 사용자**로
응답 전문에 실명이 없음을 확인하는 테스트를 넣었다 — `@JsonInclude(NON_NULL)` 때문에 값이 null이면 필드가
사라져 통과하는 착시가 생기기 때문이다.

**N1 — fake-pass 정정.** `V1__init.sql:46`이 `url_slug ... UNIQUE`로 `deleted_at`과 무관한 전역 유니크임을
확인했고, 이 메서드를 쓰는 곳이 slug 발급(`allocateSlug`)이므로 **삭제된 행까지 포함하는 쪽이 올바른 계약**이다.
즉 구현이 아니라 테스트 이름이 틀렸다. 제외했다면 삭제된 블로그의 slug를 후보로 골라 INSERT에서 터진다.
1개 테스트를 3개로 분리했다: 전역 포함(true/false 양쪽), 자기제외·삭제제외(false), 살아있는 충돌(true).
마지막 하나가 없으면 항상 false를 돌려주는 구현도 통과한다.

**B1 — `/settings` 범위 보강.** 디자인 정본(`ZeroVerse Pages.dc.html:295-298`)의 프로필 카드는
`닉네임 / 이메일 / 블로그 이름 / 한 줄 소개`이고 PRD §7이 `birth_date`·`profile_image_url` 추가를 지시한다.
여기서 `한 줄 소개`가 `user.bio`인지 `blog.description`인지 갈렸다 — 디자인의 값이 초기설정 화면의
`한 줄 소개`(=`blog.description`)와 같은 문구라 후자로 읽을 여지가 있었다. **사용자 결정: `user.bio` 유지,
블로그 설정은 별도 카드로 분리, 저장은 카드별로 자기 API만 호출.** 라벨 충돌을 피해 블로그 쪽은 `블로그 소개`로 뒀다.
slug 입력은 포함했다 — 초기설정 카피가 "설정에서 나중에 변경할 수 있어요"라고 약속하고(ADR-0004)
FR-SETTINGS-03의 수정 가능 필드에 `url_slug`가 있다. 디자인에 없는 필드지만 그 사실만으로 요구사항 필드를
지우지 않는다(AGENTS.md).

행동 테스트 7건을 추가했다(로드·PUT 호출·BLOG_002·카드 간 상태 독립·slug 미변경 저장·신규 프로필 필드).
처음 작성한 3건이 실패했는데 원인이 **로드 완료를 기다리지 않음**이었다 — 그대로 뒀다면 `getBlog()` 호출을
통째로 지워도 통과하는, N1과 같은 종류의 테스트가 됐다. 값이 실제로 채워질 때까지 기다리는 헬퍼로 고쳤다.

**B3 — 1440×1200 실제 브라우저 렌더 대조.** Playwright + Chromium으로 수행했다. 환경이 갖춰져 있지 않아
MySQL 컨테이너(`zeroverse-local-mysql`, 13306)와 `application-local.yml`(gitignore 대상, 로컬 전용 키)을
만들어 백엔드·Vite를 띄우고 가입 → 초기설정 → 공개 블로그 → 설정 순으로 실제 조작했다.

**여기서 값 대조가 놓친 결함이 나왔다.** `OnboardingScaffold.tsx:34`가 `children`을 `<p>`로 감싸고 있어
`<p>` 안에 `<div>`·`<p>`·`<form>`이 들어가는 **잘못된 HTML**이었다(React hydration 경고). M0의 빈 상태
placeholder용 마크업이 M1·M2에서 실제 폼을 받으면서 생긴 문제다. 부수 효과로 `text-center`가 폼 라벨까지
가운데로 밀어 정본의 좌측 정렬과 어긋나 있었다. children을 직접 렌더하고 가운데 정렬은 placeholder에만
남기도록 고쳤다. **콘솔 오류 8건 → 0건.**

Gate 6이 이 화면을 "코드 값 ↔ 정본 값 대조 일치"로 기록했던 항목이다. 값 대조로는 원리적으로 잡히지 않는
종류라, B3를 blocking으로 판정한 리뷰어 판단이 맞았다.

검증 결과: `/settings` 신규 필드 4종이 모두 서버 값으로 채워짐, 초기설정 후 `/blog/{slug}` 정상 이동,
공개 히어로에 실명 미노출, 가로 오버플로우 `/blog/setup` 0px · `/settings` 0px, 콘솔·네트워크 오류 0건.

**검증**: BE **325 tests** / 52 suites · 0 failures · 0 errors · **0 skipped**(`cleanTest test`, 7m30s) ·
FE **233 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공.
BE는 322 → 325(추가한 테스트 수와 일치), FE는 226 → 233.

### 2026-07-26 21:35 · 정본 시각 차이 2건 반영 + 재검증

B3에서 기록만 해 뒀던 `/blog/setup` 카드 구조와 `/settings` 라벨 배치를 정본에 맞췄다(상세는 [이슈·결정]).
같은 Playwright 절차로 1440×1200 재검증했다 — 세 화면 렌더 확인, `/settings` 신규 필드 4종이 서버 값으로
채워짐, 가로 오버플로우 `/blog/setup` 0px · `/settings` 0px, **콘솔·네트워크 오류 0건**.

**검증**: FE **233 tests** / 21 파일 · 0 failures. BE는 이 라운드에서 변경 없음(FE 전용 수정).

**검증 환경 정리 완료**: MySQL 컨테이너(`zeroverse-local-mysql`) 삭제, `application-local.yml` 삭제,
스크린샷·스크립트 등 스크래치 산출물 삭제. 저장소에 남은 것은 소스·테스트·이 워크로그뿐이다.

### 2026-07-26 22:10 · Codex 2차 리뷰 반영

2차 리뷰(`c7ac119` 대상)는 1차 지적 4건의 해소를 확인했으나 **새 결함 2건 + 비블로킹 1건**을 냈다.
두 건은 1차 반영이 만든 것이다.

**[blocking] 프로필 저장이 블로그 카드의 미저장 입력을 덮어썼다.** 로드 effect가 `user` 객체 전체에
의존하는데(`SettingsProfilePage.tsx:112`) 프로필 저장은 `refreshUser()`를 부르고 `/auth/me` 응답은 매번
새 객체로 들어온다(`authContext.tsx:66`의 `setUser(me)`). 그래서 저장할 때마다 effect가 다시 돌아
`getBlog()` 결과로 `blogForm`을 덮었다 — 블로그 제목·slug를 고쳐 놓고 프로필을 저장하면 입력이 경고 없이
사라졌다. 의존성을 `user.id`로 좁히고, 사용자가 손댄 폼은 재조회 결과로 덮지 않도록 dirty 플래그를 뒀다
(저장 성공 시 해제).

1차에서 추가한 "카드 간 상태 독립" 테스트가 이걸 놓친 이유는 **성공 메시지의 독립성만 봤기 때문**이다.
폼 값의 독립성은 검증하지 않았다. 회귀 테스트를 추가하고 **뮤테이션으로 실효성을 확인했다**:
의존성만 되돌리면(`[userId, user]`) dirty 플래그가 막아 17건 전부 통과하고, dirty 가드까지 함께 제거하면
해당 1건이 FAILED — 편집값 `아직 저장 안 한 제목`이 서버 값 `테스터의 블로그`로 덮이는 것을 정확히 잡는다.

**[blocking] 비밀번호 카드가 정본과 불일치.** 정본(`ZeroVerse Pages.dc.html:304-308`)은 두 입력과 `변경`
버튼이 한 행(`align-items:flex-end`, 패딩 `20px 24px`, 라벨 `12px/600/#9b8aa8`)인데 구현은 2열 그리드 +
버튼이 아래 행이었다. **이 항목은 직전 라운드에서 내가 "리뷰 지적이 아니고 범위 밖"으로 판단해 기록만
남겼던 것이다.** 리뷰어는 PRD §12 DoD(정본과 시각적 일치) 위반이고 워크로그가 스스로 미반영을 인정하므로
blocking이라고 판정했고, 그 판정이 맞다 — 범위 판단이 느슨했다. `FormField`에 `labelTone` 옵션을 추가해
정본대로 한 행으로 맞췄다. 정본에 없는 힌트 줄은 제거하고 정책 문구(8~64자)를 placeholder로 옮겨
`items-end` 정렬을 깨지 않게 했다.

**[non-blocking] 신규 프로필 필드 테스트가 수정 경로를 검증하지 않았다.** 서버가 준 값을 표시하는지만 봐서
`onChange`나 PUT payload에서 `birthDate`·`profileImageUrl`을 지워도 통과했다. N1에서 지적당한 것과 같은
유형을 또 만든 것이다. 두 필드를 실제로 고쳐 저장하고 `PUT /users/me` 본문을 단정하는 테스트로 교체했다.

**1440×1200 재대조**: 비밀번호 카드의 두 입력과 버튼 하단 좌표가 모두 1133px로 한 행 확인, 블로그 제목을
고친 뒤 프로필을 저장해도 편집값 유지 확인, 가로 오버플로우 0px.
`/auth/refresh` 401 1건이 남는데, 쿠키가 없는 새 브라우저 컨텍스트가 부팅 시 세션 복구를 시도하는 M1의
정상 동작이며 이후 흐름에 영향이 없다.

**검증**: FE **235 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공(233 → 235).
BE는 이 라운드에서 변경 없음(FE 전용 수정).

### 2026-07-26 22:25 · Codex 3차 리뷰 반영

3차 리뷰(`9f8efab` 대상)는 2차 지적 3건의 해소를 확인하고 **blocking 2건**을 새로 냈다.

**[blocking] 저장 중 이어서 친 입력이 성공 응답에 덮인다.** 2차 반영의 dirty 플래그는 저장 완료 시
**무조건** 해제하고 서버 응답으로 폼을 교체했다. 입력은 요청 중에도 활성이라 `저장`을 누른 뒤 응답이
오기 전에 계속 칠 수 있고, 그 최신 입력이 조용히 사라진다. 2차에서 추가한 회귀 테스트는 저장 **전에만**
편집하고 mock이 즉시 응답해 이 경합을 통과시켰다.

폼별 revision 카운터를 두고 제출 시점 값을 캡처해, 응답 시점에 revision이 그대로일 때만 dirty를 해제하고
서버 값을 반영하도록 고쳤다. 지연 응답(테스트가 붙잡았다 놓는 promise)으로 경합을 재현하는 테스트를
추가하고 뮤테이션으로 확인했다 — revision 가드를 제거하면 `저장 후 이어서 친 제목`이
`서버가 돌려준 제목`으로 덮이며 FAILED.

**같은 결함을 두 라운드 연속으로 냈다.** 2차에서 "폼 값 독립성"을 놓쳤고, 3차에서 "요청 중 편집"을
놓쳤다. 둘 다 *언제* 덮어쓰는지를 따지지 않고 *덮어쓰지 않는다*는 결론만 테스트한 결과다.
비동기 경계가 있는 상태 갱신은 **지연 응답으로 경합을 만들어** 확인해야 한다.

**[blocking] 비밀번호 변경 정책이 회원가입과 다르다.** FR-SETTINGS-02는 "새 비밀번호는 회원가입과 동일한
정책"을 요구하는데 두 DTO가 각자 정규식을 들고 있었고 실제로 갈라져 있었다 — 회원가입은 모든 비영숫자를
특수문자로 인정하고(`[^A-Za-z0-9]`), 변경은 열거된 ASCII 기호만 받았다. 그래서 `Passw0rd한`이나 공백을
포함한 비밀번호로 **가입은 되는데 변경은 400**으로 막혔다.

정규식·길이·메시지를 `common/util/PasswordPolicy.java` 한 곳에 두고 두 DTO가 그 상수를 참조하게 통합했다
(애노테이션 인자라 컴파일 타임 상수여야 한다). 다시 갈라지면 잡히도록 같은 입력에 두 엔드포인트가 같은
판정을 내리는지 확인하는 계약 테스트를 9개 경계값으로 추가했다 — 한글·공백·기호 특수문자 3종, 구성 위반
3종(숫자·영문·특수문자 누락), 길이 위반 2종(7자·65자), 기본 통과 1종.

**이 결함은 이번 라운드가 만든 것이 아니라 M2 Gate 3/4 코드에 원래 있던 것이다.** 1·2차 리뷰가 짚지
못했고, 화면 쪽 힌트 문구를 정본에 맞추는 과정에서 정책 문구를 들여다보게 되면서 3차에 드러났다.

**검증**: BE **334 tests** / 53 suites · 0 failures · 0 errors · **0 skipped**(`cleanTest test`, 7m48s,
325 → 334로 계약 테스트 9건과 일치) · FE **236 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공.
BE 회원가입 정규식은 값 변경 없이 상수로만 옮겼고 기존 인증 테스트가 그대로 통과한다.
이 라운드는 BE 검증 로직과 FE 상태 처리만 바꿔 화면 렌더에 영향이 없어 1440px 재대조는 하지 않았다.

### 2026-07-26 23:15 · Codex 4차 리뷰 반영

4차 리뷰(`12e92b2` 대상)는 3차 지적 2건의 해소를 확인하고 **blocking 2건 + non-blocking 1건**을 냈다.
이번 blocking 2건은 모두 **M2 원래 코드의 결함**이며 1~3차 리뷰가 짚지 못한 것이다.

**[blocking] 빈 slug 초기설정이 자기 slug를 중복으로 오인했다.** 가입 시 `UserRegistrar`가 nickname 기반
slug(`nick`)로 기본 블로그를 만든다. 그런데 초기 설정에서 slug를 비우면 `allocateSlug`가 자기 블로그를
제외하지 않은 전역 `existsByUrlSlug`로 검사해, 첫 후보 `nick`이 **자기 것** 때문에 점유 상태로 나오고
`nick-2`를 발급했다 — **중복이 하나도 없는데 공개 URL이 바뀌었다.**

수정은 두 가지 길이 있었다. 처음엔 "빈 slug면 기존 slug 유지"로 갔다가, FR-SETTINGS-04의 문구가
"nickname 기반 slug를 자동 생성한다"이므로 **자동 생성은 유지하되 자기 블로그만 제외**하는 쪽으로 바꿨다.
`existsByUrlSlugAndIdNot`을 신설했다 — 자기 자신은 빼되 **soft delete된 블로그는 포함**한다.
`url_slug`가 `deleted_at`과 무관하게 전역 UNIQUE라, 기존 `...AndIdNotAndDeletedAtIsNull`을 쓰면 삭제된
slug를 후보로 골랐다가 제약 위반이 난다(이 구분은 N1에서 정리한 계약과 같다).

**기존 테스트가 이 버그를 놓친 이유**: `emptySlugAutoGenerates`가 블로그 slug를 `default-slug`로 만들어
**실제 가입 상태를 재현하지 않았다**. 후보 `auto-nick`이 아무에게도 점유돼 있지 않으니 자기 제외 여부와
무관하게 통과했다. 실제 가입 그대로(닉네임 기반 slug) 세운 테스트와, 자기 제외가 "아무나 제외"로 번지지
않는지 확인하는 테스트(남의 블로그가 내 후보 slug를 선점한 경우 → `-2`)를 추가했다.

**[blocking] 저장 성공 후 도착한 오래된 초기 GET이 폼을 되돌렸다.** 3차에서 고친 건 PUT 응답 경합이고
이건 **초기 GET** 경합이다. GET이 느릴 때 사용자가 값을 넣고 저장까지 마치면 dirty가 풀리는데, 그 뒤
도착한 저장 이전 상태의 GET이 dirty 검사만 통과해 방금 저장한 값을 옛날 값으로 덮었다. 조회 세대
카운터를 두고 저장 성공 시 세대를 올려 진행 중이던 조회를 무효화했다. GET을 붙잡고 편집 → 저장 성공 →
오래된 GET 해제 순서로 재현하는 테스트를 추가했고, 세대 가드를 제거하면 FAILED로 전환된다.

> 이 테스트를 짜다 알게 된 것: 초기 GET을 붙잡으면 폼이 비어 `required`에 막혀 저장 자체가 안 된다.
> 실제 사용자도 조회 전에는 저장할 수 없어서 이 경합의 실현 조건은 리뷰어가 상정한 것보다 좁다.
> 다만 slug만 채우면 성립하므로 결함 자체는 유효하다.

**[non-blocking] 계약 테스트가 8자·64자 허용 경계를 고정하지 않았다.** 7자·65자 거부만 있어
`MIN_LENGTH`가 9로 바뀌어도 표본이 통과했다. 정확히 8자·64자인 유효 입력을 추가해 양끝을 고정했다.
직전 커밋 메시지의 "9개 경계값"은 리뷰어 지적대로 "9개 사례"가 정확한 표현이었다.

**이번 라운드에서 낸 실수**: 자기 제외 검증 테스트를 처음에 닉네임 대소문자 충돌(`dupnick`/`Dupnick`)로
재현하려다 BE가 1건 실패했다. MySQL 기본 collation(`utf8mb4_0900_ai_ci`)이 대소문자를 구분하지 않아
닉네임 unique에 걸렸다. 블로그 slug는 닉네임과 독립적으로 정할 수 있으므로 **남의 블로그가 내 후보
slug를 선점한 상황**을 직접 세우는 쪽으로 바꿨다 — 검증 의도도 이쪽이 더 정확하다.

**검증**: BE **338 tests** / 53 suites · 0 failures · 0 errors · **0 skipped**(`cleanTest test`, 9m10s,
334 → 338) · FE **237 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공(236 → 237).
화면 구조 변경이 없어 1440px 재대조는 하지 않았다.

### 2026-07-26 23:35 · Codex 5차 리뷰 반영

5차 리뷰(`4acfed1` 대상)는 4차 지적 3건의 해소를 확인하고 **blocking 1건 + non-blocking 2건**을 냈다.
blocking은 **직전 라운드가 만든 회귀**다.

**[blocking] 공유 조회 세대가 다른 카드의 초기 GET까지 폐기했다.** 4차에서 도입한 `loadGeneration`을
프로필·블로그 조회가 하나로 공유하는데, 어느 카드든 저장에 성공하면 이를 증가시켰다. 그래서 프로필 GET은
끝났지만 블로그 GET이 지연된 상태에서 프로필을 저장하면 블로그 응답이 세대 검사에서 버려졌다.
`refreshUser()`는 `userId`를 바꾸지 않아 effect도 재실행되지 않으므로 **블로그 폼은 새로고침 전까지 빈
채로 남는다**. 반대 방향도 같다. 카드 독립성을 깨는 실제 로드 실패다.

`profileLoadGeneration`·`blogLoadGeneration`으로 분리하고 각 저장이 자기 카드 조회만 무효화하게 했다.
양방향 교차 테스트 2건을 추가했고, 세대를 다시 공유하도록 되돌리면 FAILED로 전환된다.

**직전 라운드의 테스트가 이걸 못 잡은 이유**는 추가한 지연 테스트가 **같은 카드의 GET→PUT 경합만**
재현했기 때문이다. 경합을 고치면서 그 수정이 다른 카드에 미치는 영향은 검증 범위에 넣지 않았다.

**[non-blocking] soft delete slug 점유 계약이 테스트로 고정되지 않았다.** 새 쿼리
`existsByUrlSlugAndIdNot`은 전역 UNIQUE 때문에 삭제된 블로그도 점유로 세도록 올바르게 작성됐지만,
4차에서 추가한 테스트는 자기 블로그와 살아 있는 블로그만 다뤘다. 쿼리를 `...AndDeletedAtIsNull`로 바꿔도
전부 통과하는 상태였다. soft delete된 블로그가 첫 후보 slug를 쥔 상황에서 `-2`로 건너뛰는지 확인하는
테스트를 추가했다.

**[non-blocking] 지연 GET 테스트의 완료 대기가 GET 처리를 증명하지 않았다.**
`callsTo(/\/blogs\/me$/).length >= 2`는 저장 시점에 이미 GET+PUT으로 2회라 즉시 통과할 수 있었다.
뮤테이션 실측이 유효했을 뿐 테스트의 동기화 조건 자체는 경합 결과를 관찰하지 않았다. 지연 GET 핸들러가
응답을 돌려준 시점을 별도 promise로 표시하고 그것을 기다리도록 바꿨다.

**검증**: BE **339 tests** / 53 suites · 0 failures · 0 errors · **0 skipped**(`cleanTest test`, 11m01s,
338 → 339) · FE **239 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공(237 → 239).
화면 구조 변경이 없어 1440px 재대조는 하지 않았다.

### 2026-07-27 22:40 · Codex 6차 리뷰 반영

6차 리뷰(`0354699` 대상)는 5차 blocking의 해소를 확인하고 **blocking 3건 + non-blocking 1건**을 냈다.
이번엔 세 건 모두 **직전 수정이 만든 회귀가 아니라 M2 전반에 걸쳐 있던 결함**이다.

**[blocking] 지연 로드 중 편집하면 화면에 오지 않은 값이 지워졌다.** `PUT`은 부분 수정이 아니라 전체
교체다. 조회가 느린 동안 폼은 빈 상태로 그려지는데, 그때 `required` 필드만 채워 저장하면 아직 도착하지
않은 `bio`·`birthDate`·`profileImageUrl`·`description`이 전부 빈 값으로 전송되어 서버의 기존 값이
지워진다. **화면에 보이지도 않은 값을 사용자가 지울 수는 없어야 한다.**

필드별 dirty 병합 대신 **조회 완료 전 저장 자체를 차단**했다(`profileLoaded`/`blogLoaded`로 버튼 비활성).
병합은 "무엇을 보냈는지"를 사용자가 알 수 없게 만들고 상태가 하나 더 늘지만, 차단은 계약이 단순하고
"보이는 것만 저장된다"가 자명하다. 로드 게이트를 제거하는 뮤테이션에서 FAILED로 전환된다.

이 수정으로 5차에서 추가했던 "저장 성공 후 오래된 GET" 테스트는 **도달 불가능한 시나리오**가 됐다
(조회 전엔 저장할 수 없으므로). 해당 테스트를 로드 게이트 계약을 검증하는 것으로 교체했고,
6차 non-blocking으로 지적된 그 테스트의 느슨한 동기화 문제도 함께 사라졌다.

**[blocking] 동시 쓰기로 성공한 변경이 되돌려질 수 있었다.** 프로필 수정과 비밀번호 변경은 각각 User를
조회해 자기 필드만 바꾸고 독립적으로 flush한다. Hibernate 기본 UPDATE는 **모든 컬럼**을 쓰므로 둘이 옛
행을 함께 읽으면 나중 flush가 상대의 변경을 낡은 값으로 덮는다 — 비밀번호를 바꾸고 성공 응답까지 받았는데
동시에 저장된 프로필이 옛 해시를 되돌려 놓는다. 화면의 두 카드가 각각 독립된 저장 버튼을 가지므로 실제로
도달 가능한 경로다. `Blog`도 `updateBlog`(비잠금)과 `initialSetup`(잠금)이 같은 방식으로
`is_setup_completed`를 되돌릴 수 있었다.

`User`·`Blog`에 `@DynamicUpdate`를 붙여 변경된 컬럼만 쓰도록 했다. **`@Version`(낙관적 잠금)은 도입하지
않았다** — 스키마 변경(Flyway 마이그레이션)과 충돌 응답 계약이 따라와 M2 범위를 넘고, 리뷰가 지적한 핵심
시나리오는 `@DynamicUpdate`로 해소된다. 같은 컬럼을 동시에 고치는 last-write-wins는 남으며 그 한계를
엔티티 주석에 적었다. 프로필↔비밀번호 동시 제출 테스트를 추가했고, `@DynamicUpdate`를 떼면 FAILED다.

**[blocking] 빈 slug가 자동 생성 전에 400으로 막혔다.** `InitialSetupRequest.urlSlug`의 `@Size(min = 3)`가
`""`를 거부하는데 컨트롤러의 `@Valid`는 서비스보다 먼저 돈다. FR-SETTINGS-04의 "비어 있으면 nickname 기반
생성"이 **HTTP 경계에서 깨져 있었다**. 프런트가 빈 값을 아예 생략해 보내는 덕에 가려져 있었고,
빈 slug 테스트들은 서비스를 직접 불러 Bean Validation을 우회했다.

하한을 제거했다. 비어 있지 않은 값의 형식·길이·예약어는 `Blog.initialSetup`이 `SlugGenerator.isValid`로
검증해 BLOG_003을 던지므로 하한을 겹쳐 걸 이유가 없었다. MockMvc로 `""`·`null`·`"ab"`(너무 짧음) 3케이스를
HTTP 경계에서 확인한다.

**검증 환경 문제**: 첫 BE 실행이 210건 실패했는데 `NoClassDefFoundError: Could not initialize class
MySqlTestSupport` → `Could not find a valid Docker environment`였다. 세션 사이 머신 재시작으로 Docker
Desktop이 내려가 Testcontainers가 MySQL을 띄우지 못한 것이다. 처음엔 FE와 동시 실행한 자원 고갈로
추정했으나 스택을 확인해 원인을 정정했다. **테스트 대량 실패 시 첫 실패의 스택부터 본다.**

**검증**: BE **343 tests** / 53 suites · 0 failures · 0 errors · **0 skipped**(`cleanTest test`, 8m40s,
339 → 343) · FE **239 tests** / 21 파일 · 0 failures(테스트 교체라 건수 동일) · lint exit 0 · build 성공.

### 2026-07-27 23:05 · Codex 7차 리뷰 반영 (blocking 1건)

7차 리뷰는 **최종 판정 직전에 프로세스가 죽었지만**(6차 1차 시도와 같은 증상) 그 전에 blocking 1건을
로그에 확정해 남겼다. 그 1건이 **6차 수정의 실제 구멍**이었다.

**[blocking] 로드 게이트가 전체교체 데이터 손실을 막지 못했다.** 저장 버튼만 잠갔더니 경로가 하나 남아
있었다 — 로드 전에 사용자가 한 필드라도 건드리면 `dirty`가 서고, 그러면 도착한 응답이 "편집 중"이라는
이유로 폼 반영을 건너뛴다. 편집하지 않은 `bio`·`birthDate`·`profileImageUrl`·`description`은 초기 빈
값으로 남은 채 잠금만 풀리고, 다음 저장에서 그대로 삭제된다. **막으려던 손실이 경로만 바꿔 남아 있었다.**

`<fieldset disabled>`로 카드 전체를 잠갔다. 입력이 막히면 dirty가 설 수 없으므로 응답은 항상 폼을 채운다.
필드별 dirty 병합보다 계약이 단순하고("보이는 것만 저장된다") 상태도 늘지 않는다.

**직전 테스트가 이걸 통과시킨 이유**: 서버 `description`을 준비해 놓고 정작 그 값이 폼에 채워지는지도,
PUT 본문에 실리는지도 단정하지 않았다. 리뷰 권고대로 (1) 로드 전 입력이 잠겼는지, (2) 잠금 해제 후
미편집 필드가 서버 값으로 채워지는지, (3) 제목만 고쳐 저장했을 때 PUT 본문에 소개가 그대로 실리는지를
모두 단정하도록 고쳤다. `fieldset` 잠금을 제거하는 뮤테이션에서 FAILED로 전환된다.

**검증**: FE **239 tests** / 21 파일 · 0 failures · lint exit 0 · build 성공. BE는 이 라운드에서 변경
없음(FE 2개 파일만 수정) — 직전 실행의 343 tests 결과가 그대로 유효하다.

중간에 BE가 1건 실패했다 — `BlogSettingsServiceTest.java:384`가 제거된 `name` 속성을 `extracting`으로 꺼내
`IntrospectionError`가 났다. 리뷰가 지적하지 않은, 이번 수정이 만든 회귀다. 실명 제외 검증으로 바꿔 해소했다.

## [이슈·결정] — 리뷰 반영분

- 2026-07-26 · **`OwnerInfo.name` 확정** — 제거. 이전 항목의 "Gate 6 시각 대조에서 확정" 대기 상태를 종료한다.
  근거: 디자인 히어로가 소유자를 nickname으로만 표시하고 공개 FE도 `owner.name`을 쓰지 않으며, FR-BLOG-01의
  "소유자 기본 정보"에 실명이 명시돼 있지 않다. 실명 공개가 제품 요구가 되면 정본에 결정을 먼저 기록한다.
- 2026-07-26 · **라이브 브라우저 렌더링 미확인 항목 종료** — B3로 수행 완료. `RISK-0005`(운영 HTTPS 쿠키
  smoke)는 별개 항목으로 최초 배포 전 게이트를 그대로 유지한다.
- 2026-07-26 · **해소 — `/blog/setup` 카드 구조를 정본에 맞췄다.** 정본(`ZeroVerse Pages.dc.html:245-249`)대로
  아이브로우·제목·설명을 `surface-raise`(#ffe9c9) 배경 + `border-bottom:3px` **헤더 밴드**로 묶고, 본문의
  `border-2 border-shadow` 내부 상자를 걷어냈다. 패딩은 정본 `24px 32px`, 제목 `23px→21px`, 아이브로우
  `letter-spacing` 제거. `BlogInitialSetupPage`의 폼도 중복 패딩을 걷고 필드 간격 16px·버튼 여백 4px로 맞췄다.
  **최초 판단 정정**: "고치면 `/signin`·`/signup`까지 바뀐다"고 기록했으나 틀렸다. `OnboardingScaffold`는
  `BlogInitialSetupPage`에서만 쓰이고 인증 화면은 `AuthCard`를 쓴다. M1 영향은 없고 순수 M2 범위였다.
- 2026-07-26 · **해소 — `/settings` 프로필 카드 라벨 배치를 정본에 맞췄다.** `FormField`에
  `orientation="inline"`(정본 `grid-template-columns:90px 1fr`, 라벨이 입력 왼쪽)과 `surface="warm"`(정본
  입력 배경 #fff8ec)을 옵션으로 추가하고 프로필·블로그 카드에 적용했다. **기본값은 기존 동작 그대로**라
  `/blog/setup` 등 다른 화면은 영향받지 않는다. textarea는 정본대로 라벨을 위쪽 정렬(`align-items:start` +
  라벨 `padding-top:8px`)했다.
  라벨 `프로필 이미지 주소` → `프로필 이미지`로 변경했다. `주소 (slug)`와 부분 일치해 셀렉터가 두 필드를
  동시에 잡는 문제가 브라우저 검증 중 실제로 발생했다.
- 2026-07-26 · ~~미반영 — `/settings` 비밀번호 카드 배치.~~ → **2차 리뷰에서 blocking으로 판정되어 반영 완료.**
  "리뷰 지적이 아니고 범위 밖"이라는 최초 판단이 틀렸다. 정본과의 시각 불일치를 **워크로그에 스스로 적어 두면**
  그 순간부터 PRD §12 DoD 위반이 문서화된 상태가 된다. 앞으로 시각 차이를 발견하면 "기록만 남긴다"로
  미루지 말고 정본에 맞추거나, 미루는 근거를 정본 문서 쪽에 결정으로 남겨야 한다.
- 2026-07-26 · **비밀번호 정책은 `PasswordPolicy` 한 곳에서만 정의한다.** FR-SETTINGS-02가 "회원가입과
  동일한 정책"을 요구하는데 두 DTO가 각자 정규식을 들고 있어 실제로 갈라졌다. 같은 규칙을 두 곳에 적어 두면
  요구사항 문구가 아무리 명확해도 갈라진다 — 앞으로 공통 정책은 상수 한 곳 + 계약 테스트로 묶는다.
- 2026-07-27 · **"저장 금지"만으로는 부족하다 — 조회 전에는 입력까지 막아야 한다.** 입력을 열어 두면
  한 글자만 쳐도 dirty가 서고, dirty는 도착한 응답이 폼을 채우는 것을 막는다. 결국 미편집 필드가 빈 채로
  남아 같은 삭제가 일어난다. **"편집 중이면 응답을 덮지 않는다"와 "응답이 와야 폼이 채워진다"가 한 화면에
  공존하면, 그 사이에 값을 잃는 창이 생긴다.**
- 2026-07-27 · **전체 교체(PUT) API를 쓰는 화면은 "조회 완료 전 저장 금지"가 기본이다.** 부분 수정이
  아니므로 화면에 아직 오지 않은 값이 그대로 비어서 전송된다. 사용자가 보지 못한 값을 지우게 두는 것보다
  저장을 잠그는 편이 계약이 단순하고 안전하다. M4 이후 글 작성·수정 화면에도 같은 기준을 적용한다.
- 2026-07-27 · **같은 행을 여러 엔드포인트가 각자 쓰면 `@DynamicUpdate`가 없는 한 서로를 덮는다.**
  Hibernate 기본 UPDATE는 전 컬럼을 쓴다. 도메인마다 독립 저장 버튼이 늘어나는 구조(설정 화면의 3카드)에서는
  기본값이 곧 lost update다. 낙관적 잠금이 필요한지는 별개 판단이되, 최소한 `@DynamicUpdate`는 기본으로 둔다.
- 2026-07-27 · **DTO 검증이 서비스 계약을 앞질러 막는지 확인한다.** `@Valid`는 컨트롤러에서 서비스보다 먼저
  돈다. `@Size(min)` 같은 하한을 습관적으로 붙이면 "빈 값이면 자동 생성" 같은 FR이 HTTP 경계에서 조용히
  깨진다. 서비스·엔티티가 이미 검증하는 규칙을 DTO에 겹쳐 걸지 않는다. **서비스 직접 호출 테스트는 Bean
  Validation을 우회하므로, HTTP 계약은 MockMvc로 확인해야 한다.**
- 2026-07-26 · **경합을 고칠 때는 그 수정이 옆 경로에 미치는 영향까지 테스트 범위에 넣는다.**
  4차의 조회 세대 카운터는 같은 카드의 GET→PUT 경합만 재현해 검증했고, 그 카운터를 두 카드가 공유한다는
  사실이 만든 교차 회귀는 5차에서야 드러났다. **공유 상태를 새로 도입하면 그것을 공유하는 모든 주체에 대해
  양방향으로 확인한다.**
- 2026-07-26 · **테스트 픽스처가 실제 흐름을 재현하지 않으면 그 경로의 결함은 영원히 통과한다.**
  빈 slug 자동 할당 버그는 픽스처가 블로그 slug를 `default-slug`로 만들어 실제 가입 상태(닉네임 기반 slug)를
  재현하지 않은 탓에 세 번의 리뷰를 통과했다. 상태 전이를 검증할 때는 **그 상태를 만드는 실제 경로와 같은
  값**으로 세운다.
- 2026-07-26 · **비동기 경계가 있는 상태 갱신은 지연 응답으로 경합을 만들어 검증한다.** 저장 중 편집 유실
  결함을 2·3차 두 라운드 연속으로 냈다. 즉시 응답하는 mock은 "요청 중 사용자가 계속 입력한다"는 실제 조건을
  만들지 못해, *덮어쓰지 않는다*는 결론만 통과시키고 *언제* 덮어쓰는지는 검증하지 못한다.
- 2026-07-26 · **테스트가 "상태 독립"을 주장할 때 무엇의 독립인지 명시한다.** 1차 반영의 카드 독립성 테스트는
  성공 메시지의 독립성만 검증하고 폼 값의 독립성은 보지 않아, 프로필 저장이 블로그 입력을 덮어쓰는 결함을
  통과시켰다. 같은 라운드에서 fake-pass(N1)를 지적받고 고치면서 새 fake-pass를 두 건 더 만들었다 —
  **뮤테이션으로 실효성을 확인하지 않은 신규 테스트는 신뢰하지 않는다**는 규칙이 이 마일스톤에서 세 번 검증됐다.
- 2026-07-26 · **로컬 검증 환경은 저장소에 없다.** `application-local.yml`이 gitignore 대상이고 상시 MySQL도
  없어(테스트는 Testcontainers) 브라우저 검증에는 매번 컨테이너·설정 구성이 선행된다. 앞으로 시각 대조가
  필요한 마일스톤마다 반복되므로 절차를 문서화하거나 스크립트로 남기는 편이 낫다.

## [머지]

- 아직 없음.
