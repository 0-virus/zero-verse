# Planning Review: M3 카테고리 계약과 삭제·순서·조회 정책

- 회의 ID: `M3-20260906-categories`
- 관련 마일스톤: M3
- 상태: `USER_DECISION_REQUIRED`
- 소집 사유: 공개 API·오류 계약, 카테고리 소유권·공개 조회, soft delete와 DB unique 제약, BE/FE 동시 변경.
- 생성 일시: 2026-09-06 KST
- 진행자: Codex 리더
- 관련 준비: `docs/PM-M3-readiness.md`, M2 종료 검증과 병행한 준비. M3 구현은 아직 시작하지 않았다.
- 관련 ADR: ADR-0002(오류 분류), ADR-0003(보안), ADR-0004(계약 전용 오류와 검증 게이트)

## 1. 결정할 질문

확정된 FR-CAT-01~05를 구현하면서 삭제된 카테고리의 값 재사용, 변경 불가 카테고리의 순서, 공개 글 수와 오류 계약을 아래 최소 범위로 정합화할 것인가?

## 2. 배경과 범위

- M1은 `Category.createDefault`, `CategoryType`, 기본 카테고리 조회만 구현했다. M3는 목록/생성/수정/삭제/순서변경과 설정 화면을 완성한다.
- `V1__init.sql`의 name/order unique key에는 `deleted_at`이 없다. 삭제된 행도 같은 이름과 순서를 계속 점유하므로 삭제 후 재생성·정렬과 충돌할 수 있다.
- PRD §5.4는 LOCKED의 순서도 변경 불가라고 명시한다. FR-CAT-05의 같은 부모 전체 ID 배열을 처리할 때 잠금 행까지 무조건 재번호하면 이 규칙을 어긴다.
- 카테고리 글 수와 삭제 시 글 이동은 M3 요구사항이나 Post CRUD는 M4다. 현재 DB에는 posts 테이블이 있으므로 Post 기능 전체를 앞당기지 않고 실제 SQL 계약을 검증할 수 있다.
- 공개 블로그의 카테고리 목록에는 별도 GET allowlist와 조회자별 글 수 정책이 필요하다. 비공개·임시저장 글의 존재가 공개 수치로 새지 않도록 해야 한다.
- FR-CAT-03 수정 필드는 name/type/display_order이며 parent_id는 없다. PRD 본문의 부모 편집 가능 문구와 구분해야 한다.

포함: 루트+자식 한 단계, 소유자만 쓰기, DEFAULT/LOCKED 규칙, 실제 글 수·글 이동, 같은 부모 순서변경, 단일 리스트 카드·인라인 편집·루트/자식 추가·타입 선택·드래그 및 키보드 순서변경, 블로그 카테고리 표시.

제외: Post CRUD·에디터·새 이미지 업로드·Universe 관계 변경·카테고리 slug·타 부모로 이동·새 DnD 라이브러리·가짜 글 수.

## 3. 근거 문서

| 문서 | 절 | 확인된 내용 |
| --- | --- | --- |
| REQUIREMENTS.md | FR-CAT-01~05 | 트리·글 수·소유권·삭제 글 이동·전체 형제 순서 배열 |
| REQUIREMENTS.md | FR-BLOG-02, NFR-04/08/09 | 글 공개범위, 오류·무결성·검증 |
| PRD.md | §5.4, §7 settings/posts, §9-H/R, §10 M3/M4 | 불변 타입, 단일 리스트·인라인, 마일스톤 경계 |
| design/DESIGN-SYSTEM.md | §8.7 | 카테고리 카드/행/추가/삭제 확인 카피 |
| V1__init.sql | categories, posts, universes | 삭제 행을 포함한 이름/순서 unique, 실제 집계·이동 대상 |
| Category.java / CategoryRepository.java | 현재 구현 | DEFAULT 생성·기본 조회만 존재 |
| SecurityConfig.java | 공개 GET allowlist | 카테고리 GET은 아직 공개되지 않음 |
| ErrorCode.java | CAT_001~003 | 카테고리 없음·깊이·기본 삭제 금지만 정의됨 |

## 4. 검토할 권고안(미확정)

### Q1. soft delete와 unique

