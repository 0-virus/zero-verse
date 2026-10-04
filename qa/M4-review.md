# M4 게시글·콘텐츠·로컬 이미지 독립 QA 인수 계획

- 기준 시각: 2026-10-03 KST
- 기준 브랜치/기준점: `feature/M4-posts` / `97567d9` (기존 dirty 문서 변경은 보존)
- 현재 판정: **준비 중 — 제품 구현 진행 중이며 PASS/완료 아님**
- 범위: M4 회의록 §20.1~§20.8, `docs/worklog/M4-posts.md`의 승인된 Implementation Plan Task1~7, REQUIREMENTS FR-BLOG-02·FR-POST-01~08·FR-UPLOAD-01~04·NFR-02/04/06~09, PRD §4~7·§9.7·§10~§12, ADR-0005의 M4 `category_id` lock 인계
- 저장 결정: M4는 실제 로컬 폴더 + 인증된 multipart/content API로 완성한다. S3 presigned/실제 AWS/U0는 후속 별도 gate이며 이 문서의 PASS로 승격하지 않는다.

제품 코드·타 역할 테스트·runtime/DB/browser fixture는 QA가 수정하거나 임의 기동하지 않는다. 아래의 “PASS”는 구현 완료 후 독립 증거를 확보했을 때만 기록할 수 있다. 구현자 자체 테스트, 커밋 제목, 상태 문구만으로 PASS를 부여하지 않는다.

## 승인 계약과 QA 경계

### 공유 HTTP 계약

- 모든 JSON 경로는 `/api/v1`과 기존 `success/data/error/timestamp` envelope를 사용한다. 목록은 `items,page,size,totalElements,totalPages,hasNext,hasPrevious`를 가진다.
- `POST /posts`는 `blogId,title,contentJson,contentHtml,categoryId,visibility,publish,thumbnailUrl,tagNames,images` 전체 snapshot을 받는다. `PUT /posts/{id}`는 `blogId` 없이 같은 필드를 받으며 생략된 nullable은 `null`, 배열은 빈 배열로 해석한다.
- `PostImageInput`은 `imageUrl,altText,displayOrder`이고, `PUT /posts/{id}/images`는 `{images:[...]}`이다. 본문 JSON의 관리 이미지 URL 집합과 snapshot을 일치시킨다.
- `PostDetail`은 `id,blogId,blogSlug,blogTitle,author,category,title,contentJson,contentHtml,thumbnailUrl,visibility,viewCount,publishedAt,createdAt,updatedAt,tags,images,previous,next`; 목록 summary는 본문 전체 대신 `excerpt`를 제공한다.
- 목록 경로는 `/blogs/{blogId}/posts`, `/blogs/slug/{urlSlug}/posts`, `/tags/{tagName}/posts`, `/posts/drafts`를 모두 유지한다. 상세는 `/posts/{id}`이다.
- 로컬 업로드는 `POST /uploads` multipart(`file`,`purpose`)이며 purpose는 `PROFILE_IMAGE|POST_THUMBNAIL|POST_IMAGE`다. 성공 data는 `id,imageUrl,contentType,size,purpose`; canonical URL은 `/api/v1/uploads/{uuid}/content` 상대 경로다. 정상 binary GET만 JSON envelope의 예외다.
- 이미지 GET은 `GET /uploads/{uuid}/content`에서 매번 DB 연결·접근권한을 평가하고 `no-store`, `nosniff`, 실제 MIME을 반환한다. external URL fetch, Blob URL 저장, 파일 경로 응답은 금지한다.

### 권한 fixture

M5 유니버스 CRUD는 M4 범위가 아니므로 `qa/m4-api-smoke.ps1`가 관계를 생성하지 않는다. root가 실제 DB에 준비한 fixture를 credential 인자로 주입한다.

| 별칭 | 관계/상태 | PUBLIC | UNIVERSE(친구) | PRIVATE/draft | 이미지 접근 |
| --- | --- | --- | --- | --- | --- |
| `owner` | 글·블로그 소유자, ACTIVE | 200 | 200 | 200 | 현재 연결된 글/프로필은 200 |
| `forward` | `forward → owner` ACCEPTED | 200 | 200 | 403 `POST_002` | UNIVERSE 글의 연결 이미지만 200 |
| `reverse` | `owner → reverse`만 ACCEPTED, 반대 방향 | 200 | 403 `POST_002` | 403 `POST_002` | 404 `UPLOAD_003` |
| `unrelated` | owner와 관계 없음 | 200 | 403 `POST_002` | 403 `POST_002` | 404 `UPLOAD_003` |
| 익명 | Bearer 없음 | 200 | 401 `AUTH_004` | 401 `AUTH_004` | 공개 글의 현재 연결만 200, 그 외 404 `UPLOAD_003` |

잘못되거나 만료된 Bearer는 익명으로 강등하지 않는다. malformed Bearer는 공개 글에서도 401 `AUTH_004`여야 한다. 삭제된 post/blog/owner는 글과 이미지 모두 404 및 비노출이어야 한다.

## 요구사항 → 인수 매핑

