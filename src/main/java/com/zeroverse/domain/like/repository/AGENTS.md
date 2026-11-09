# like repository 경계

post/user 기준 exists·count·hard delete를 제공한다. like count는 soft-deleted user를 제외하며 unique
constraint는 V1과 DB를 최종 방어선으로 유지한다.
