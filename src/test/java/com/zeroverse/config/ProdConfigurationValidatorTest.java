package com.zeroverse.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.domain.auth.config.AuthCookieProperties;
import com.zeroverse.domain.upload.config.S3StorageProperties;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.security.jwt.JwtProperties;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

class ProdConfigurationValidatorTest {

    @Test
    void acceptsTheSingleHttpsOriginAndVerifiedUtcDbContract() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProdConfigurationValidator validator = validator(environment,
                List.of("https://zeroverse.example"), List.of("https://zeroverse.example"));

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsMixedLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local");
        ProdConfigurationValidator validator = validator(
                environment,
                List.of("https://zeroverse.example"), List.of("https://zeroverse.example"));

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsExtraCorsOriginInSingleOriginProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProdConfigurationValidator validator = validator(
                environment,
                List.of("https://zeroverse.example", "https://other.example"),
                List.of("https://zeroverse.example"));

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsPathBearingCorsOrigin() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProdConfigurationValidator validator = validator(
                environment,
                List.of("https://zeroverse.example/blog"),
                List.of("https://zeroverse.example"));

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsConflictingDuplicateSslModeParameters() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProdConfigurationValidator validator = validator(
                environment,
                List.of("https://zeroverse.example"),
                List.of("https://zeroverse.example"));
        ReflectionTestUtils.setField(
                validator,
                "datasourceUrl",
                "jdbc:mysql://aiven.example/db?sslMode=VERIFY_IDENTITY&sslMode=DISABLED");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUrlTimezoneSessionOverride() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ProdConfigurationValidator validator = validator(
                environment,
                List.of("https://zeroverse.example"),
                List.of("https://zeroverse.example"));
        ReflectionTestUtils.setField(
                validator,
                "datasourceUrl",
                "jdbc:mysql://aiven.example/db?sslMode=VERIFY_IDENTITY"
                        + "&forceConnectionTimeZoneToSession=false");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void initializerValidatesProductionBeforeContextRefresh() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        setValidRawProductionProperties(environment);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);

        assertThatCode(() -> new ProdConfigurationInitializer().initialize(context))
                .doesNotThrowAnyException();
    }

    @Test
    void initializerRejectsS3AttemptTimeoutAboveFiveSeconds() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        setValidRawProductionProperties(environment);
        environment.setProperty("zeroverse.upload.s3.attempt-timeout", "6s");

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProdConfigurationInitializer().initialize(context))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void initializerRejectsAttemptTimeoutLongerThanApiCallTimeout() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        setValidRawProductionProperties(environment);
        environment.setProperty("zeroverse.upload.s3.attempt-timeout", "5s");
        environment.setProperty("zeroverse.upload.s3.api-call-timeout", "4s");

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProdConfigurationInitializer().initialize(context))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ProdConfigurationValidator validator(
            MockEnvironment environment, List<String> cors, List<String> authOrigins) {
        environment
                .withProperty("spring.datasource.url",
                        "jdbc:mysql://aiven.example/db?sslMode=VERIFY_IDENTITY")
                .withProperty("spring.datasource.username", "zeroverse")
                .withProperty("spring.datasource.password", "password")
                .withProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY")
                .withProperty("spring.datasource.hikari.data-source-properties.connectionTimeZone", "UTC")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession", "true")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl",
                        "file:/etc/zeroverse/aiven-truststore.p12")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType",
                        "PKCS12")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword",
                        "truststore-password")
                .withProperty("server.address", "127.0.0.1");
        setValidRawProductionProperties(environment);

        UploadProperties upload = new UploadProperties();
        upload.setBackend("s3");
        S3StorageProperties s3 = new S3StorageProperties();
        s3.setBucket("zero-verse-s3");
        s3.setRegion("ap-northeast-2");
        s3.setPrefix("zeroverse/prod/");
        ProdConfigurationValidator validator = new ProdConfigurationValidator(
                upload,
                s3,
                new CorsProperties(cors),
                new AuthCookieProperties("refresh_token", "/api/v1/auth", true, "Strict", authOrigins),
                new JwtProperties(Base64.getEncoder().encodeToString(new byte[32]), null, null),
                environment);
        ReflectionTestUtils.setField(validator, "publicOrigin", "https://zeroverse.example");
        ReflectionTestUtils.setField(validator, "datasourceUrl", environment.getProperty("spring.datasource.url"));
        ReflectionTestUtils.setField(validator, "datasourceUsername", "zeroverse");
        ReflectionTestUtils.setField(validator, "datasourcePassword", "password");
        ReflectionTestUtils.setField(validator, "viewerHmacSecret", "01234567890123456789012345678901");
        return validator;
    }

    private static void setValidRawProductionProperties(MockEnvironment environment) {
        environment
                .withProperty("zeroverse.upload.backend", "s3")
                .withProperty("zeroverse.upload.s3.bucket", "zero-verse-s3")
                .withProperty("zeroverse.upload.s3.region", "ap-northeast-2")
                .withProperty("zeroverse.upload.s3.prefix", "zeroverse/prod/")
                .withProperty("zeroverse.upload.s3.endpoint", "")
                .withProperty("zeroverse.upload.s3.path-style", "false")
                .withProperty("zeroverse.upload.s3.attempt-timeout", "5s")
                .withProperty("zeroverse.upload.s3.api-call-timeout", "10s")
                .withProperty("zeroverse.public-origin", "https://zeroverse.example")
                .withProperty("zeroverse.cors.allowed-origins", "https://zeroverse.example")
                .withProperty(
                        "zeroverse.auth.cookie.allowed-origins", "https://zeroverse.example")
                .withProperty("zeroverse.auth.cookie.secure", "true")
                .withProperty("zeroverse.auth.cookie.same-site", "Strict")
                .withProperty("spring.datasource.url", "jdbc:mysql://aiven.example/db?sslMode=VERIFY_IDENTITY")
                .withProperty("spring.datasource.username", "zeroverse")
                .withProperty("spring.datasource.password", "password")
                .withProperty("spring.datasource.hikari.data-source-properties.sslMode", "VERIFY_IDENTITY")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.connectionTimeZone", "UTC")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession",
                        "true")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl",
                        "file:/etc/zeroverse/aiven-truststore.p12")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType",
                        "PKCS12")
                .withProperty(
                        "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword",
                        "truststore-password")
                .withProperty(
                        "zeroverse.jwt.secret-base64",
                        Base64.getEncoder().encodeToString(new byte[32]))
                .withProperty(
                        "zeroverse.upload.viewer-hmac-secret",
                        "01234567890123456789012345678901")
                .withProperty("server.address", "127.0.0.1");
    }
}
