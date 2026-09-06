# ADR-0005: M3 카테고리 무결성·조회·편집·초기 설정 계약

- 상태: `ACCEPTED`
- 날짜: 2026-09-06
- 등급: `HIGH`
- 결정 주체: 사용자
- 근거: [M3 회의](../meetings/M3-20260906-categories.md) §4 Q1~Q4, REQUIREMENTS FR-CAT-01~05·FR-BLOG-02·FR-SETTINGS-04·NFR-04~09, PRD §5.4·§7·§9·§10~12.
- 승인 증거: 리더가 Q1 활성 unique, Q2 불변/순서, Q3 count/API, Q4 setup 복구의 권고와 상세 회의록을 제시하고 결정을 기다린 뒤, 사용자가 **"시작"**이라고 응답했다. 리더는 바로 앞의 구체 Q1~Q4 권고안으로 진행하라는 지시로 기록했다. 과거 `$project-lead` 호출이나 포괄 위임을 승인 근거로 사용하지 않는다. 다른 미결 정책과 M4 신규 안건은 포함하지 않는다.

## 결정

### Q1 — 삭제 이력 보존과 값 재사용

V1을 보존하고 V2 forward migration으로 `active_key AS (CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END) STORED`를 추가한다. 기존 parent_key와 함께 `(blog_id,parent_key,name,active_key)`, `(blog_id,parent_key,display_order,active_key)` unique를 둔다. JPA는 generated column을 쓰지 않는다. 활성 형제의 중복은 거부하고 삭제한 이름·순서는 재사용한다. 모든 카테고리 쓰기는 blog 행 잠금 이후 상태를 재조회한다.

V2 전 기존 데이터·제약, 적용 후 기존 행 보존과 활성 제약을 검증한다. 재사용 이력이 생긴 뒤 V1 제약으로 단순 복구하지 않는다. 문제 시 카테고리 쓰기를 중단하고 호환 앱 복귀 또는 별도 forward repair로 복구한다. 실제 운영 데이터 삭제/복구는 별도 승인 대상이다.

### Q2 — 불변 타입·부모·정렬

DEFAULT는 이름·타입·삭제 불가, 순서 변경 가능이다. LOCKED는 이름·타입·숫자 순서·삭제 불가다. 같은 값의 멱등 PUT은 허용한다. GENERAL은 GENERAL/LOCKED로 설정할 수 있고 LOCKED 전환 전에 되돌릴 수 없음을 안내한다. DEFAULT는 시스템 전용이며 부모 선택은 생성 때만 지원한다. GENERAL subtree에 불변 자식이 있으면 전체 삭제를 거부한다.

순서는 같은 부모의 활성 ID 전체를 정확히 한 번 받는다. 기존 정렬된 displayOrder 값들을 요청 ID 순서로 배정하고 LOCKED 값은 보존한다. 간격을 허용하며 삭제 때 재번호하지 않는다. unique 교환은 같은 트랜잭션에서 임시 음수→flush→최종 비음수의 두 단계로 수행한다. 같은 ID 집합에서는 마지막 요청이 적용되는 last-write-wins이며 생성/삭제로 집합이 바뀐 요청은 거부한다. revision/ETag는 추가하지 않는다.

### Q3 — 실제 count·이동·공개 API

정확한 경로/입력/DTO/상태 코드는 회의 §4의 계약 표와 REQUIREMENTS §6.4에 따른다. 공통 ApiResponse/PageResponse를 사용한다. 공개 GET만 method 제한으로 허용하고 쓰기는 소유자만 허용한다. 목록은 루트 단위 페이지 + 직속 children 전체, 각 postCount는 직접 소속 글 수다. FE는 모든 루트 페이지 로드 후 쓰기를 활성화한다.

삭제 post는 count에서 제외한다. 비로그인은 PUBLIC 발행 글, 로그인 비소유자는 PUBLIC과 viewer→owner ACCEPTED 관계의 UNIVERSE 발행 글, 소유자는 모든 발행 글을 센다. `includeDrafts=true`는 소유자만 허용하고 비소유자 요청은 CAT_004다. 카테고리 이름/계층은 공개되며 LOCKED는 편집 잠금이다.

실제 posts/universes SQL을 사용하며 Post/Universe CRUD를 앞당기거나 0 count stub을 만들지 않는다. 삭제 subtree의 모든 posts(soft-deleted 포함)를 같은 블로그의 활성 DEFAULT로 옮긴다. bulk 전 flush/후 persistence context 동기화를 보장한다. M4 category_id를 쓰는 모든 경로도 같은 blog lock과 활성·동일 블로그 재검증에 참여한다.

기존 CAT_001~003은 유지한다. CAT_004=403 권한 없음, CAT_005=409 이름 중복, CAT_006=400 변경 불가, CAT_007=400 순서 오류이며 상세 메시지는 REQUIREMENTS NFR-04에 고정한다. 기본 검증은 VALIDATION_001이다. 경로 블로그/소유자가 없거나 삭제됐으면 BLOG_001, 경로에 속하지 않거나 삭제된 category/parent는 CAT_001, 미인증 쓰기는 AUTH_004다.

UI는 단일 카드·인라인 편집·들여쓰기·native drag/키보드를 사용한다. 삭제 안내의 디자인 '전체'는 실제 정책인 **'미분류'**로 override한다. Post 목록 필터의 실제 API 연동은 M4다.

### Q4 — 초기 설정 시작 카테고리

칩은 루트 GENERAL 이름이다. 기존 initial-setup 성공 후 전체 GET→누락 이름만 순차 POST→완료 이동한다. 이름 중복·응답 유실·부분 실패는 GET으로 성공분을 확인하고 남은 것만 재시도한다. 완료된 initial-setup을 반복하거나 성공분을 삭제하지 않는다. 새로고침 뒤에도 setup 완료를 유지하며 카테고리 관리에서 확인/추가할 수 있게 안내한다. 새 일괄 API나 setup 입력 필드는 추가하지 않는다.

## 검증·반대 논거·후속

- 실제 MySQL V1→V2 기존행 보존, 활성 unique/삭제 후 재사용, 생성·정렬·삭제 경쟁과 LOCKED/subtree 우회 차단을 검증한다.
- 101개 이상 루트/children 페이지 경계, 비로그인·관계 방향·소유자·draft count, 실제 post 이동 및 HTTP/OpenAPI 계약을 검증한다.
- FE 전체 로드 전 쓰기 차단, 키보드/drag, 잠금 안내, setup 부분 실패·응답 유실·새로고침 복구와 1440px 디자인을 검증한다. 최종 검토는 구현자와 분리한다.
- 반대 논거: migration/잠금/SQL은 CRUD 규모를 늘리고 last-write-wins는 같은 집합의 오래된 편집을 막지 못한다. 새 version/라이브러리/Post CRUD를 추가하지 않는 범위에서 데이터 무결성과 명시된 한계를 테스트한다.
- V2 재사용 후 복구와 M4 blog lock 참여는 [위험 레지스터](../RISK-REGISTER.md)로 이관한다. 실제 M4 S3 입력 및 기타 M4 정책은 이 승인에 포함되지 않는다.
