# Planning Review: M7 피드·검색 계약

- 회의 ID: `M7-20261109-feed-search`
- 관련 마일스톤: M7
- 상태: `APPROVED` — 실행은 사용자 지시로 보류
- 생성: 2026-11-09 KST
- 진행자: Codex root
- 소집 사유: BE/FE·여러 도메인·접근제어, 신규 공개 API 세부 계약, 검색용 파생 컬럼/기존 데이터 이관 제안
- 관련 worklog: [M7](../../worklog/M7-feed-search.md)
- 아래 **A-v2**는 2026-11-09 사용자 전체 승인을 받았다. 단, **추가 지시까지 작업 착수를 보류**한다. 승인과 실행 재개를 구분하며 자동으로 구현하지 않는다.

## 1. 결정할 질문

게시글 중심 검색·서버 관계 필터·접근권한별 건수·정확한 본문 텍스트 검색을 갖춘 아래 A안과 M7 실행계획을 승인할 것인가?

## 2. 배경과 범위

- M4/M5/M6 완료, dev `bc99cba`에서 `feature/M7-feed-search` 생성. 기존 공개 피드/검색 security matcher와 QueryDSL 의존성은 있으나 실제 feed/search 서비스는 없다.
- 포함: 공개/유니버스 피드, 제목/작성자 필터, 통합 게시글 검색, MainPage/SearchPage/TopBar 연결, 피드 카드 집계·메인 관계 현황, 실제 MySQL/브라우저 인수.
- 제외: M8 알림 생성/센터/최근 알림/배지, S3, 관리자, 새로운 검색엔진/외부 라이브러리, BlogPage 목록 count 확장, 독립 사용자·블로그 검색 결과.
- 사용자 루트 미추적 package/package-lock/run scripts, .env.local, .local-data, 기존 DB를 보존한다. 검증은 격리 합성 DB이며 사용자 실행 DB에는 임의 적용하지 않는다.

## 3. 근거 문서

| 문서 | 근거 |
|---|---|
| REQUIREMENTS FR-FEED-01~02 | following/PUBLIC 피드·SUSPENDED 기존 글 포함·삭제 제외·제목/작성자 필터 |
| REQUIREMENTS FR-SEARCH-01 | 게시글 중심, BLOG/USER/TAG는 검색 범위, owner PRIVATE/draft 포함 |
| PRD §5.9/5.10, §7 MainPage/SearchPage, §9-G/N/S | 로그인 universe/guest public, 관계 3탭, 페이지네이션, 검색 5탭·단일 컬럼 |
| DESIGN-SYSTEM §8.1/8.8, Main Feed v2/Pages 원본 | 1440px·3열·검색1000px·타입 배지·건수 |
| PostAccessPolicy/PostRepository/UniverseRepository | 기존 읽기권한·soft delete·방향별 ACCEPTED/친구 정의 |
| PostContentService/Post entity/V1~V3 | TipTap JSON 원본·서버 HTML cache, 기존 본문텍스트 컬럼 없음 |
| M6 회의 §6/PRD §9.9 | 살아 있는 댓글 및 사용자 기준 count·기존 like 보존·읽기권한 |

## 4. 선택지

| 안 | 방법 | 이점 | 비용·위험 | 복귀 |
|---|---|---|---|---|
| A (권고) | 검색용 plain text 파생 컬럼을 forward migration으로 채우고 QueryDSL DB 필터·count·paging | 태그/속성/엔티티 오탐과 inline 경계 누락 방지; 페이지 정확성 | schema·기존행 backfill·쓰기 동기화·업그레이드 검증 | 컬럼/원문 보존, M7 읽기 중단·호환 앱 복귀; 재도입 전 캐시 재구축 검증 |
| B | schema 유지, HTML 태그 제거 SQL과 HTML-escaped 검색어 LIKE | 작은 diff·migration 없음 | `amp`가 `&amp;`에 오탐, `sp<strong>ring</strong>`의 spring 누락; 단순 escaped LIKE로 요구사항 충족 불가 | 쿼리 되돌림 |
| C | 외부 검색엔진/FULLTEXT와 색인 파이프라인 | 확장성 | 새 인프라·동기화·검색 의미 변화, MVP 범위 초과 | 별도 운영 계획 필요 |

