# comment controller 경계

모든 댓글 endpoint는 ApiResponse를 사용한다. GET comments만 optional auth 공개 경로이며 malformed
Bearer를 guest로 강등하지 않는다. create=201, update/delete=200과 승인된 COM/POST/AUTH 오류를 문서화한다.