- 권고: V1은 유지하고 V2 forward migration으로 살아 있는 카테고리에만 이름/순서 unique를 적용한다. 삭제 행은 이력을 보존하면서 값 점유를 해제한다. 생성/수정/삭제/정렬은 블로그 행 잠금으로 직렬화하고 DB 제약도 유지한다.
- V2는 기존 parent_key를 유지하고 `active_key AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED`를 추가한다. 이름/순서 unique를 각각 `(blog_id,parent_key,name,active_key)`, `(blog_id,parent_key,display_order,active_key)`로 교체한다. nullable deleted_at을 unique에 단순 추가하는 방식은 사용하지 않는다. JPA는 generated column을 직접 쓰지 않는다.
- 배포 전 기존 데이터·제약을 점검하고 V2 적용 후 검증한다. 재사용 이력이 생긴 뒤 V1 제약으로 단순 되돌리면 중복 때문에 실패하므로 DB 다운그레이드/이력 삭제는 하지 않는다. 문제 발생 시 카테고리 쓰기를 중단하고 호환 애플리케이션 복귀 또는 별도 forward repair로 복구한다. 실제 운영 데이터 삭제·복구 작업은 별도 승인 대상이다.
- 대안: 현 V1을 유지하고 삭제된 이름·순서를 영구 점유한다. migration은 없지만 사용자가 삭제한 이름을 재사용할 수 없고 정렬 구현이 복잡해진다.
- 검증: 실제 MySQL에서 기존 데이터 업그레이드, 살아 있는 형제의 중복 거부, 삭제 후 이름/순서 재사용, 순서 교환의 중간 unique 충돌 방지, 동시 생성/정렬/삭제.

### Q2. 불변 타입·부모·오류

- 권고: DEFAULT는 이름·타입·삭제를, LOCKED는 이름·타입·순서·삭제를 변경하지 않는다. DEFAULT 순서는 기존 PRD가 금지하지 않았으므로 변경 가능으로 명확화한다. GENERAL만 GENERAL/LOCKED로 설정할 수 있으며 DEFAULT 생성은 시스템 전용이다. 부모 선택은 생성 때만 지원하고 수정에서의 부모 이동은 M3에 추가하지 않는다.
- 순서 배열은 같은 부모의 살아 있는 모든 ID를 정확히 한 번 포함한다. 기존 형제의 정렬된 displayOrder 값들을 요청 ID 순서로 배정하되 LOCKED의 숫자 값은 보존한다. LOCKED에 다른 값이 배정되는 요청은 400이다. 삭제로 생긴 간격은 허용하며 삭제마다 재번호하지 않는다. DB 중간 unique 충돌은 트랜잭션 내 임시 음수 순서·flush·최종 비음수 순서의 두 단계로 막는다.
- 동시 편집은 최소 계약인 **last-write-wins**를 권고한다. 생성/삭제로 형제 ID 집합이 달라진 오래된 요청은 CAT_007로 거부하지만, 같은 ID 집합의 두 순서 변경은 직렬 처리 후 마지막 요청이 적용된다. 전체 ID 검증이 모든 stale 요청을 차단한다고 주장하지 않는다. revision/ETag/새 충돌 코드는 이번에 추가하지 않는 대신 이 한계를 안내·테스트한다.
- GENERAL 루트를 삭제할 때 불변 자식이 있으면 전체 삭제를 거부한다. 불변 자식을 우회 삭제하지 않는다.
- 기존 CAT_001~003의 의미를 유지한다. 신규 오류 제안은 아래 계약 표와 같다. Bean Validation은 VALIDATION_001을 유지한다.
- 대안: 기존 CAT_003에 수정·잠금·순서·권한 실패까지 합친다. 코드 수는 줄지만 기존 의미와 복구 경로가 달라져 권고하지 않는다.

### Q3. Post 선행 의존·공개 조회·화면

