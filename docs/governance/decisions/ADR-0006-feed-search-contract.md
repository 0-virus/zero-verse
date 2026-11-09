# ADR-0006: M7 피드·검색과 파생 본문 캐시

- 상태: `ACCEPTED`
- 결정일: 2026-11-09
- 결정 주체: 사용자 A-v2 전체 승인 및 `승인된 작업 시작해.` 재개
- 관련 회의: [M7 A-v2 §6](../meetings/M7-20261109-feed-search.md)
- 관련 마일스톤: [M7](../../worklog/M7-feed-search.md)
- 대체한 ADR: 없음

## 맥락

FR-FEED-01~02와 FR-SEARCH-01은 글 접근권한을 적용한 피드와 게시글 중심 검색을 요구한다. raw HTML 문자열 검색은 `amp`가 `&amp;`에 매칭되고 inline mark로 나뉜 `spring`을 놓칠 수 있다. 검색 결과와 탭별 건수도 같은 권한과 데이터 시점으로 계산해야 한다.

## 결정

1. 로그인 메인은 universe 피드, guest는 public 피드다. universe `all`은 outgoing ACCEPTED 전체, `friends`는 양방향, `following`은 친구를 제외한 outgoing이다. 기존 관계 목록·현황 following은 친구 포함 계약을 유지한다.
2. 검색 BLOG/USER/TAG는 게시글 검색 범위다. ALL은 distinct post 합집합이며 범위별 count는 겹칠 수 있다. 반환 데이터와 counts/metadata는 readOnly+REPEATABLE_READ snapshot에서 계산한다. QueryDSL DB 필터 뒤 paging하고 선택된 page IDs로 태그·상호작용 수를 일괄 조회한다.
3. V4는 nullable `posts.content_text LONGTEXT`를 추가하고 V5 Java migration은 canonical TipTap JSON의 평문을 keyset100으로 채운다. 고정된 `PostTextExtractorV1`을 migration·writer·offline maintenance가 공유한다. 기존 V1~V3 및 JSON/HTML 원본은 유지한다.
4. V5 DML은 전체 단일 transaction으로 실패 시 rollback한다. `updated_at`을 비롯한 원문·게시·삭제·조회·감사값을 보존하고 corrupt JSON을 건너뛰거나 HTML로 대체하지 않는다. 빈 doc은 유효하다. Flyway 실패·NULL cache는 M7 읽기 준비 완료를 막는다.
5. create/update에서 캐시를 함께 갱신한다. 구버전 writer 복귀 후 재도입은 offline verify/rebuild 및 NULL/stale0 확인을 요구한다. 공개 관리 API나 외부 검색엔진은 추가하지 않는다.
6. 인증 상태표·입력/오류·응답 필드·facet/링크·160-code-point excerpt·FE stale 처리·복구 도구의 정확한 계약은 회의 §6과 REQUIREMENTS §6.10-A를 따른다. 이 ADR은 별도 새 권한이나 범위를 추가하지 않는다.

## 근거와 결과

정확한 텍스트 검색과 DB 수준의 paging/count를 함께 보장한다. 기존 QueryDSL/MySQL/Flyway를 사용해 외부 운영 의존성을 늘리지 않는다. 반면 파생 필드 동기화와 기존 행 이관, 구버전 writer 이후의 캐시 재구축 비용이 생긴다. LONGTEXT substring 검색의 데이터 규모별 성능과 FULLTEXT/외부 색인은 후속 판단 대상이며 의미를 임의 변경하지 않는다.

## 대안

| 대안 | 채택하지 않은 이유 |
|---|---|
| SQL 태그 제거 + HTML-escaped keyword | 엔티티 오탐·inline 단어 경계 누락을 해결하지 못함 |
| 애플리케이션에서 전체 글 필터 | 메모리·paging·건수·권한 일관성 요구를 만족하지 못함 |
| FULLTEXT/외부 검색엔진 | substring·한국어 검색 의미와 운영 범위를 함께 바꾸므로 M7에서 제외 |

## 검증 방법

MySQL 빈 DB/V3 upgrade·100/101/201/gap/invalid/rollback/재실행/backup-restore·audit 보존, extractor 경계, 모든 writer/maintenance, ACL·facet distinct·동시 writer snapshot·query bound를 자동 검증한다. 실제 immutable JAR HTTP와 Chrome1440px에서 피드/검색·계정 전환·URL·권한·원본 디자인을 독립 QA한다. 구현·검증 완료 여부는 M7 worklog이며 이 문서의 ACCEPTED는 완료를 뜻하지 않는다.

## 롤백 또는 대체 조건

컬럼을 삭제하거나 원문·사용자 DB를 초기화하지 않는다. 구버전 writer가 실행되면 cache 정합성을 보장하지 않으며, M7 재활성화 전에 writer 정지와 offline 검증/재구축이 필요하다. 사용자 DB repair·원문 수정·복원은 별도 승인이다. 기존 extractor의 의미를 바꾸려면 새 버전과 forward migration으로 심의한다.
