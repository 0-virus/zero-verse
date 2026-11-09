# M7 discovery security 경계

`DiscoveryViewerResolver`는 M7 feed/search에만 적용되는 optional/protected bearer 판정과 현재
사용자 존재 확인을 담당한다. 기존 M4~M6 인증 정책을 전역으로 바꾸지 않는다.