## 5. 독립 검토

세 검토자는 같은 최초 A안과 worklog를 읽고 서로 결과를 공유하지 않았다. 파일·STATE·제품·Git 변경과 runtime 검증 없이 독립 소스/정본 검토했다. 다음은 결과의 손실 없는 핵심 요약이며 검증 PASS를 의미하지 않는다.

### Product Reviewer — `/root/m7_product_review`

- 권고 APPROVE_WITH_CHANGES, 확신도88, HIGH.
- 사실: FR-SEARCH-01은 게시글 중심이나 Pages 샘플은 독립 엔티티처럼 보인다. Main/검색/TopBar 골격만 존재하고 기존 following 목록은 친구를 포함한다. 공통 optional-auth resolver는 없고 controller별 rejectInvalidBearer가 있다.
- 가정: BLOG/USER/TAG counts도 게시글 수, 로그인 main은 universe. 이 의미를 사용자 승인·PRD/UI에 명시해야 한다.
- 이점: 신규 엔티티 검색/인프라 없이 핵심 발견 경험, inline/entity 오탐 방지, 3기존 목록으로 현황 공급, M8 제외.
- 위험/완화: 엔티티탭 오해(높음)→facet 설명; following 통계 혼동(높음)→피드 전용 의미; 인증/stale(중~높음)→상태표/세대폐기; migration audit/partial(중간)→보존·재실행; facets 중복/ACL(중간)→snapshot/distinct matrix.
- 가장 강한 반대: B가 더 작고 schema 없이 빠르며 엔티티형 디자인보다 탐색 경험이 약해질 수 있다. 단 B는 정확 검색 요구를 만족하지 못한다.
- 실패: 친구가 following에도 포함, 만료토큰 guest강등, tag join count중복, updated_at변조, spring누락/amp오탐, stats실패0 표시.
- 필수 보완: post-centric 명시승인·정본/카피, 실제 인증코드/상태표, following 의미, audit 보존, 승인 후 정본·역할배정.

### Architecture Reviewer — `/root/m7_architecture_review`

- 권고 APPROVE_WITH_CHANGES, 확신도87, HIGH. 인증·snapshot·migration보존/복구 미확정이면 BLOCKED 승격 필요.
- 사실: 공통 QueryDSL ACL/optional viewer resolver/batch counts 없음; V1 updated_at ON UPDATE; missing keyword를 필수 RequestParam에만 맡기면 기존 exception handler가500 처리할 가능성; 기존 post service에 large offset 보호 선례가 있음. Flyway Java는 Spring DI 밖에서 실행한다.
- 가정/이점: 기존스택·post중심·파생cache·원문보존·별도 viewer정책. 정확한 text검색과 batch DTO로 구현 가능.
- 위험/완화: 인증(높음)→상태표·신규 resolver, ACL조인(높음)→EXISTS/distinct 공통predicate, snapshot(중~높음)→명시RR·동시writer test, audit(높음)→시간원값 UPDATE, 캐시stale(중간)→offline재구축, LONGTEXT/N+1(높음)→projection/배치/상한, wildcard/offset(중간)→검증, missing keyword(중간)→명시400.
- 가장 강한 반대: 단순 캐시 추가가 아니라 인증·ACL·복구를 함께 확정하는 HIGH 계약이며 존재하지 않는 resolver 재사용을 가정하면 보안 경계가 빠진다.
- 실패: 잘못된viewer강등, friend섞임, multi-tag중복, count다른snapshot, V5부분실패·audit변조, 구writer cache stale, 100카드 N+1.
- 필수 보완: 정확인증표, 공통ACL, 모든metadata RR snapshot와 matchedTypes 정의, DISTINCT/EXISTS/projection/offset, 이관transaction/audit/recovery, 모든write/rebuild, HTTP/OpenAPI오류계약.

### Delivery & Risk Reviewer — `/root/m7_delivery_review`

