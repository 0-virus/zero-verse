# notification repository 경계

목록은 인증 user·visible count·unread count·actor/target scalar projection을 batch로 조회해
source LONGTEXT를 로딩하지 않는다. hidden-by-block은 page/count보다 먼저 native predicate로
제외하고, receiver lock 이후 read-all 조건부 UPDATE와 notification row lock을 제공한다.
