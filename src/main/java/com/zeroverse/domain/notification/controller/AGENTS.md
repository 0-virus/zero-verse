# notification controller 경계

모든 알림 endpoint는 Bearer 보호·ApiResponse·private no-store를 사용한다. 목록은 all/unread/read
필터와 최대 100 offset page를 사용하고, 개별 read만 canonical NotificationItem을 반환하며
read-all/delete는 empty success를 반환한다. 오류는 승인된 AUTH/USER/NOT/VALIDATION 코드만 문서화한다.