- 권고 APPROVE_WITH_CHANGES, 확신도91, HIGH.
- 사실: API/DB/ACL/UI를 함께 변경하고 기존 `{"type":"doc"}` fixture가 있다. M6 실제MySQL/immutableJAR/Chrome1440 인수 선례가 있으나 M7은 미실행이다.
- 가정/이점: 격리DB검증·백필완료전기동금지·M8제외, nullablecache와원문보존, post중심/배치/기존의존성.
- 위험/완화: 미확정API(높음)→canonical표/예제, 백필실패/재시작(높음)→transaction/재실행·restore, audit(중간)→전후비교, ACLcount(중간)→matrix, staleFE(중간)→authloading/세대guard, 전달증거(중간)→실제HTTP/MySQL/Chrome/SHA.
- 가장 강한 반대: 구체안과기존인프라가충분해추가gate가일정을늦춘다는반론. 그러나 구writer stale·빈doc경계·복구증거는필수다.
- 실패: 중간malformed후부분cache, oldwriterstale, 빈doc오판, facet정보누출, 계정A→B늦은응답, 실행JAR덮어쓰기.
- 필수 보완: friends canonical매핑·response예제, 빈doc유효경계,100/101/201/gap/실패·restore검증, 원문/audit보존, readiness/호환표, 실제acceptancegate, URL/state, 독립리뷰가능커밋분할.

### 진행자 종합

- 세 검토 모두 A의 정확성·범위에 동의하되 위 보완을 요구했다. 서로 다른 방향의 결론은 없으며 다수결이 아니라 실제 소스에서 확인한 인증검사 부재/ON UPDATE/빈doc 허용을 근거로 A-v2를 보완했다.
- 위험 HIGH. 공개 API·DB·시각 정본과 데이터 정본의 표현 정합화가 있어 자동승인 조건을 충족하지 않는다.
- runtime 성능·migration transaction 동작·디자인 인수는 아직 가정/미실측이며 Task1~5 필수 gate다. 검사에서 실패하면 구현을 수정·재검증하며 요구사항을 낮춰 통과시키지 않는다.

## 6. A-v2 — 사용자 승인 계약 (실행 보류)

### 6.1 피드

- `GET /api/v1/feed/public?keyword=&page=0&size=20&sort=latest`: optional auth, PUBLIC 발행글. 로그인 상태라도 이 API는 PUBLIC만. `keyword`는 제목 또는 작성자 닉네임의 부분 문자열 OR 검색이다.
- `GET /api/v1/feed/universe?relation=all&keyword=&page=0&size=20&sort=latest`: 인증 필요. viewer→author ACCEPTED인 사람의 PUBLIC/UNIVERSE 발행글. 자기 글·PRIVATE·draft는 포함하지 않는다.
- `all`: outgoing ACCEPTED 전체(친구 포함), `friends`: outgoing과 incoming 모두 ACCEPTED, `following`: outgoing ACCEPTED이고 incoming ACCEPTED가 아닌 사람. 이 마지막 값은 **피드 탭 전용 의미**이며 기존 `/universe/following`의 친구 포함 계약은 변경하지 않는다. 원본 Main Feed 샘플은 친구/단방향을 나눠 표시하지만 데이터 계약은 본 안으로 명시 승인받는다.
- guest 메인은 public, 로그인 메인은 universe. guest 친구/내가 발견한 탭은 로그인 안내를 표시하고 보호 API를 호출하지 않는다.
- FE의 현재 로컬 key `friend`는 canonical API/URL `friends`로 통일한다. 관계 enum은 소문자, type enum은 대문자, sort는 소문자이며 다른 대소문자/알 수 없는 값은400이다. following 탭에는 `친구를 제외한 내가 발견한 사람의 글입니다.` 안내를 같은 보조텍스트 스타일로 제공한다. 우측 현황의 following 수는 기존 계약대로 친구 포함임을 설명한다.
- 모든 필터·ACL은 DB pagination 이전 적용. main 전체 건수는 API totalElements. 없는 관계는 정상 empty, 관계 미가입을 오류로 만들지 않는다.
- 원본 3열/히어로·탭·32px 페이지네이션을 유지한다. 제목/작성자 검색은 중앙 탭 아래 같은 토큰의 간단한 검색 form으로 제공한다(기존 전역 검색은 통합 검색 유지).

### 6.2 공통 조회 경계

