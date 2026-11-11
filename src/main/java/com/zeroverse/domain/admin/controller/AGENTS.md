# admin controller 경계

`/api/v1/admin/users`의 5개 API만 노출한다. 공통 `ApiResponse`, 정확한 A-v2 DTO,
검증 오류 `VALIDATION_001`, 관리자 오류 `ADMIN_001/002`, 사용자 오류 `USER_001/003`을
사용하며 성공·오류 모두 `private, no-store`여야 한다.
