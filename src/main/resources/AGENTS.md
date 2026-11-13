# backend resources 경계

`application.yml`은 local/test 기본값과 환경 변수 이름을 보존하고, `application-prod.yml`은 운영
환경 변수만 받는다. `META-INF/spring.factories`의 `ProdConfigurationInitializer` 등록은 prod 설정을
DataSource/Flyway 이전에 검증하기 위한 런타임 계약이므로 제거하거나 늦은 bean 등록으로 대체하지 않는다.

`db/migration`은 V1을 수정하지 않고 forward migration만 추가한다. `application-local.yml`은
gitignore 대상이며 bootJar에 포함하지 않는다.
