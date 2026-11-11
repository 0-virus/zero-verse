# frontend/src/features/admin

M9 관리자 기능은 `REQUIREMENTS.md` §6.13-A, M9 회의 §5·§10/10.1·§11, ADR-0008의 현재 권한·soft delete·DTO 계약을 따른다.

- `AdminAccessProvider`의 목록 `size=1` probe가 현재 DB 권한의 정본이다. `AuthUser.role`만으로 관리자 접근을 확정하지 않는다.
- 민감한 목록·상세·mutation은 인증 loading/probe 중 렌더하지 않는다. guest에는 admin endpoint를 호출하지 않는다.
- `USER_001`은 actor probe 실패와 target not-found를 구분하고, stale account/request/mutation 결과·retry·navigation은 화면에 적용하지 않는다.
- 새 API/의존성/계약 필드는 추가하지 않는다. 화면은 1440px·radius 0·크림 페이퍼 토큰과 Pages 관리자 원본을 따른다.
- `AdminUserTable`은 다른 공통 `Table` 소비자를 변경하지 않고 Pages 원본의 `2fr 1.6fr 1fr 1fr 1fr 90px` track, `gap:10px`, header `11px 20px`, row `13px 20px`를 semantic table 안에 보존한다.
