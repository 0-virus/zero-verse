# like controller 경계

POST/DELETE likes는 인증 mutation이고 200 InteractionState를 반환한다. interactions GET은 숫자 postId
공개 경로에서 optional auth를 지원하며 malformed Bearer는 AUTH_004/AUTH_002로 거부한다.
