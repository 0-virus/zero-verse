# notifications feature

알림 API·공유 상태·화면 보조 컴포넌트를 소유한다.

- 공개 계약은 `docs/governance/meetings/M8-20261110-notifications.md` §9.1, §9.3, §9.5와 `docs/PRD.md` §9.11을 따른다.
- `preview`(page 0, size 3)와 센터 page cache를 분리하고, unreadCount는 viewer/account·요청·mutation 세대를 확인한 응답만 반영한다.
- guest·인증 준비 중에는 보호 API를 호출하지 않는다. `canManage=false`는 읽기 전용으로 렌더한다.
- 서버가 반환한 `NotificationItem.href`만 내부 이동에 사용한다. unavailable은 메시지·대상·링크를 노출하지 않는다.
- 이 디렉터리의 모든 컴포넌트는 `min-width:1440px`, radius 0, 디자인 토큰을 유지한다.