- 권고: 카테고리 repository의 실제 SQL로 글 수를 집계하고 삭제된 카테고리의 모든 소속 글을 DEFAULT로 이동한다. Post 엔티티/CRUD를 먼저 구현하거나 0을 반환하는 stub을 만들지 않는다. 테스트는 실제 posts/universes 행으로 검증한다.
- 잠금은 blogId 기준으로 획득한 뒤 상태를 재조회한다. M4의 category_id를 쓰는 모든 글 생성·수정 경로도 같은 blog lock에 참여하고 활성·동일 블로그 카테고리를 다시 검증해야 한다. native bulk 이동 전 flush 및 이후 persistence context 갱신을 보장한다. 이 의존은 M4 준비 문서·ADR에 전달하며 Post 기능 선행 구현은 하지 않는다.
- 목록 GET은 method 제한으로 공개한다. 글 수는 삭제 제외·조회자 공개범위를 적용하고 소유자의 명시적 옵션만 임시저장을 포함한다. 다른 사용자가 draft 옵션을 요청해도 임시저장 수를 노출하지 않는다.
- UI는 정본 단일 카드에 계층을 들여쓰기로 표현한다. 기존 플랫폼 DnD와 키보드 접근을 사용한다. 글 삭제 이동 안내는 실제 저장 정책인 '미분류'로 명시하는 카피 정합화가 필요하다.
- 공개 글 목록 필터의 실제 Post API 연동은 승인된 M4 범위와 구분한다. M3 카테고리 표시를 Post API 완성으로 보고하지 않는다.
- 대안: 글 수·삭제 이동을 M4로 미룬다. M3 FR-CAT 완료 범위를 줄이므로 권고하지 않는다.

### Q4. 초기 설정 시작 카테고리

- M2에서 M3로 이관한 시작 카테고리 칩을 포함한다. 새 initial-setup 필드/API는 만들지 않는다. 칩은 루트 GENERAL 이름이며 타입·부모 고급 편집은 카테고리 관리에서 한다.
- 기존 initial-setup 성공 후 카테고리 GET → 누락된 칩만 순차 POST → 완료 이동. 이름 중복·응답 유실·부분 실패는 GET 재조회로 성공분을 보존하고 남은 칩만 재시도한다. 이미 완료된 initial-setup을 반복하지 않는다.
- 새로고침 후에도 setup 완료 사실을 되돌리지 않는다. 카테고리 관리에서 실제 저장분을 확인하고 나머지를 추가하도록 명시한다. 부분 실패를 전체 성공으로 숨기거나 성공분을 삭제해 롤백하지 않는다.
- 대안: initial-setup에 카테고리 일괄 입력과 원자 저장을 추가한다. 새 공개 계약과 M2 변경이 늘어나므로 현재 권고하지 않는다.

### 정확한 계약 제안(아직 승인되지 않음)

JSON 이름은 기존 camelCase를 따른다. 경로의 `{blogId}`는 삭제되지 않은 블로그·소유자를 확인한다. 없는 블로그는 BLOG_001, 다른 소유자 쓰기는 CAT_004, 경로 블로그에 속하지 않거나 삭제된 category/parent는 CAT_001이다. 인증 없는 쓰기는 AUTH_004다.

| 호출 | 입력 | 성공 data |
| --- | --- | --- |
| GET `/api/v1/blogs/{blogId}/categories` | page=0, size=20(max100), includeDrafts=false | 공통 PageResponse. items는 루트 CategoryResponse이며 각 children에 직속 자식 전체를 포함. totalElements는 루트 수. 루트/자식은 displayOrder,id 오름차순 고정 |
| POST 같은 경로 | name(공백 제거 후 1~100), parentId(nullable), type(GENERAL/LOCKED), displayOrder(필수 비음수 int) | 생성 CategoryResponse, HTTP201 |
| PUT `.../categories/{categoryId}` | name/displayOrder 동일 검증, type은 DEFAULT/GENERAL/LOCKED를 파싱하되 기존 DEFAULT의 같은 값 유지 외 DEFAULT 전환 금지. parentId 수정 없음 | 수정 CategoryResponse, HTTP200 |
| DELETE `.../categories/{categoryId}` | 없음 | 공통 성공 래퍼 data=null, HTTP200 |
| PUT `.../categories/order` | 같은 부모 전체 ID의 JSON 배열(중복/빈 배열 금지) | 공통 성공 래퍼 data=null, HTTP200 |