- post/blog/author soft delete는 모든 결과와 count에서 제외. 작성자가 SUSPENDED여도 기존 글은 공개범위대로 포함. 차단해도 PUBLIC 자체의 읽기를 막지 않고, M5 차단으로 관계가 제거되면 UNIVERSE와 universe feed에서 제외한다.
- M6 `LikeController.rejectInvalidBearer`와 `JwtAuthenticationFilter.ATTR_ERROR_CODE`는 참고하되 **공통 resolver가 이미 존재한다고 가정하지 않는다**. M7 전용 `DiscoveryViewerResolver`를 새로 만들어 세 API가 공유하며 기존 M4/M6 API 인증 정책을 전역 변경하지 않는다. 상태표는 아래와 같다.

| 인증 상태 | public/search | universe |
|---|---|---|
| Authorization header 없음 | guest ACL | 401 AUTH_004 |
| 유효 토큰·ACTIVE·살아 있는 사용자 | 해당 viewer ACL | 해당 viewer 관계 |
| 유효 토큰·SUSPENDED·살아 있는 사용자 | 해당 viewer의 읽기 허용 | 해당 viewer 관계 읽기 허용 |
| 만료 토큰 | 401 AUTH_002 | 401 AUTH_002 |
| header는 있으나 비어 있음/잘못된 scheme·형식·서명·type | 401 AUTH_004 | 401 AUTH_004 |
| 유효 토큰이나 사용자 없음/soft-deleted | 404 USER_001 | 404 USER_001 |

SUSPENDED 로그인·mutation 금지는 유지하고 유효한 기존 토큰의 읽기는 기존 Universe 목록/게시글 읽기와 일치시킨다. author의 SUSPENDED도 제외조건으로 쓰지 않는다. 오류를 guest로 강등하지 않는다.
- 모든 M7 응답은 `ApiResponse`, `Cache-Control: private, no-store`. 조회수·알림·관계·like 등 mutation 없음.
- page 기본0·0이상 int, size 기본20·1~100, sort `latest|popular`만. 잘못된 타입/범위/enum은 400 `VALIDATION_001`. 큰 page도 overflow 없이 빈 items와 정확한 total 반환.
- offset은 `(long)page*size`로 계산한다. count 후 offset>=total이면 QueryDSL/JPA offset을 호출하지 않고 빈 페이지를 반환한다. offset<total인데 JPA int offset 한도를 넘으면 400 VALIDATION_001로 거부한다. count/empty 경로도 같은 snapshot이며 큰값을 int로 잘라 실행하지 않는다.
- keyword는 trim 및 연속 공백을 한 칸으로 정규화하고 UTF-16 길이 최대100. 피드의 누락/빈 검색어는 필터 없음. `%`, `_`, escape 문자는 문자 그대로 비교한다. prepared parameters 사용. case-insensitive 검색이며 유니코드 비교는 기존 MySQL utf8mb4 collation을 따른다.
- feed latest=`publishedAt DESC,id DESC`, popular=`viewCount DESC,publishedAt DESC,id DESC`.

### 6.3 검색·건수·카드