| ID | 정본 계약 | 독립 인수 증거 | 담당/상태 |
| --- | --- | --- | --- |
| M4-HTTP-01 | FR-POST-01~08, FR-BLOG-02, §20.1, worklog 공유 HTTP | 새 JAR의 모든 경로를 실제 HTTP로 호출하고 camelCase DTO, envelope/timestamp, 성공·오류 상태를 대조. `qa/m4-api-smoke.ps1`가 create/get/list/update/delete/images/drafts/tag 경계를 수행 | QA/root — 미실행 |
| M4-POST-01 | FR-POST-01/02, §20.1 | owner가 `categoryId=null` draft/public을 만들고 DEFAULT 연결, publish 전환 시 `publishedAt` 설정, 재발행 시 유지, public→draft 시 null을 GET으로 확인 | backend + QA — 미실행 |
| M4-POST-02 | FR-POST-01~03, ADR-0005 Q3 | owner만 create/update/delete. 타인·익명 write는 403/401. active 동일-blog category만 허용하고 `blog → user → post` lock 및 deleted owner/blog/category 경계를 DB/HTTP로 확인 | backend + QA — 미실행 |
| M4-POST-03 | FR-BLOG-02, FR-POST-04/05/06/08, §20.1 | 익명·owner·forward·reverse·unrelated fixture로 PUBLIC/UNIVERSE/PRIVATE/draft 상세와 두 blog-list 경로·tag-list·draft-list를 대조. publish=false 비소유 노출 금지 | backend + QA — 미실행 |
| M4-POST-04 | FR-POST-04/05, §20.1 | `page=0,size=20,max=100`, latest/popular, category/tag/visibility/publish filter, excerpt, previous/next가 접근 가능한 같은 blog 발행 글만 가리키는지 확인 | backend + QA — 미실행 |
| M4-POST-05 | FR-POST-05, §20.2, RISK-0011 | 동일 authenticated key 및 동일 anonymous IP+UA의 2회 상세가 24h 안에 한 번만 증가하는지 확인. `23:59:59`, `24h`, 동시 10회→1회, 48h cleanup, HMAC 원문 미저장·재시작은 실제 MySQL/Clock evidence 없이는 미검증 | backend/root + QA — 부분 미실행 |
| M4-POST-06 | FR-POST-03, NFR-06/08 | delete는 soft delete, 목록/count/detail/previous/next에서 즉시 제외, 삭제 후 같은 id 404. 삭제된 category/blog/owner와 기존 V1/V2 데이터 보존도 확인 | backend + QA — 미실행 |
| M4-TAG-01 | FR-POST-01/02/08, NFR-08 | tag trim/lowercase/중복 제거·최대 10개/각 100자, 동시 동일 tag 하나, normalized tag GET과 rollback-only 재사용 여부를 실제 MySQL로 확인 | backend + QA — 미실행 |
| M4-CONTENT-01 | NFR-02, §4.6, §20.1/20.5 | JSON을 원본으로 round-trip하고 client `contentHtml`과 불일치해도 JSON 기준 HTML 생성. `<script>`, event, style, javascript/data URL, iframe 제거. GET에서 `<script`, `onerror`, `javascript:` 잔존 여부를 확인 | backend/FE + QA — 미실행 |
| M4-CONTENT-02 | NFR-02, §20.1 | JSON/HTML UTF-8 각 1MiB, depth 32, node 10000, 허용 TipTap/표 attrs만 검증. draft 빈 doc 허용, publish는 trim 제목 1~200 및 텍스트/검증 이미지 필요. 실패 시 원 입력 보존 | backend + QA — 미실행 |
| M4-IMG-01 | FR-UPLOAD-01~04(§9.7 override), §20.2/20.3 | Bearer multipart upload와 binary content GET을 실제 HTTP로 호출. no token/malformed token, purpose, canonical relative URL, response metadata, 정상 4 MIME을 확인. S3/presigned 호출이 섞이지 않는지 확인 | backend + QA — 미실행 |
| M4-IMG-02 | FR-UPLOAD-01, §20.3, UPLOAD_001/002 | 실제 바이트 JPEG/PNG/WebP/GIF 1..5,242,880 bytes 허용, 빈/5MiB+1/선언 MIME 불일치/임의 파일 거부. 선언 header만 믿지 않고 bounded read+Tika magic을 사용 | backend + QA — 미실행 |
| M4-IMG-03 | §20.3, NFR-08 | UUID/CREATE_NEW/containment/symlink 차단, 기존 파일 overwrite 금지, 외부 URL fetch 금지, 파일 경로가 응답·로그에 노출되지 않음. `.local-data/uploads`가 Git 제외되고 재시작 후 metadata+bytes가 유지 | backend/root + QA — 미실행 |
| M4-IMG-04 | §20.2/20.3/20.5, VALIDATION_001/UPLOAD_004 | owner/purpose/최초 binding을 한 transaction에서 검증. POST_IMAGE↔thumbnail purpose 혼용, external URL, 다른 post 재바인딩, JSON/images 불일치는 400 `UPLOAD_004`; 서로 다른 URL의 중복·비연속 `displayOrder`/형식 오류는 400 `VALIDATION_001`, 동일 URL 중복은 dedupe 후 0..N-1 재번호로 분리한다. detached 이력은 유지 | backend + QA — 미실행 |
| M4-IMG-05 | §20.3, FR-UPLOAD-02~04 | 미연결 upload는 owner만, 본문/thumbnail은 글 predicate, profile은 현재 active profile 연결일 때만 읽기. 공개→draft/PRIVATE/삭제/탈착 후 이전 주소의 제3자 GET을 막고 owner/허용 viewer만 유지 | backend + QA — 미실행 |
| M4-IMG-06 | §20.5 Review Focus 1/5 | upload 성공 뒤 post/DB 실패 시 이번 요청 임시 파일만 정리하고 기존·성공한 미연결 파일은 보존. 로그아웃/계정 변경 중 늦은 응답의 새 세션 노출을 차단 | backend/FE + QA — 미실행 |
| M4-FE-01 | §20.3/20.4, Task4 | FormData에 JSON Content-Type 강제 금지, 401 single-flight/세션 generation 유지, canonical origin 외 Bearer 금지, `ManagedImage` Blob URL revoke/unmount/logout/account switch 확인 | frontend + QA — 미실행 |
| M4-FE-02 | PRD §7.2 write/edit, Task5 | TipTap toolbar JSON round-trip, Markdown paste, draft↔publish, duplicate click, upload-in-flight publish block, 실패 입력 보존, category/tag/image/thumbnail 연결을 실제 1440px browser에서 확인 | frontend/root + QA — 미실행 |
| M4-FE-03 | PRD §7.2 blog/detail/settings, Task6 | 두 목록 경로와 filters/sort/page, detail share/owner edit-delete/previous-next, 실제 image failure, PRIVATE→PUBLIC/account switch cache invalidation. M6 댓글/좋아요 가짜 숫자·버튼 없음 | frontend/root + QA — 미실행 |
| M4-DB-01 | NFR-06~08, §20.2 | V3 forward migration이 V1/V2를 수정하지 않고 `post_images` active order unique, `post_view_records(post_id,viewer_key)` unique, `image_uploads` metadata/tombstone을 만들며 기존 행·Auditing·soft delete를 보존 | backend/root + QA — 미실행 |
| M4-OPENAPI-01 | NFR-03~05, PRD §12 | `/v3/api-docs`/Swagger에서 JSON·multipart·binary operation, bearer, 400/401/403/404/409와 UPLOAD/POST/AUTH 코드가 실제 응답과 일치. 정적 annotation만으로 PASS 금지 | backend/root + QA — 미실행 |
| M4-BUILD-01 | NFR-09, PRD §11~12 | backend full test/bootJar, frontend full test/lint/build에서 failure/error/skip 0. placeholder/skip/stub 없음. 구현자와 다른 QA/root 패스가 명령·exit code·건수를 직접 확인 | backend/frontend/root — 미실행 |
| M4-DESIGN-01 | PRD §6/§7, 디자인 §8.2~8.4 | 1440px browser에서 paper/ink/accent/hard shadow, radius 0, 1060px editor/980px detail, `친구` 라벨, thumbnail은 발행 설정 카드, 별도 hero 없음 | frontend/root + QA — 미실행 |
| M4-DOC-01 | Task7, §20.4/20.8 | README에 `.local-data` 경로·백업·재시작·기동/종료·환경변수와 S3 후속 미완료를 기록. 기존 U0/사용자 파일/DB를 삭제하지 않음. 로컬 M4 완료와 S3/U0 미완료를 분리 기록 | root/QA — 미실행 |

## `m4-api-smoke.ps1` 실행 범위

### 실행 입력

스크립트는 기존 계정/관계 fixture를 바꾸지 않도록 계정을 새로 등록하지 않는다. root가 준비한 owner·forward·reverse·unrelated credential을 메모리로 signin한다.