- CategoryResponse: `id, parentId, name, type, displayOrder, postCount, children`. children은 자식에서는 빈 배열. postCount는 해당 카테고리 직접 소속 글 수(하위 합산 아님). owner 관리 화면은 includeDrafts=true로 전체 관리 수를 요청한다.
- 목록 공통 페이징을 폐기하지 않고 **루트 단위 페이지 + 자식 포함**으로 계층을 유지한다. FE 관리/공개 패널은 모든 root 페이지를 순차 읽어 전체 목록을 구성한다. 관리 쓰기는 전체 로드 성공 전 비활성화하며 일부 페이지만 얻은 목록으로 reorder를 보내지 않는다. 로드 중 타 클라이언트 변경으로 인한 목록 차이는 서버의 전체 ID 검증으로 실패시키고 재조회한다.
- 글 수: 삭제된 post 제외. 비로그인은 PUBLIC 발행 글. 로그인 비소유자는 PUBLIC 및 viewer→owner의 ACCEPTED 관계가 허용하는 UNIVERSE 발행 글. 소유자는 PUBLIC/UNIVERSE/PRIVATE 발행 글과 includeDrafts=true일 때 자신의 임시저장. 비소유자의 includeDrafts=true는 CAT_004로 거부한다. 다른 사람의 draft/private 수는 노출하지 않는다.
- 삭제 이동은 대상 GENERAL subtree의 **삭제된 post를 포함한 모든 posts 행**을 동일 블로그의 활성 DEFAULT로 이동한다. soft-deleted post 복원 시에도 삭제 category를 가리키지 않도록 하며, 집계에서는 계속 삭제 post를 제외한다.
- LOCKED는 접근제어가 아니라 변경 잠금이다. GET에 카테고리 이름/계층은 공개되나 볼 수 없는 글의 수를 포함하지 않는다. GENERAL→LOCKED는 되돌릴 수 없음을 저장 전에 안내한다.
- 이름 중복은 DB collation의 기존 대소문자 정책과 일치시킨다. name unique는 CAT_005, displayOrder 중복 및 부적합 순서 배열은 CAT_007. DEFAULT/LOCKED 불변 필드를 같은 값으로 보내는 멱등 수정은 허용하되 다른 값은 CAT_006. DEFAULT 직접 삭제는 기존 CAT_003, LOCKED 또는 잠금 자식이 있는 subtree 삭제는 CAT_006.
- POST의 DEFAULT 요청과 PUT의 금지된 타입 전환은 CAT_006이며 알 수 없는 enum/null/형식·필수 입력 오류는 VALIDATION_001이다. 인증·경로 블로그·소유권을 검증한 뒤 도메인 불변 규칙을 적용한다.

| 신규 코드 | HTTP | 제안 메시지 |
| --- | --- | --- |
| CAT_004 | 403 | 카테고리에 대한 권한이 없습니다. |
| CAT_005 | 409 | 같은 부모에 동일한 이름의 카테고리가 있습니다. |
| CAT_006 | 400 | 변경할 수 없는 카테고리입니다. |
| CAT_007 | 400 | 카테고리 순서가 올바르지 않습니다. |

필수 검증: MySQL V1→V2 기존행 보존, 동시 unique/순서 경쟁, 잠금 상태의 삭제·정렬 우회, 101개 이상 루트와 자식의 페이지 경계, 비로그인/관계방향/소유자별 count, 실제 post 일괄 이동, 시작 칩 부분 실패·새로고침 복구, BE/FE 계약 교차검증. 구현 중 이 제안으로 해소되지 않는 모순은 임의 확정하지 않는다.

## 5. 독립 검토

세 역할은 동일 REVIEWING 원안을 별도 컨텍스트에서 읽었고 서로 중간 의견을 공유하지 않았다. 아래는 진행자의 제출 결과 요약이다. 계약 보완은 세 제출 이후 진행자가 취합한 것이며 검토자의 사용자 승인으로 간주하지 않는다.

### Product — Codex pm