- `GET /api/v1/search?keyword=...&type=ALL&page=0&size=20&sort=latest`, optional auth. type은 대문자 `ALL|POST|BLOG|USER|TAG`만. 기본 ALL. keyword 필수·정규화 후1~100; 빈 입력은 API400, FE는 API 호출 대신 입력 안내를 표시한다.
- Controller의 keyword는 `@RequestParam(required=false)`로 받은 뒤 누락/null/blank를 명시적으로 VALIDATION_001 처리해 기존 MissingServletRequestParameterException 처리에 의존하지 않는다. page/size/sort/type/relation의 누락은 기본값, 명시적 빈 값은400이다.
- POST=제목 또는 본문 텍스트, BLOG=블로그 제목, USER=작성자 닉네임, TAG=태그명 부분검색. ALL=이 조건들의 OR. multi-tag/multi-field 중복은 post ID 기준 제거한다.
- 검색 ACL은 기존 PostAccessPolicy와 동일: 삭제 제외 후 owner 자신의 PRIVATE/draft 포함; 나머지는 PUBLIC 발행 또는 viewer→author ACCEPTED의 UNIVERSE 발행만. 남의 draft는 PUBLIC이어도 제외한다.
- 검색 latest=`coalesce(publishedAt,createdAt) DESC,id DESC`, popular=`viewCount DESC,coalesce(publishedAt,createdAt) DESC,id DESC`. draft의 기준은 createdAt이며 수정으로 자동 최상단 승격하지 않는다.
- 피드 응답 data는 기존 PageResponse 필드 그대로. items는 기존 PostSummary 필드에 `commentCount:long`, `likeCount:long`을 추가한 **별도 FeedPostSummary**다. 기존 PostSummary/BlogPage 계약은 유지한다.
- 검색 data는 PageResponse 필드(`items,page,size,totalElements,totalPages,hasNext,hasPrevious`)에 `counts:{ALL,POST,BLOG,USER,TAG}`를 더한 SearchResponse. items는 FeedPostSummary 필드에 `matchedTypes: (POST|BLOG|USER|TAG)[]`를 추가한다.
- counts는 같은 keyword·viewer ACL에 대한 전체 범위별 글 수이며 선택 type/page와 무관하다. ALL은 합집합의 distinct post 수다. 한 글이 여러 범위에 매칭될 수 있어 나머지 건수 합과 ALL이 다를 수 있다. items·total·counts는 한 read-only transaction의 동일 snapshot으로 읽는다.
- `@Transactional(readOnly=true,isolation=REPEATABLE_READ)` 안에서 viewer 확인·page·selected total·5facet·matchedTypes·태그·댓글/좋아요 batch metadata까지 읽는다. 모든 count는 DISTINCT post ID, tag/relation 조건은 EXISTS/NOT EXISTS로 조인 증폭을 피한다. matchedTypes는 선택 탭과 무관한 **전체 매칭 타입**을 고정 순서 POST/BLOG/USER/TAG로 반환한다. 이미 시작한 요청은 snapshot 기준이며 관계철회가 commit된 뒤 시작한 새 요청에는 철회가 적용된다. actual MySQL 동시 writer barrier 테스트로 snapshot을 증명한다.
- ALL 탭 카드 배지는 matchedTypes의 첫 항목(POST→BLOG→USER→TAG 우선), 개별 탭은 선택 type 배지. 모든 카드는 게시글 제목·메타·plain-text snippet이며 링크는 해당 글 상세다. owner draft는 draft 표시와 `/edit/:postId`로 연결한다. 독립 user/blog 엔티티를 반환하지 않는다.
- 이는 Pages 원본의 엔티티형 예시를 데이터 정본의 게시글 중심으로 정합화하는 **명시 승인 대상**이다. 디자인 파일은 수정하지 않고 PRD §9/검색화면에 기록한다. 검색 탭 아래 `블로그·사용자·태그 조건에 맞는 글을 검색합니다.`라는 보조 설명을 둔다. 발행글 상세 링크는 `/blog/{blogSlug}/{id}`다.
- snippet/excerpt는 검색용 plain text의 앞160 Unicode code points(초과시 말줄임표), HTML을 DOM에 삽입하거나 highlight markup을 생성하지 않는다.
- tags/commentCount/likeCount는 선택 페이지 post IDs로 batch 조회한다. M6 count 의미(살아 있는 댓글 root+reply, 탈퇴 댓글작성자 제외; 탈퇴 사용자 like 제외)를 유지한다. 카드마다 interactions API나 개별 aggregate 쿼리를 실행하지 않는다.
- DB projection은 목록에 필요한 scalar와 `content_text` 앞부분만 읽고 `content_json/content_html` LONGTEXT를 로딩하지 않는다. empty ID에 batch쿼리 없음. HTTP 요청 안 SQL SELECT 목표 상한은 guest feed6/auth feed7, guest search11/auth search12이며 page size1→100으로 늘어도 증가하지 않아야 한다. 구현상 더 큰 고정 상한이 필요하면 근거와 측정값을 리더·QA가 검토하며 N+1 허용으로 완화하지 않는다.

### 6.4 본문 검색 캐시·마이그레이션