```powershell
.\qa\m4-api-smoke.ps1 `
  -BaseUri http://127.0.0.1:8080 `
  -OwnerEmail $ownerEmail -OwnerPassword $ownerPassword `
  -ForwardViewerEmail $forwardEmail -ForwardViewerPassword $forwardPassword `
  -ReverseViewerEmail $reverseEmail -ReverseViewerPassword $reversePassword `
  -UnrelatedViewerEmail $unrelatedEmail -UnrelatedViewerPassword $unrelatedPassword
```

토큰·비밀번호·raw binary·파일 경로는 출력하지 않는다. fixture 생성·M5 관계 mutation·DB SQL·runtime 기동/종료·기존 데이터 삭제는 수행하지 않는다. 스크립트가 만드는 post/upload는 고유 제목 prefix를 사용하며 물리 cleanup하지 않는다. 단, 승인된 soft-delete negative gate를 위해 스스로 만든 private post 1건은 `DELETE`하고 후속 글/image 404를 확인한다. 기존 post/계정/파일/fixture는 삭제하지 않는다.

### 스크립트가 실제로 검사하는 것

1. 여섯 세션 signin/auth-me와 owner의 active blog/default category를 확인한다.
2. owner가 PUBLIC/UNIVERSE/PRIVATE/draft와 publish↔draft transition post를 만들고, XSS client HTML, canonical body/thumbnail image, tag normalization을 검증한다.
3. 익명·owner·forward·reverse·unrelated 상세/list/tag/draft 접근을 위 fixture 표와 비교한다. malformed Bearer가 공개 글에서 익명으로 강등되지 않는지 확인한다.
4. 실제 PNG bytes multipart의 정상, 빈/임의 MIME, 정확 5MiB, 5MiB+1, wrong-purpose/external/rebind/json-image mismatch를 확인한다.
5. `GET /uploads/{uuid}/content`의 public/UNIVERSE/private/draft/detached/delete 권한과 `Content-Type`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 확인한다.
6. post image snapshot 동기화, soft delete, detail view 중복 증가(동일 key 2회 1회)를 확인한다.

### 스크립트로 대체하지 않는 필수 증거

- 23:59:59/24h 경계, HMAC namespace/secret rotation, 동시 10회 조회와 48h bounded cleanup
- V1→V3 existing-row migration, DB lock/unique 경쟁, category 삭제↔post 저장, 동일 tag 동시 생성
- 파일/DB 부분 실패의 임시 파일 정리와 재시작 후 실제 bytes/metadata 영속성
- symlink/containment를 실제 Windows 파일 시스템에서 확인하는 테스트
- FE `ManagedImage` 늦은 응답·Blob revoke·계정 전환, TipTap/toolbar round-trip, 1440px 디자인, OpenAPI, 전체 build/lint

위 항목은 **미실행/미검증으로 남겨야 하며** API smoke exit 0을 M4 전체 PASS로 해석하지 않는다. 각 항목에는 구현 역할의 테스트 출력, root의 실제 MySQL/browser/restart 출력, QA의 독립 대조가 모두 필요하다.

## 현재 gate

- [ ] backend Task1~3 실제 diff·테스트·migration 제공
- [ ] frontend Task4~6 실제 diff·테스트/lint/build 제공
- [ ] `qa/m4-api-smoke.ps1` fresh local HTTP 실행 및 raw output 보관
- [ ] owner/forward/reverse/unrelated/anonymous 권한 매트릭스와 image content 매트릭스 PASS
- [ ] XSS/JSON-image equality/5MiB/MIME/purpose/rebind/detach/delete/restart PASS
- [ ] 24h/동시성/HMAC/DB migration/lock 증거 PASS
- [ ] 1440px browser·ManagedImage·editor/list/detail·README/OpenAPI PASS
- [ ] 모든 실패/미실행/잔여 S3/U0 위험이 기록되고 root가 독립 최종 판정

현재 이 문서는 인수 기준과 검증 도구 준비 기록이다. 제품 구현 완료·테스트 실행·M4 PASS를 주장하지 않는다.

## Task4 프론트 API·인증 이미지 조기 독립 코드리뷰 — 2026-10-03 16:22 KST

### 스냅샷과 범위

- 기준 checkout은 `feature/M4-posts`, HEAD `97567d9`이며 backend/frontend 구현자가 계속 수정 중이다. 이번 절은 `frontend/src/lib/apiClient.ts`, `features/post/{types.ts,postApi.ts,postApi.test.ts}`, `features/upload/**`, `src/test/apiClient.test.ts`, `Avatar.tsx`, `PostCard.tsx`, `Prose.tsx`와 실제 이미지·auth identity 소비처를 읽기 전용으로 대조했다. Task5 editor 전체 UI/디자인, backend 재리뷰, 제품 test/runtime/browser는 범위와 권한 밖이라 실행하지 않았다.
- 읽기 시점의 주요 UTC mtime은 `apiClient.ts 06:27:41.770`, `postApi.ts 07:02:19.492`, `postApi.test.ts 07:02:22.126`, `uploadApi.ts 07:12:57.229`, `uploadApi.test.ts 06:25:38.652`, `ManagedImage.tsx 06:58:28.175`, `ManagedImage.test.tsx 06:31:01.760`, `Prose.tsx 07:12:28.490`, `PostCard.tsx 06:42:04.743`, `apiClient.test.ts 06:28:35.093`이다. `uploadApi.ts`/`Prose.tsx`는 리뷰 중에도 갱신된 파일이므로 이 시각 이후 변경은 반영하지 않는다.
- 근거는 worklog Task4, 회의 §20.3·§20.5, REQUIREMENTS의 인증/업로드·canonical image 규칙이다. §20.3은 인증 이미지를 Bearer fetch→Blob으로 표시하고 Blob URL을 서버에 보내지 않으며, canonical URL만 저장하고 외부 profile URL 호환은 읽기만 보존하도록 한다. 이 절은 조기 정적 리뷰이며 최종 acceptance/PASS가 아니다.

### 정적으로 확인된 계약

- `apiClient.ts:106-136`은 FormData/Blob을 JSON 직렬화하지 않고 `Content-Type`을 직접 붙이지 않으며, 보호 요청에만 메모리 Bearer와 `credentials: include`를 적용한다. `:161-223`은 refresh single-flight, token generation, 1회 재시도와 만료 callback을 유지하고, `:229-236`은 binary 오류 envelope를 공통 오류로 처리한다. `apiClient.test.ts`에는 정상 JSON, multipart header, 일반·binary 401, concurrent single-flight, 늦은 401, refresh 실패 회귀가 의도 테스트로 존재한다.
- `uploadApi.ts:16-31`은 클라이언트 1 byte~5MiB·4 MIME과 multipart `file`/`purpose`를 검사한다. `:34-53`의 최신 resolver는 API origin·query/hash/userinfo를 확인하고 canonical `/api/v1/uploads/{id}/content`만 managed fetch 대상으로 분기한다. 기존에 지적된 단순 prefix 수용 문제는 최신 source에서 해소됐다(단, id 형식 자체는 UUID로 제한하지 않아 서버/HTTP에서 최종 검증해야 한다).
- `ManagedImage.tsx:28-59`는 `src`·`userId` 세대, 취소 플래그, 늦은 response 무효화와 `URL.revokeObjectURL` cleanup을 사용하고, `:61-66`은 `src`·`userId`가 같은 Blob만 렌더한다. 따라서 이전에 확인된 계정 전환 직후 stale Blob 노출 문제는 최신 source에서 정적으로 보완됐다. `:67-78`과 전체 `rg` 결과상 실제 `<img>`는 ManagedImage 한 곳이며, Prose body(`Prose.tsx:117-126`), PostCard thumbnail(`PostCard.tsx:45-51`), 설정 프로필(`SettingsProfilePage.tsx:389-398`), 상세 author(`PostDetailPage.tsx:137`), Hero(`Hero.tsx:160`), PostEditor preview/node view가 공통 경로를 사용한다. Avatar는 emoji fallback 전용(`Avatar.tsx:4-21`)이다.

