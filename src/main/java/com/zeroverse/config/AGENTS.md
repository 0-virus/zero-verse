# backend runtime configuration 경계

`ProdConfigurationInitializer`는 Spring Boot `META-INF/spring.factories` 등록을 통해 active `prod` raw
environment를 DataSource/Flyway bean 생성 및 context refresh보다 먼저 검사한다. UTC JVM, HTTPS 단일
origin, loopback bind, TLS `VERIFY_IDENTITY`/외부 truststore, secret, S3 location과 JDBC URL 중복·반대
옵션을 거부한다. `ProdConfigurationValidator`의 component 검증은 이후 defense-in-depth다.

`ProdUploadPreflight`는 prod DB의 기존 `LOCAL` image row를 자동 이관하지 않고 기동을 중단한다.
`ReadinessController`는 Boot readiness가 `ACCEPTING_TRAFFIC`이고 DB `SELECT 1`이 성공할 때만 `UP`을
반환하며 reverse proxy가 외부 health 경로를 차단한다.

`SecurityConfig.filterChain`은 Servlet 웹 애플리케이션에서만 생성한다. `UploadIntentCleanup` 같은
`WebApplicationType.NONE` 유지보수 CLI가 `HttpSecurity`를 요구하지 않도록 하는 경계이며,
`PasswordEncoder` 등 비-HTTP 보안 bean은 CLI에서도 유지한다.