- 권고 APPROVE_WITH_CHANGES / 최초 제출 확신도 88, 원문 재전달 90 / 위험 HIGH. 권고와 필수 조건은 동일하며 두 수치를 보존한다.
- 근거: FR-CAT-01~05, FR-BLOG-02, PRD §5.4·§7·§9-H/R·§10. 기존 posts 테이블로 실제 글 수/이동을 검증할 수 있어 Post CRUD 선행 구현은 불필요하다.
- 필수 보완: 루트 페이징+children DTO·전체 로드 전 reorder 차단·101개 경계, DEFAULT/LOCKED 정책·되돌릴 수 없는 잠금 안내, count와 오류 계약, 시작 칩 부분 성공 복구를 명시한다. M3 카테고리 선택을 M4 Post 필터 완성으로 보고하지 않는다.
- 이점: 정본 단일 리스트와 기존 API를 사용해 기능 경계를 유지한다. 가정은 보통 카테고리 수가 작고 새 DnD 의존성이 불필요하다는 것이다.
- 위험/실패: 일부 페이지로 reorder하면 형제가 누락되고, setup 재시도에서 중복이 생기거나 성공분이 유실될 수 있다. 전체 로드 잠금·GET 기반 복구로 완화한다.
- 가장 강한 반론: 소규모 카테고리에 계약을 과도하게 늘리면 MVP 전달을 늦춘다. DEFAULT 순서는 금지 범위를 넓히지 않는 최소안이 적절하다.
- 이견: 위험 완화 표에는 비소유자 PUBLIC-only라는 보수안도 제시됐다. 진행자는 FR-BLOG-02의 실제 열람 범위와 일치하는 viewer→owner ACCEPTED UNIVERSE count를 최종 권고하되 사용자에게 명시한다.

### Architecture — Codex backend

- 권고 APPROVE_WITH_CHANGES / 최종 상세 보고 확신도 92(초기 요약 88) / 위험 HIGH.
- 근거: V1 categories unique 및 posts/universes schema, Category·BaseSoftDeleteEntity·BlogRepository, SecurityConfig GET allowlist, ErrorCode CAT_001~003, FR-CAT-01~05.
- 이점: active-only unique·blog 행 잠금·기존 테이블 SQL로 요구사항을 충족하며 Post/Universe stub과 새 추상화를 피한다.
- 가정: V1 유지/V2 추가, 메타데이터 공개·count 권한 분리, M4 category_id 쓰기가 동일 잠금에 참여한다.
- 위험/필수 보완: `(…,deleted_at)`만으로는 활성 unique가 보장되지 않는다. generated active key와 migration 검증·복구가 필요하다. 잠금 후 상태 재조회, 두 단계 순서 교환, 같은 blog/관계 방향/익명 mutation 차단 검증이 필요하다.
- **중요 지적:** blog lock+ID 집합 검증은 같은 ID 집합의 오래된 reorder를 거부하지 않는다. revision/ETag/snapshot 조건부 요청을 추가하거나 last-write-wins를 명시해야 한다. 진행자는 기존 ID 배열 계약을 유지하는 후자를 제안한다.
- M4 의존/실패: Post 쓰기가 blog lock을 우회하면 category 삭제의 글 이동 직후 삭제 category를 다시 참조할 수 있다. 동일 잠금·활성 재검증 및 bulk flush/context 동기화, delete/create 경쟁 테스트가 필요하다.
- 가장 강한 반론: migration·SQL·잠금·version까지 더하면 CRUD보다 커진다. count/이동을 M4로 미루는 축소안은 간단하지만 FR-CAT-01/04를 줄이므로 별도 결정이 필요하다. 이번에는 version을 추가하지 않는다.

### Delivery & Risk — Codex qa

- 권고 BLOCKED / 확신도 94 / 제안 위험 HIGH.
- 근거: M2 아직 미머지, Q1~Q4 승인 부재, 공개 API·DB·개인정보 영향, FR-CAT/PRD DoD와 실제 V1 제약. 현재 초안을 승인 기록으로 볼 수 없다.
- 필수 보완: Q1 generated active key·실제 MySQL 업그레이드/복구, Q2 잠금/전체 순서/subtree 삭제, Q3 count 권한, Q4 부분 setup 복구를 고정한다. DTO·오류·정본·ADR 및 실제 검증과 M2 머지가 모두 선행해야 한다.
- 위험/실패: nullable deleted_at unique는 활성 중복을 허용하고, 잠금 자식 우회 삭제·private/draft count 누출·setup 재실행은 사용자 데이터/상태를 손상시킨다. 실제 DB·권한 방향·부분 실패·페이지 경계 acceptance로 완화한다.
- 가장 강한 반론: 합의된 단순 부분부터 구현할 수도 있지만 DB/API 계약과 불가역 잠금 정책이 미확정이어서 재작업 위험이 크다. 준비·검토는 계속하고 관련 구현은 보류한다.
- 가정/미확인: 새 migration은 아직 실행하지 않았으며 M2 최종 gate는 별도 확인한다. BLOCKED는 기술 불가능 판정이 아니라 승인·선행 증거 미충족이라는 의미다.

