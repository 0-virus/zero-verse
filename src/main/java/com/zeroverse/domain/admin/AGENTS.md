# admin 도메인 경계

M9 관리자 사용자 목록·상세·역할/상태 변경·soft delete API를 소유한다. 기존 V1~V6
스키마와 하위 이력을 보존하며 User만 soft delete한다. 모든 endpoint는 DB 현재
`ACTIVE ADMIN nondeleted`를 확인하고 `private, no-store`를 적용한다.

목록·상세는 명시적 REPEATABLE_READ snapshot에서 scalar/batch 조회를 사용한다. mutation은
삭제된 행을 포함한 actor/target User를 ID 오름차순 `FOR UPDATE`로 잠근 뒤 actor부터
현재 권한을 재검증한다. 관리자 bootstrap·실사용 DB 승격·새 migration은 이 경계에 없다.