- A안은 `posts.content_text LONGTEXT`라는 서버 파생 캐시를 추가한다. 클라이언트 입력 필드는 아니며 상세 API에 원문처럼 추가하지 않는다. canonical `content_json`의 text node를 순서대로 추출한다. inline mark는 경계가 아니며 paragraph/heading/list item/table cell/hardBreak 등 화면 구획에는 공백을 둔다. 공백 정규화 후 trim, image src/alt/title·link href·JSON node/mark 이름은 제외한다. 원본 text에 `<tag>`/`&`/emoji가 들어 있으면 실제 문자로 남는다.
- 기존 검증된 TipTap→HTML 렌더와 같은 텍스트 의미를 갖는지 unit 테스트한다. 예: `sp`+bold(`ring`)=`spring`; 문단 A/B=`A B`; 실제 `&`는 `amp`로 검색되지 않는다. 최소 `{"type":"doc"}`, `{"type":"doc","content":[]}`, 빈 문단·이미지만 있는 문서는 정상 빈 문자열이다. malformed JSON/non-object/non-doc root/잘못된 text·content shape/알 수 없는 node는 실패다. 기존 허용노드·depth32/node10000/UTF8 1MiB 경계를 동일하게 고정하며 source text는 HTML unescape하지 않는다.
- V1~V3는 불변. V4 SQL로 nullable 컬럼을 추가하고 V5 Java `BaseJavaMigration`으로 기존 모든 행을 `id > lastId ORDER BY id LIMIT 100` keyset으로 backfill한다. migration은 versioned immutable `PostTextExtractorV1`을 호출하고 일반 `PostTextExtractor`도 V1로 위임한다. 향후 의미 변경은 새 버전·forward migration이며 과거 V5/추출기의 의미를 바꾸지 않는다. Spring service DI에 의존하지 않는다.
- 원문 JSON/HTML·ID·publishedAt·createdAt·updatedAt·deletedAt·viewCount를 보존한다. UPDATE는 읽은 원래 updated_at을 명시적으로 함께 대입해 ON UPDATE를 억제한다. null 날짜와 microsecond 정밀도도 원값 비교한다.
- **트랜잭션/복구:** V4 DDL은 별도 committed 상태로 남는다. V5는 DML 전체를 하나의 JDBC transaction(autoCommit=false)으로 묶고 batch별로 commit하지 않는다. Flyway가 소유한 transaction이면 이를 사용하고, 직접 소유할 경우 성공시에만 commit·실패시rollback·기존connection상태복원한다. 메모리는100행단위지만 실패하면 V5의 모든 text update는 rollback되어야 한다. MySQL 실측이 다르면 완료로 인정하지 않고 트랜잭션 구현을 수정한다.
- 잘못된 기존 JSON은 건너뛰거나 HTML fallback으로 바꾸지 않고 offending ID만 보고해 실패한다. 원문/자격증명은 로그에 남기지 않는다. Flyway 실패로 앱 기동/readiness를 차단하므로 partial검색을 공개하지 않는다. NULL cache가 남으면 M7 read readiness도 실패한다.
- 합성 V3 fixture에 rowcount/maxID·100/101/201경계·IDgap·deleted/draft·빈문서·후반invalid를 넣어 실패후V4만남음/V5전체rollback·Flywayhistory를 확인한다. 격리DB에서 실패fixture 원인수정→필요시 Flyway repair→V5처음부터 재실행의 멱등성과 snapshot backup/restore를 검증한다. **실제 사용자 DB의 원인수정/repair/복원은 별도 승인**이며 임의 행삭제/원문수정으로 통과시키지 않는다. empty DB 전체 migrate도 검증한다.
- 현재 앱의 모든 post 생성/내용수정 경로에서 같은 transaction으로 content_text를 갱신한다. 기존 코드 직접 Post.create/update 사용 테스트도 적용 경로를 확인한다. 대량 메모리 적재·시작시 무제한 재색인은 금지한다.
- canonical text는 Post.create/update에서도 보장해 직접 factory를 호출하는 경로의 누락을 막는다. content validation의 파생값과 entity에 저장된 값은 같은 V1 extractor 결과다. 이미지 메타만 바꾸는 updateImages는 본문이 바뀌지 않으면 text를 바꾸지 않는다.
- 운영 복귀 시 새 컬럼을 삭제하지 않는다. 구버전 writer가 실행된 후 M7을 다시 올리는 경우에는 stale cache 전체 재구축 검증이 필요하므로 단순 downgrade/re-upgrade가 안전하다고 주장하지 않는다. 검색 성능 인덱스/외부엔진은 후속이며 최대페이지100과 쿼리 수 상한은 M7에서 검증한다.
- **호환표:** V3앱+V3DB=기존동작, M7앱+V3DB=V4/V5완료후기동, V3앱+V5DB=검색사용불가·cache정합성보장안함, M7재도입=구writer정지→offline캐시검증/재구축→NULL/stale0확인→기동. 단순 startup full-scan/rebuild는 하지 않는다.
- backend는 같은 extractor로 keyset100의 offline `verifyPostSearchText`(읽기전용 기본) / `rebuildPostSearchText`(명시 offline/apply 확인 필수) Gradle 작업을 제공한다. runtimeClasspath의 별도 Java main으로 JDBC접속하고 DB정보는 env로만 받는다. verify는 invalid/null/stale 개수·ID만 출력, rebuild는 원문/audit보존과 transactionrollback을 migration과 같이 적용한다. 공개 API/웹 관리자 기능은 추가하지 않는다. 이 실행에서 apply 대상은 격리 합성 DB뿐이다. 정확한 실행명령·필수env명·복구절차는 README에 검증 후 기록한다.

