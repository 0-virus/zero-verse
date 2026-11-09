# comment DTO 경계

CommentItem은 승인된 최소 author DTO와 content/deleted/can* 상태를 제공한다. HTML을 저장하거나
렌더링하지 않고 plain text를 trim·검증한다. `replies`는 root의 PageResponse, leaf는 null이다.
