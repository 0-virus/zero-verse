# Search controller 경계

`keyword`는 누락·blank를 명시적으로 `VALIDATION_001`로 거부한다. type은 대문자,
sort는 소문자 enum만 허용하고 optional auth malformed bearer를 guest로 강등하지 않는다.