### 6.5 FE 상태·메인 현황

- URL을 keyword/type/relation/page/size/sort의 기준으로 삼고 탭·keyword 변경은 page0, 새로고침/뒤로가기로 복원한다. 요청 세대와 viewerId 변경 시 이전 결과·counts를 즉시 숨기고 stale 응답을 무시한다. 인증 준비 중에는 guest 결과를 먼저 표시하지 않는다.
- 확정 인증 실패는 이전 개인 결과를 폐기한다. 단순 네트워크 오류는 오류·재시도를 표시하고 다른 keyword/계정 결과를 남기지 않는다. 로딩/빈 상태/오류를 분리한다.
- AUTH_002/004, USER_001로 조회가 최종 실패하면 결과·counts를 폐기한다. apiClient의 기존401 refresh는 유지하고 refresh후 같은조회 실패도 빈개인상태+오류로 처리한다. 같은키의 transient 오류는 기존결과 보존 또는 오류화면 중 일관된 하나를 선택할 수 있으나 다른키/계정 결과는 절대 재사용하지 않는다.
- TopBar submit은 trim/정규화된 keyword로 `/search` 이동. 검색 페이지 입력과 URL 동기화; blank 상태에서 전체 글 검색을 호출하지 않는다.
- Main 우측 유니버스 현황은 기존 `/universe/friends`, `/universe/following`, `/universe/followers`를 `page=0,size=1`로 조회한 totalElements 3개를 사용한다. 신규 summary API를 만들지 않는다. 이 following 수에는 기존 계약대로 친구가 포함된다. 독립 loading/error 및 계정 stale guard; 실패를 0으로 표시하지 않는다. guest는 로그인 안내. 최근 알림은 M8 현상 유지.
- 승인 후 PRD 라우트 맵의 `/universe/summary`를 이 세 기존 목록으로 정합화한다. runtime중지는 승인 blocker가 아니라 실제 인수 전에 리더가 해결할 실행 선행조건이다.

### 6.6 검증·완료

- 실 MySQL: ACL/result/count/facet 일치, multi-match distinct, 21+ paging/동점정렬/큰offset, 제목/본문/닉네임/블로그/태그와 escaping, 1 vs100 rows SQL query-bound, DB migration 보존/오류/재시작, relation 전환/차단 이후 새 조회, no view/no notification.
- Controller/OpenAPI: 공통 wrapper 및 모든 query/error/security, invalid Bearer·guest universe401·SUSPENDED/deleted 경계, 쿼리문자열 포함 실제 HTTP.
- FE: 실제 user interaction으로 검색 submit/5탭/3관계탭/page/back/reload/empty/error/retry/권한상실/계정전환/stale 응답·counts·draft 링크.
- 독립 QA: 원본 디자인과 Chrome1440px main/search 대조 및 합성 HTTP 시나리오. 코드작성자와 최종검토자 분리. 필수 finding 해소 후 최종소스/검증증거 SHA를 맞춰 dev PR·merge한다.

### 6.7 Canonical 응답 예제와 전달 단위