### Important 발견

| ID/판정 | 근거 | 영향 | 수정 방향 / 재판정 조건 |
| --- | --- | --- | --- |
| **M4-FE-EARLY-CANON-01 — Important / 수정 필요·실행 미검증** | `uploadApi.ts:24-31`은 응답이 null인지만 확인하고 `response.imageUrl`을 그대로 반환한다. `PostEditor.tsx:186-190,206-207`과 `SettingsProfilePage.tsx:222-228`은 이를 그대로 본문/thumbnail/profile form에 넣는다. 더구나 설정의 `profileImageUrl` 자유 입력(`SettingsProfilePage.tsx:479-485`)은 저장 시 `updateProfile` payload(`:310-316`)로 임의 문자열을 보낼 수 있어, 기존 외부 URL 읽기 호환과 신규 managed canonical 쓰기를 정적으로 구분하지 않는다. `uploadApi.test.ts:30-49`도 정상 canonical fixture만 확인하고 malformed/external/blob/data/query/비정상 path 응답을 거부하는 경계가 없다. | §20.3의 “canonical만 저장, Blob URL은 서버로 보내지 않음, 외부 profile URL은 읽기 호환만”을 FE 경계에서 보장할 수 없다. 서버가 잘못된 응답을 주거나 사용자가 자유 입력을 바꾸면 외부·`blob:`·`data:`·비정상 canonical이 Post/thumbnail/profile 쓰기 payload에 들어갈 수 있다. 이는 현재 소스의 계약 공백이며 실제 서버가 거부하는지는 미실측이다. | 업로드 API 반환 직전에 API origin의 정확한 `/api/v1/uploads/{UUID}/content`(query/hash/userinfo 없음)만 허용하는 canonical validator를 적용하고, malformed response는 저장 전에 실패시킨다. 설정 URL 입력은 제거/읽기 전용 또는 “변경은 managed upload만”으로 제한하고, 기존 legacy external 값은 변경하지 않은 재전송만 별도 허용한다. canonical·external/blob/data·query 응답과 자유 입력 회귀 테스트를 추가한 뒤 FE test/lint/build 및 실제 profile/post HTTP로 재판정한다. |

### 제외·미실행

- **Critical 발견 없음.** ManagedImage의 `userId/src` identity guard, 늦은 Blob 차단, revoke, external URL 무Bearer, FormData header, 401 single-flight는 위 조기 snapshot에서 정적으로 확인했으며 별도 중복 결함으로 올리지 않았다.
- `npm` test/lint/build, backend test, JAR/API smoke, DB/browser, logout/restart/실제 binary 권한은 실행하지 않았다. 따라서 위 Important는 정적 계약 FAIL/실행 미검증이며 M4 전체 PASS가 아니다.

## Task1/2 조기 독립 코드리뷰 — 2026-10-03 15:52 KST

### 스냅샷과 경계

- 기준 checkout은 `feature/M4-posts`, HEAD `97567d9cae51869b1ad1f896bce3dc5be4f2c42c`였다. backend 생성 파일은 아직 Git 미추적(`??`)이며 구현자가 계속 수정 중이다. 초기 읽기 시각(UTC 파일시각)은 `PostService.java 06:41:41`, `PostController.java 06:38:39`, `PostContentService.java 06:34:55`, `PostRepository.java 06:34:06`, `V3__posts_local_uploads.sql 06:33:28`, `PostServiceIntegrationTest.java 06:41:44`, `PostControllerTest.java 06:45:36`이다. 리뷰 중 `ErrorCode.java`와 `PostContentService.java`가 갱신되어 후속 재독 결과를 아래에 반영했다.
- 최신 재독(2026-10-03 16:05 KST) 시각의 주요 UTC 파일시각은 `PostService.java 07:03:37.960`, `PostContentService.java 06:56:13.475`, `PostController.java 06:58:41.504`, `PostControllerTest.java 06:58:45.157`, `PostServiceIntegrationTest.java 06:58:57.902`, 새 `PostConcurrencyTest.java 07:00:04.421`, `PostMigrationTest.java 06:45:12.474`, `V3__posts_local_uploads.sql 06:33:28.184`이다. 이 최신 source/test가 이전 표의 초기 line reference를 갱신하는 기준이다.
- 읽은 범위는 `domain/post/**`, `domain/tag/**`, `domain/universe/**`, V3 migration 및 대응 post/migration test다. runtime·DB·browser를 기동하지 않았고 제품 test/Gradle/API smoke도 실행하지 않았다. 이 절은 조기 발견이며 최종 PASS가 아니다.
- 정적상 create/update/delete/image-only PUT은 `blog → user → post` lock을 시도하고, Universe predicate는 `viewer → owner ACCEPTED` 방향을 사용한다. 이는 소스 구조 확인일 뿐 동시성·실측 통과 증거가 아니다.

### Critical/Important 발견

