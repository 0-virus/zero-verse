# notification DTO 경계

NotificationPage는 기존 7개 PageResponse 필드에 unreadCount/canManage를 추가한다. unavailable
알림은 고정 안내만 노출하고 actor·targetId·href를 null로 masking하며 저장 message·제목·본문을
반환하지 않는다. 시간은 기존 HTTP DTO와 동일하게 UTC OffsetDateTime으로 표현한다.