아래 data는 게시글123이 제목과태그에 모두 일치하는 예다. 실제 outer wrapper는 기존 success/data/error/timestamp 계약을 그대로 쓴다. 빈페이지는 items=[]이어도 counts/total을 전체검색 기준으로 유지한다.

```json
{"items":[{"id":123,"blogId":10,"blogSlug":"orbit","blogTitle":"궤도","author":{"id":1,"nickname":"별","profileImageUrl":null},"category":{"id":2,"name":"미분류"},"title":"spring","excerpt":"spring","thumbnailUrl":null,"visibility":"PUBLIC","viewCount":0,"publishedAt":"2026-11-09T00:00:00Z","createdAt":"2026-11-09T00:00:00Z","updatedAt":"2026-11-09T00:00:00Z","tags":["spring"],"commentCount":0,"likeCount":0,"matchedTypes":["POST","TAG"]}],"page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false,"hasPrevious":false,"counts":{"ALL":1,"POST":1,"BLOG":0,"USER":0,"TAG":1}}
```

피드 data는 위 PageResponse 필드만 사용하며 counts/matchedTypes는 없다. draft는 publishedAt=null이다. migration/text-cache, backend discovery API, frontend UI, QA/closeout을 각각 리뷰 가능한 커밋 단위로 나누되 리더가 파일을 명시해 stage하며 사용자 루트 파일을 포함하지 않는다. 실제 인수는 M6처럼 immutable bootJar·source/JAR SHA·MySQL8.4·Chrome1440px로 검증한다.

## 7. 결정

- 상태: APPROVED / 실행 보류
- 결정: 사용자 `전체 승인. 하지만 작업 착수는 추가 지시가 있을 때까지 잠시 보류.`에 따라 A-v2 전체를 승인된 계약으로 보존하고 구현·마이그레이션·검증·Git 통합을 보류한다.
- 결정 주체/일시: 사용자 / 2026-11-09.
- 재개 조건: 사용자의 명시적 후속 착수·재개 지시. 같은 A-v2에 대한 재승인은 요청하지 않으며, 범위·계약이 달라지면 변경점만 심의한다.
- 승인 필요 근거: 헌법 제3조, governance README §5의 공개 API·DB 계약 HIGH.

## 8. 후속 작업

1. 독립 3인 검토→root 보완 계약 및 실행계획→사용자 결정.
2. 승인 후 리더 REQUIREMENTS·PM PRD 동기화, backend/frontend 구현, QA 독립 검증.
3. 실제 검증·리뷰 완료 후 리더 dev 통합·증거/기록 closeout.

## 9. 문서 반영

- [x] 결정 레지스터
- [ ] 필요 시 위험 레지스터/ADR
- [ ] 승인 후 REQUIREMENTS/PRD
- [x] M7 worklog 착수

## 10. 정정 기록

승인 이후 변경은 이 절에 append한다. 현재 초안은 승인 기록이 아니다.

### 2026-11-09 — 사용자 전체 승인 및 실행 보류

- 사용자 원문: `전체 승인. 하지만 작업 착수는 추가 지시가 있을 때까지 잠시 보류.`
- §6 A-v2 계약과 실행계획은 승인됐다. 기존 검토의 미승인/REVIEWING 표현은 당시 이력이다. 승인 후 실행을 시작하지 않았으며 준비 중이던 PM/QA도 중단했다.
- 재개 시 REQUIREMENTS/PRD 정본 동기화부터 이어간다. 제품 코드/테스트/migration과 runtime은 아직 미착수이며 사용자 DB에는 적용하지 않았다.

### 2026-11-09 — 명시 실행 재개

- 사용자 `승인된 작업 시작해.`로 위 실행 보류를 해제했다. A-v2 전체승인은 유지하며 추가 승인 없이 구현·독립QA·dev 통합을 진행한다.
- 이전 문서의 실행 보류/미확정 표현은 당시 이력이다. 현재 계약은 §6 승인본, 현재 실행 상태는 본 재개 기록과 M7 worklog를 따른다.
- root가 REQUIREMENTS6.10-A를 동기화하고 PM이 PRD를 동기화한다. backend/frontend 구현과 QA인수준비를 소유권별로 배정했다.
