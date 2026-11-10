# M7 discovery·M8 notification security 경계

`DiscoveryViewerResolver`는 M7 feed/search에만 적용되는 optional/protected bearer 판정과 현재
사용자 존재 확인을 담당한다. `NotificationViewerResolver`는 M8 보호 경로에서만 strict bearer,
현재 사용자 생존성·SUSPENDED 읽기 전용 상태를 판정한다. `NotificationCacheControlFilter`는
알림 endpoint의 성공·오류 응답에만 `private, no-store`를 부여한다. 기존 M4~M7 인증 정책을
전역으로 바꾸지 않는다.
