# M7 discovery·M8 notification security 경계

`DiscoveryViewerResolver`는 M7 feed/search에만 적용되는 optional/protected bearer 판정과 현재
사용자 존재 확인을 담당한다. `NotificationViewerResolver`는 M8 보호 경로에서만 strict bearer,
현재 사용자 생존성·SUSPENDED 읽기 전용 상태를 판정한다. `NotificationCacheControlFilter`는
알림 endpoint의 성공·오류 응답에만 `private, no-store`를 부여한다. 기존 M4~M7 인증 정책을
전역으로 바꾸지 않는다.

## M9 관리자 인증

`AdminCacheControlFilter`는 admin 성공·검증·보안 오류 모두 `private, no-store`로 응답한다.
admin 경로는 유효 ACCESS 인증 후 `AdminService`가 DB 현재 ACTIVE ADMIN·생존성을 판정한다.
claim-only ADMIN gate를 추가하면 승격 전 토큰의 정상 접근을 막으므로 사용하지 않는다.
서명된 ACCESS의 누락/잘못된 role·subject(양수 ID 이외)는 `AUTH_004`로 처리한다.