| ID/판정 | 근거 | 영향 | 수정 방향 / 재판정 조건 |
| --- | --- | --- | --- |
| **M4-EARLY-ACL-01 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:190-191`은 `canRead` 실패 시 `viewerId == null ? AUTH_004 : POST_002`로 분기하고, 현재 `PostControllerTest.java:67-73`도 익명 private을 `AUTH_004`로 기대한다. | 정본 §20.1의 익명/인증 비허용 상세 오류 분리와 해당 controller 기대값은 source/test에 반영됐다. 익명 UNIVERSE/PRIVATE/draft 실제 HTTP와 실행 결과는 아직 없다. | fresh test/HTTP에서 UNIVERSE·PRIVATE·draft 및 malformed Bearer를 재판정한다. |
| **M4-EARLY-ACL-02 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:197-214,264-284`가 blog owner id를 명시 전달하고 익명 `publish=false`를 `AUTH_004`, 인증 비소유자를 `POST_002`로 먼저 거부한다. tag 경로는 viewer 자신의 draft만 `matchesPublish`로 남긴다. | §20.1 draft filter 계약은 source에 반영됐다. tag owner semantics와 빈/혼합 fixture는 아직 HTTP로 검증하지 않았다. | owner·타인 draft가 섞인 tag/blog list 및 익명 filter를 fresh HTTP로 재판정한다. |
| **M4-EARLY-IMG-01 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:390-420`이 음수·중복·비연속 `displayOrder`를 `VALIDATION_001`로 거부하고 정렬한다. smoke `qa/m4-api-smoke.ps1:642-646`의 서로 다른 URL 동일 order 기대와 정렬된다. | 입력 order 계약은 source에 반영됐으나 HTTP/DB unique·soft-delete 이력은 미실행이다. | 동일 order/음수/비연속, 순서 교환, 삭제 후 재사용을 fresh HTTP/MySQL로 확인한다. URL 중복은 아래 별도 residual이다. |
| **M4-EARLY-VIEW-01 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:304-330`가 48h cutoff와 batch 500 `DELETE ... ORDER BY ... LIMIT`을 요청 transaction에서 수행한다. | bounded cleanup 경로는 source에 생겼지만 48h 경계·동시성·MySQL 실행 결과가 없다. | 48h 초과/미초과 row, batch 한도 및 동시 조회를 실제 MySQL로 재판정한다. |
| **M4-EARLY-NAV-01 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:455-480`는 blog 전체 후보를 읽은 뒤 published/access predicate를 적용하고 정렬해 첫 접근 가능 글을 선택한다. 초기 PageRequest(0,20) 누락은 source에서 제거됐다. | 20개 inaccessible 후보 뒤 accessible 글 누락 위험은 정적으로 해소됐지만 large fixture/권한 방향은 미실행이다. | 접근 불가 글 20개 이상 fixture의 owner/forward/reverse/unrelated previous/next를 HTTP로 확인한다. |
| **M4-EARLY-VIEW-02 — 정적 수정 확인 / 실행 미검증** | 최신 `PostService.java:101-119`는 fallback 없는 필수 property와 UTF-8 32-byte fail-fast를 사용한다. | fixed default 위험은 source에서 해소됐지만 test profile 주입·runtime 누락 시 startup 결과는 미실행이다. | random runtime secret 및 test secret 주입, 누락/짧은 값 startup 실패를 확인한다. |
| **M4-EARLY-TEST-01 — Important / 부분 보완·실행 미검증** | 최신 `src/test/java/com/zeroverse/domain/post/PostConcurrencyTest.java`가 concurrent 10→1, `23:59:59`/`24h`, 48h batch cleanup 및 adjacent 접근 fixture를 포함한다. 그러나 `src/test/java/com/zeroverse/migration/PostMigrationTest.java:19-31`은 table/index 존재만 확인하고 V1→V3 기존 row·ledger unique·active order 보존을 검증하지 않는다. backend가 보고한 `PostServiceIntegrationTest` 실패는 QA가 재실행하지 않았다. | 요구된 테스트 소스는 일부 생겼지만 실제 MySQL 실행·lock/unique 결과·migration 보존 증거가 없어 독립 gate를 통과했다고 볼 수 없다. | root/backend의 fresh test 명령·exit·failure/error/skip 및 기존 row 보존 SQL evidence를 직접 대조한다. QA는 제품 test를 추가하지 않는다. |
| **M4-EARLY-IMG-02 — Important / FAIL** | 최신 `PostService.java:405-408`은 `!seenUrls.add(image.imageUrl())`를 `VALIDATION_001`로 거부한다. | §20.1 회의 계약은 images에서 URL 중복을 제거한 뒤 연속 order를 사용한다고 명시한다. 현재 unique URL duplicate input은 dedupe되지 않고 실패하므로 승인된 입력 정규화 계약과 어긋난다. | URL 중복은 계약대로 dedupe한 뒤 최종 order를 재번호/검증하고, 서로 다른 URL의 duplicate/invalid order는 `VALIDATION_001`로 유지한다. smoke `qa/m4-api-smoke.ps1:648-657`에 duplicate URL 201·1개·order 0 기대를 추가했으며 HTTP 실행으로 확정한다. |

### 이미 backend에 전달된 ContentService 발견 — 수정 상태 추적

초기 조기 초안에서 전달된 다섯 항목은 backend가 리뷰 중 `PostContentService.java`를 계속 갱신한 뒤 소스상 반영됐다(최신 후속 파일시각 UTC `06:56:13`). `PostContentServiceTest.java:55-85`와 source readback으로 정적 반영만 확인했으며 제품 test/HTTP는 실행하지 않았다.

- `PostContentService.java:59-90`: root `type=doc`와 client `untrustedHtml` UTF-8 1MiB를 검사한다.
- `PostContentService.java:144-166,199-215`: node/mark attrs allowlist를 검사한다.
- `PostContentService.java:217-224,257-294`: `colspan`/`rowspan`을 검증하고 `th`/`td` render에 반영한다.
- `PostContentService.java:226-234`: canonical이 아닌 image URL을 `UPLOAD_004`로 매핑한다.

판정은 **정적 수정 확인·실행 미검증**이다. Task2/Task3 구현 완료 후 해당 test, 통합 저장/복원, 실제 HTTP에서 다시 판정한다.

### 계약 정합성 보류와 제외

- root 조율로 POST 생성 계약은 201(`POST /posts`, `POST /uploads`), PUT/DELETE는 200으로 확정했고 smoke의 `New-Post` 기본 기대값 `qa/m4-api-smoke.ps1:471-488`는 201을 유지한다. 최신 `PostController.java:51-63`의 실제 response는 201으로 수정됐지만 OpenAPI annotation `responseCode = "200"`가 남아 있으므로 backend가 문서를 201로 맞출 때까지 HTTP/OpenAPI gate는 미검증이다.
- `PostService.java:423-426`의 JSON/image 집합 불일치는 현재 `VALIDATION_001`이다. Task3 upload metadata/binding 구현 전에는 최종 `UPLOAD_004` 여부를 단정하지 않는다. 반대로 displayOrder 중복은 위 M4-EARLY-IMG-01처럼 `VALIDATION_001`로 분리한다.
- HMAC·24h ledger·migration·목록/접근 predicate는 runtime/DB 실측을 하지 않았고, image upload 구현(`domain/upload/**`)과 FE는 이번 조기 scope 밖이다. 삭제·fixture mutation·서버 기동·제품 파일 수정은 하지 않았다.

### 조기 판정

현재는 **조기 리뷰 FAIL/미검증 혼합 — M4 PASS 아님**이다. 원래 ACL·draft·order·cleanup·navigation·HMAC 7건은 최신 source에서 정적 반영됐고 동시성 테스트 파일도 추가됐지만, `M4-EARLY-IMG-02` URL dedupe 불일치와 POST 201/OpenAPI·migration 보존·실제 test/HTTP 실측 공백이 남아 있다. 구현자 수정 및 fresh snapshot, 이후 root가 제공할 MySQL/HTTP/FE evidence 없이는 최종 acceptance를 내리지 않는다.

## HTTP acceptance harness 첫 실행 — New-ImageBytes 순수 함수 수정 — 2026-10-03 16:34 KST

- root의 `build/m4-run-http-acceptance.ps1` 첫 실행은 합성 4계정·관계 2개·signin/default category까지 성공한 뒤 `New-ImageBytes`의 `[Buffer]::BlockCopy`에서 제품 HTTP 호출 전 중단됐다. 오류는 `Object must be an array of primitives`였고, root 서버/DB를 QA가 재기동·변경하지 않았다.
- QA가 동일 자료 흐름을 Windows PowerShell 5.1에서 최소 재현했다. `switch`가 branch의 `byte[]`를 함수 출력으로 열거해 `System.Object[]`(각 요소 `System.Byte`)를 만들었고, 이를 `BlockCopy`에 전달하면 primitive-array 예외가 발생했다. 대형 결과의 `return $bytes`도 파이프라인 열거를 유발할 수 있었다.
- QA 소유 `qa/m4-api-smoke.ps1:316-329`만 수정했다: 빈 배열도 단일 객체로 반환(`return ,([byte[]]::new(0))`), seed를 명시 `[byte[]]`로 재캐스팅(` [byte[]]$seed = switch ...`), 생성 byte 배열을 단일 객체로 반환(`return ,$bytes`). 제품 소스·runtime·DB·root harness는 변경하지 않았다.
- 스크립트 AST에서 `New-ImageBytes` 함수만 추출한 순수 검증 결과는 4 MIME×128 bytes와 0/정확 5MiB/5MiB+1의 7 cases, 모두 `System.Byte[]`, 기대 길이, `PURE_IMAGE_BAD=0`이었다. PowerShell parser `PS_PARSE_ERRORS=0`, QA smoke `git diff --check` exit 0이다. 5MiB+1 생성 측정은 45.132ms였으며 HTTP/API 호출은 없었다.
- 이 수정은 harness blocker 해소를 위한 순수 함수 검증일 뿐이다. root가 기존 합성 계정을 보존하고 새 fixture로 `127.0.0.1:18080` 단일 HTTP smoke를 재실행하기 전까지 API 결과·M4 PASS를 주장하지 않는다.

## HTTP acceptance harness 2회차 — 빈 byte[] 바인딩 수정 — 2026-10-03 16:36 KST

- root의 2회차는 4종 정상 업로드와 익명 거부까지 실제 HTTP로 진행했지만, 빈 파일 negative에서 `Upload-Image`의 `[Parameter(Mandatory)][byte[]]$Bytes`가 길이 0 배열을 거부해 제품의 `400/UPLOAD_002` 검증 전 중단됐다. `[AllowEmptyCollection()]`이 없는 mandatory byte[]의 동일 최소 재현을 Windows PowerShell 5.1에서 확인했다. `Invoke-Request.FileBytes`의 기존 `[AllowNull()][byte[]]`는 별도 binding 재현에서 빈 배열을 허용했다.
- QA 소유 `qa/m4-api-smoke.ps1:396`의 `Upload-Image.Bytes`에 `[AllowEmptyCollection()]`만 추가했다. 실제 함수 AST를 HTTP stub과 호출해 `UPLOAD_EMPTY_BINDING=PASS`를 확인했고, New-ImageBytes 7 cases는 `PURE_IMAGE_BAD=0`으로 재검증했다. 전체 parser `PS_PARSE_ERRORS=0`, smoke `git diff --check` exit 0이다.
- 이번 수정도 제품 runtime/DB/기존 합성 데이터에 영향을 주지 않는다. root가 `127.0.0.1:18080`에서 다음 단일 smoke를 실행해 빈 파일의 실제 `400/UPLOAD_002` 및 후속 전체 gate를 얻기 전까지 HTTP acceptance와 M4 PASS는 미검증이다.

## HTTP acceptance harness 4회차 — 모든 helper 빈 배열 binding 보정 — 2026-10-03 16:58 KST

- root 4회차는 제품 HTTP 이전에 이미지 없는 글의 `@()` 입력을 준비하다 `Images` empty-array parameter binding 오류로 중단됐다. `New-ContentJsonWithImages.ImageUrls`도 빈 배열에서 동일 binding 오류를 최소 재현했고, 이미지 없는 글/본문/스냅샷은 정본상 유효 입력이다.
- AST audit에서 QA smoke의 배열 parameter 8개를 전수 확인했다. `Invoke-Request.FileBytes`, `New-ContentJsonWithImages.ImageUrls`, `New-Post.TagNames/Images`, `Assert-ContainsId/Assert-ExcludesId.Items`, `Update-PostSnapshot.Images`에 `[AllowEmptyCollection()]`을 적용했다. `Upload-Image.Bytes`는 이전 회차에서 이미 보정됐다. 제품 코드·runtime/DB는 변경하지 않았다.
- PowerShell 5.1 pure 결과: `New-Post` body의 tag/image 배열 0·1·2, `New-ContentJsonWithImages` image node 0·1·2, `Update-PostSnapshot` images 0·1·2가 모두 통과했다. AST audit `ARRAY_PARAMETER_MISSING_ALLOW_EMPTY=0`, `PURE_EMPTY_ARRAY_CASES=PASS`다. parser/diff는 fresh check에서 확인하며, 이는 HTTP/API PASS가 아니다.
- root가 다음 단일 smoke에서 이미지 없는 글의 실제 API 경계와 이전 dedupe `201/1개/order0`, raw `7,12 → 400`을 재측정해야 한다. 현재 M4 PASS는 주장하지 않는다.

## HTTP acceptance harness 3회차 — duplicate URL fixture order 분리 — 2026-10-03 16:52 KST

- root 3회차는 5MiB 정확/초과, 4 MIME, 서로 다른 URL의 중복 `displayOrder` `400/VALIDATION_001`까지 실제 HTTP로 통과했다. 이후 dedupe 전용 fixture가 동일 URL에 raw `displayOrder=7,12`를 보내 정본의 “입력 order는 중복 없는 연속 0..N-1” 조건 자체를 위반했고, backend의 정상적인 `400/VALIDATION_001`에서 중단됐다.
- QA 소유 `qa/m4-api-smoke.ps1:651-652`를 동일 URL의 유효 raw order `0,1`로 수정해 URL first-occurrence dedupe 후 최종 1개/order 0을 검사하도록 했다. 순서 검증을 약화하지 않기 위해 `:659-664`에 서로 다른 URL raw `7,12`의 별도 `400/VALIDATION_001` negative를 추가했다. 이는 backend dedupe 기대를 PASS로 만들기 위한 완화가 아니라 raw-order 검증과 URL dedupe 기능을 분리한 fixture 수정이다.
- PowerShell 5.1 pure 검증에서 `New-Post` request body image 배열은 count 2, index order `0,1`로 유지되고, `Get-PostImages`는 단일 JSON image도 `System.Object[]`, count 1, index 0/order 0을 유지하도록 `:533-535`를 단일 배열 반환으로 보정했다. 제품 HTTP/runtime/DB는 수정 후 아직 재실행하지 않았고, root가 다음 단일 smoke에서 실제 dedupe `201/1개/order0` 및 raw `7,12 → 400`을 확인해야 한다.

## 2026-10-03 17:49 KST — 중간 인수 증거 재대조 및 최종 게이트 보류

### 범위·판정

- 기준은 PRD §9.7과 회의 §20.8의 로컬 M4 실행 결정이다. 사용자는 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”**라고 명시했으므로 Chrome 확장의 로컬 파일 URL 권한을 확대하지 않는다. 실제 파일 선택·전송 자동화만 제외하고, multipart API·FE 자동 업로드 테스트·로컬 파일 저장·5MiB/권한·브라우저 이미지 표시·재시작 보존은 여전히 인수 조건이다.
- 아래 HTTP/Chrome/파일시스템 결과는 QA가 새로 실행한 결과가 아니라 root가 제공·관측한 중간 증거로 귀속한다. QA는 제품 코드·타 역할 테스트·서버/DB/browser를 이 절에서 기동·수정하지 않았다. 따라서 이 절은 M4 전체 PASS가 아니라 최종 재검증 전 상태 기록이다.

### 새로 확인된 증거

- `build/m4-frontend-final-results.json`을 직접 읽어 `success=true`, 89 test suites/311 tests, passed 89/311, failed 0, pending 0을 확인했다. root가 전달한 frontend WORKLOG의 최종 기록은 lint/build exit 0이다. FE의 R1(선택 위치 Markdown 삽입), R4(draft picker 전환), R5(DEFAULT 실제 선택 및 GENERAL 선행), R6(20개 초과 draft pagination), R7(ordered list/table attrs) 자동 회귀도 JSON report의 passed assertion으로 확인되지만, backend 최신 소스와의 통합·독립 재리뷰는 별도다.
- root가 `3890657091DDDD7927DC53CBB4E14891125F568F51739B69F32793053723B36A` JAR/API `127.0.0.1:18080`에서 6차 `build/m4-run-http-acceptance.ps1 → qa/m4-api-smoke.ps1`를 exit 0으로 관측했고 `M4 API smoke passed for the executed HTTP gates.`를 남겼다. 공개/친구/비공개/draft 권한, 4 MIME, 5MiB 경계, snapshot/order/dedupe, publish 전환과 삭제 후 이미지 차단은 **그 JAR의 실행 범위에서** 통과했으나 해당 JAR은 R1~R8 수정 전 snapshot이다. 최종 artifact로 재실행하기 전에는 최신 제품의 HTTP PASS로 승격하지 않는다.
- root가 API로 만든 PNG fixture `post23`을 비로그인 Chrome에서 `complete=true`, `naturalWidth=96`, `naturalHeight=64`로 표시되는 것을 관측했다. 원본·익명 HTTP 응답·`.local-data/uploads/ac0be62f-ebc1-4d50-b20b-a5865570a904` 파일 SHA-256은 모두 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`이며 응답은 `200/image/png/no-store`였다. 이는 브라우저 파일 선택 검증을 우회한 API 생성 이미지 표시·바이트 보존 증거이지, 파일 선택창 PASS가 아니다.
- root가 `LocalImageStoreSymlinkCheck`를 공식 `eclipse-temurin:21-jdk`의 network-none/read-only/tmpfs 검증 컨테이너에서 실행해 target/root/ancestor symlink 모두 `read=UPLOAD_003`, `write=COMMON_500`, 외부 marker `UNCHANGED`, `SYMLINK_CHECK=PASS`, `FIXTURE_CLEANED=true`, exit 0을 관측했다. 이 결과는 실제 파일 경계에 대한 root 증거이며, 재시작 후 metadata/bytes 보존까지 대체하지 않는다.
- root가 전달한 targeted backend 결과 중 `build/m4-security-time-green-20261003-1720.log`는 BUILD SUCCESSFUL(2m18s), `build/m4-post-v3-migration-20261003-1730.log`는 BUILD SUCCESSFUL(1m10s)이다. 후자는 target2→legacy active/deleted fixture→target3과 보존을 검증한 별도 `PostV3MigrationTest` 실행으로 기록됐지만, 최종 full suite와 최종 artifact의 fresh 재검증은 남았다.

### 실패·수정 중·잔여 게이트

- `build/m4-backend-full-test-20261003-1750.log`를 직접 읽은 결과 backend 전체는 `419 tests completed, 1 failed`로 BUILD FAILED했다. 실패는 `SecurityAccessControlTest`의 blanket allowlist 회귀에서 `path=/api/v1/posts/1` 기대 불일치다. 공개 상세가 승인된 M4 계약과 기존 fixture 기대가 충돌한 건으로, backend가 fixture/보호 write 경계를 수정 중이며 이 결과는 최종 PASS가 아니다.
- 독립 전체 리뷰의 R1~R8은 다음과 같이 추적한다: R1/R4/R5/R6/R7의 FE 자동 증거는 위 JSON에서 녹색이고 R1/R4는 root 실제 UI 관측도 녹색, R5는 fresh `/write`에서 실제 DEFAULT `미분류` selected로 root가 확인했다. 다만 최종 FE 소스 snapshot·backend 연동·독립 재리뷰 전에는 항목별 최종 승인으로 확정하지 않는다. R2의 `PostContentService.java`와 `PostContentServiceTest.java`가 전달됐지만 최종 JAR에서 malformed content/ordered-list attrs를 다시 확인하지 않았다. R3(SQL ACL/정렬·offset/limit·large page), R8(삭제된 blog/owner의 `POST_001/404`)은 backend 수정·targeted/full 실행을 기다린다.
- 최종 backend full test archive/JUnit XML, 수정 후 bootJar hash, 최신 JAR의 HTTP smoke(`-ValidateMalformedContent -ValidateListAttributes` 포함), HMAC/24h·동시성·migration·재시작 보존, final PNG URL/metadata/bytes 재확인, FE 최종 artifact와 독립 R1~R8 재리뷰가 모두 pending이다. 특히 현재 PNG fixture는 최종 재시작 전/후 동일 URL·SHA를 비교해야 한다.
- OpenAPI 실제 operation/response code, README의 로컬 데이터·재시작·S3 후속 기록, staged diff/commit/push는 QA가 이 절에서 승인하지 않는다. S3/LocalStack/U0와 M5 이후 기능은 PRD §9.7 결정대로 별도 후속 범위이며, 과거 U0 `BLOCKED`를 로컬 M4 PASS로 바꾸지 않는다.

### 현재 독립 QA 판정

**중간 증거 통과 + 최종 게이트 보류 / M4 PASS 아님.** 최신 FE 자동 리포트와 root의 이전 HTTP·PNG·symlink 증거는 해당 범위에서 유효하지만, backend full suite의 1 failure와 R1~R8 수정 후 최종 artifact/재리뷰가 닫히지 않았다. 다음 판정은 root가 최종 증거 묶음을 전달한 뒤에만 내린다.

## 2026-10-03 18:18 KST — 최종 로컬 M4 독립 QA 판정

### 최종 판정 범위

- PRD §9.7·회의 §20.8의 승인 범위인 **로컬 파일 저장 + 인증 multipart/content API + 글/이미지 접근권한 + 5MiB 제한**에 한정해 최종 판정한다.
- 사용자의 **“브라우저 파일 업로드만 제외하고 API·자동 테스트로 검증해”** 결정에 따라 파일 선택창·브라우저 파일 전송 자동화는 실행하지 않았다. multipart API, FE 자동 업로드 테스트, 실제 로컬 파일 저장·권한·이미지 표시·재시작 보존은 검증 범위에 포함했다.
- 아래 runtime/Chrome/HTTP 동작은 root가 실행한 독립 acceptance 결과이고, QA는 로그·archive·JSON·최종 JAR를 직접 재집계/대조했다. QA는 제품 코드·타 역할 테스트·runtime/DB/browser fixture·Git을 변경하지 않았다.

### 최종 증거

- **Backend 전체:** `build/m4-final-backend-full-20261003-1900.log`는 `BUILD SUCCESSFUL in 13m 49s`, exit 0이다. 보존 archive `build/m4-final-junit-20261003-1915`를 QA가 PowerShell XML 집계한 결과 `66 XML / 426 tests / 0 failures / 0 errors / 0 skipped`다. `PostConcurrencyTest` 3, `PostContentServiceTest` 10, `PostServiceIntegrationTest` 8, `UploadServiceTest` 8, `PostV3MigrationTest` 1, `SecurityAccessControlTest` 24를 포함한다.
- **Backend artifact:** `build/m4-final-bootjar-20261003-1920.log`는 `BUILD SUCCESSFUL in 18s`다. 최종 runtime JAR `build/m4-runtime-20261003-181423.jar` SHA-256은 `D2B5FBB8A659D707F1A4039FB062D1768113D9AD53946F7A1D6AAA983783E2EF`다. root의 preflight F11E JAR와 최종 JAR의 `BOOT-INF/classes` hash 비교에서 차이는 OpenAPI 201 annotation을 포함한 `UploadController.class` 하나이며, `LocalImageStore.class`는 동일하다.
- **Frontend:** `build/m4-frontend-final-results.json`은 `28 testResults / 311 tests / 311 passed / 0 failed / 0 pending / success=true`다. `build/m4-frontend-r7-results.json`은 `2 files / 40 tests / 40 passed / 0 failed / 0 pending / success=true`다. frontend WORKLOG의 최종 `npm.cmd run lint`와 `npm.cmd run build`도 exit 0이다. 직전 QA 기록의 `89 suites`는 Vitest nested suite 집계이며, 최종 보고 기준은 `28 files / 311 tests`로 정정한다.
- **HTTP/API:** root의 최종 수정 runtime에서 7차 smoke가 exit 0이고 `M4 API smoke passed for the executed HTTP gates.`를 반환했다. malformed leaf는 `400/VALIDATION_001`, ordered-list `start/type` 보존은 `True/True`, `page=2147483647&size=20`은 `items=0,total=1`로 large-page overflow가 재현되지 않았다. 공개·친구 방향성·PRIVATE/draft·MIME·1~5MiB·purpose/rebind/detach/delete/view dedup·snapshot/order·삭제 후 이미지 차단 gate를 포함한다.
- **최종 OpenAPI:** root가 최종 D2B5 runtime에서 posts `201`/400/401/403, uploads `201`, binary content GET operation 존재를 실제 `/v3/api-docs`로 확인해 exit 0으로 기록했다. 실제 HTTP 201과 문서 annotation이 일치하며, 이전 uploads 200 annotation 잔여 지적은 해소됐다.
- **재시작·이미지:** post23의 기존 canonical image URL은 최종 runtime 교체 후에도 HTTP `200`, `395 bytes`, `image/png`, `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`를 유지했다. 원본·`.local-data` 파일·HTTP 응답 SHA-256은 모두 `2F76F73080F7F5ACE1CA54507604FE80937718FB8A6E1757F9E71D125B243F67`다. Chrome 비로그인 이미지도 `complete=true`, `naturalWidth=96`, `naturalHeight=64`로 재확인됐다.
- **FE/브라우저 회귀:** root Chrome에서 R1 Markdown 삽입, R4 draft 전환, R5 실제 DEFAULT `미분류` 선택, R7 ordered-list/table 의미 보존을 확인했고, post33의 ordered-list `start=5/type=a` marker `e.`, computed `lower-alpha`, image natural 96×64 및 1440px viewport를 확인했다. 파일 선택/전송은 사용자 결정대로 제외했다.
- **파일 경계·문서:** root의 공식 `eclipse-temurin:21-jdk` read-only/network-none/tmpfs symlink proof는 target/root/ancestor symlink read `UPLOAD_003`, write `COMMON_500`, marker `UNCHANGED`, exit 0이었다. README에는 `.local-data/uploads`, DB와 파일 동시 백업·재시작, local/test profile, no static exposure, 5MB, S3 후속 범위가 기록되어 있다.
- **독립 리뷰:** `/root/m4_whole_review`가 최종 소스/회귀와 함께 FE R1/R2 방어/R4/R5/R6/R7 및 BE R2/R3/R7/R8을 종료 가능으로 판정했고 추가 Critical/Important 회귀가 없음을 root가 전달했다. R7 추가 FE 회귀 2 files/40 tests는 위 JSON으로 별도 확인했다.

### 최종 QA 판정

**로컬 M4 PASS.** 승인된 로컬 범위의 backend/FE 자동 테스트, API 권한·업로드·콘텐츠·페이징·OpenAPI, migration/동시성/HMAC/조회수, 로컬 파일 경계, 재시작 이미지 보존 및 1440px 브라우저 표시 증거가 모두 닫혔다. 구현자 결과와 root/runtime 및 독립 whole-review 컨텍스트를 분리해 확인했다.

### 비차단 잔여 범위

- S3 presigned/실제 AWS/LocalStack/SeaweedFS U0는 PRD §9.7 결정대로 후속이다. 기존 U0 `BLOCKED` 판정은 변경하지 않으며 로컬 M4 PASS를 AWS 정책 동등성으로 해석하지 않는다.
- 브라우저 파일 선택창·실제 파일 전송 자동화는 사용자 명시 결정으로 제외했고, API/FE 자동 업로드 및 이미지 표시로 대체했다.
- M5 이후 관계 CRUD·댓글·좋아요 및 운영 배포 hardening은 이번 판정 범위가 아니다.
- QA 판정은 제품 로컬 M4에 대한 것이며, 아직 실행하지 않은 commit/push/merge/release 절차의 완료를 의미하지 않는다.

## 2026-10-03 18:20 KST — R7 브라우저·자동 테스트 증거 귀속 정정

- 앞선 18:18 최종 절의 `R7 ordered-list/table 의미 보존` 표현은 다음과 같이 정정한다. **root Chrome 직접 증거는 ordered-list `start=5/type=a`의 marker `e.` 및 computed `lower-alpha`와 이미지 표시**다.
- `rowspan/colspan` 의미 보존은 Chrome 직접 증거로 주장하지 않는다. 해당 계약은 backend `PostContentServiceTest`와 FE R7 추가 회귀 JSON(`2 files/40 tests`, 전부 passed)의 자동 테스트 증거로 판정한다. Chrome에서는 일반 2×2 표 작성·발행 흐름만 확인했다.
- 이 정정은 R7 통과 판정, 로컬 M4 PASS, 테스트 결과나 범위를 변경하지 않는다. 파일 선택·전송 자동화 제외와 S3/U0/M5 후속 범위도 그대로다.