## 6. 진행자 종합

- 최종 등급: **HIGH**(공개 API·보안·DB 및 정책 명확화). QA의 선행 gate 보류 근거는 유효하며 사용자 결정과 M2 종료 전 구현하지 않는다.
- 자동 승인: 불가. 새 안건은 사용자에게 구체적으로 제시하고 승인 전 구현하지 않는다.
- 합의점은 V1 보존, 실제 MySQL 검증, 불변 타입·권한 보호, M4 선행 구현 금지, setup 성공분 보존이다. 다수결 대신 각 위험을 명시하고 Q1~Q4를 사용자에게 요청한다.
- 진행자 최소안: generated active key, 기존 ID 배열+동일 집합 last-write-wins, GET root 페이징+전체 child 및 조회자 열람 범위 count, 기존 setup 뒤 순차 CRUD 복구. 새 라이브러리·version/ETag·Post CRUD·setup API 확장은 도입하지 않는다.

## 7. 결정

- 상태: USER_DECISION_REQUIRED
- 결정: 미확정. 현재 파일은 준비안이며 승인 증거가 아니다.
- 결정 주체/일시: 미정

## 8. 후속 작업

| 작업 | 담당 | 상태 |
| --- | --- | --- |
| M2 검증 완료 및 M3 준비안 취합 | 리더·역할 팀 | 완료(PR #8 dev merge 4c129e2, M3 정책 승인은 별도) |
| 독립 심의 3인 검토 | Codex 검토자 | 완료(사용자 승인은 아님) |
| 정확한 계약과 사용자 결정 요청 | 리더 | 계약 보완 완료·사용자 승인 대기 |
| 승인된 내용의 PRD/REQUIREMENTS/ADR 동기화 | 리더·PM | 승인 전 대기 |
| M3 구현·검증 | backend/frontend/QA | M2 종료·심의 승인 전 대기 |

## 9. 문서 반영

- 현재는 준비안만 작성했다. 확정 후 결정 레지스터·필요 ADR·위험·M3 worklog에 연결한다.

## 10. 정정 기록

승인 이후의 변경은 결론을 덮어쓰지 않고 timestamp와 사유를 append한다.

### 2026-09-06 · 독립 심의 취합 후 미승인 초안 보완

- 상단 REVIEWING과 §7 PROPOSED 혼재를 USER_DECISION_REQUIRED로 통일했다. 어떤 과거 승인도 취소/변경한 것이 아니며 원안은 승인된 적이 없다.
- Q1의 구체 generated-key·복구 방안, Q2 동일 ID 집합의 last-write-wins 한계, Q3 M4 잠금 참여, PUT DEFAULT 멱등 허용과 POST 금지의 모순을 명확히 했다. 변경 이유는 독립 심의 및 진행자의 실제 schema/DTO 대조 결과다. 새 API version 필드는 추가하지 않는다.
- 세 검토자의 필수 보완과 PUBLIC-only 대안 이견을 보존했다. 최종 권고는 여전히 미확정이며 사용자 승인 뒤에만 정본과 구현에 반영한다.
- 05:37 KST: 진행자가 사용자에게 Q1 재사용/복구, Q2 잠금/last-write-wins, Q3 공개 count/DTO/오류, Q4 시작 칩 부분 성공 복구를 각각 비동기 질문으로 제시했다. 아직 응답을 받지 않았으며 권고 선택지가 미리 선택돼 있어도 승인으로 간주하지 않는다.
- M2 선행 gate는 PR #8의 `4c129e2` dev 머지로 해소됐다(GitHub 2026-09-06 05:41:12 KST). M3 자체의 Q1~Q4 승인은 여전히 대기이며 이 기록이 이를 대체하지 않는다. 후속 계획은 `docs/worklog/M3-categories.md`에 연결했다.
