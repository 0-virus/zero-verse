package com.zeroverse.config;

import com.zeroverse.domain.auth.config.AuthCookieProperties;
import com.zeroverse.domain.upload.config.S3StorageProperties;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.security.jwt.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Fail-fast production boundary; values are checked without logging secrets. */
@Component
@Profile("prod")
public class ProdConfigurationValidator {

    private final UploadProperties uploadProperties;
    private final S3StorageProperties s3Properties;
    private final CorsProperties corsProperties;
    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;
    private final Environment environment;

    @Value("${zeroverse.public-origin:}")
    private String publicOrigin;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${zeroverse.upload.viewer-hmac-secret:}")
    private String viewerHmacSecret;

    public ProdConfigurationValidator(
            UploadProperties uploadProperties,
            S3StorageProperties s3Properties,
            CorsProperties corsProperties,
            AuthCookieProperties cookieProperties,
            JwtProperties jwtProperties,
            Environment environment) {
        this.uploadProperties = uploadProperties;
        this.s3Properties = s3Properties;
        this.corsProperties = corsProperties;
        this.cookieProperties = cookieProperties;
        this.jwtProperties = jwtProperties;
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("local".equals(profile) || "test".equals(profile)) {
                fail();
            }
        }
        if (!ZoneId.systemDefault().normalized().equals(ZoneOffset.UTC)) {
            fail();
        }
        if (!uploadProperties.isS3()
                || blank(s3Properties.getBucket())
                || blank(s3Properties.getRegion())
                || blank(s3Properties.getPrefix())
                || !s3Properties.getPrefix().startsWith("zeroverse/")
                || !s3Properties.getPrefix().endsWith("/")
                || s3Properties.getPrefix().contains("..")
                || s3Properties.isPathStyle()
                || !validS3Timeouts(s3Properties.getAttemptTimeout(), s3Properties.getApiCallTimeout())) {
            fail();
        }
        URI origin = parseHttpsOrigin(publicOrigin);
        if (!containsOnlyOrigin(corsProperties.allowedOrigins(), origin)
                || !containsOnlyOrigin(cookieProperties.allowedOrigins(), origin)) {
            fail();
        }
        if (!Boolean.TRUE.equals(cookieProperties.secure())
                || !"strict".equalsIgnoreCase(cookieProperties.sameSite())) {
            fail();
        }
        String configuredSslMode = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.sslMode", "");
        String connectionTimeZone = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.connectionTimeZone", "");
        String forceSessionTimeZone = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession", "");
        boolean urlHasConflictingSslMode = !hasAcceptedSingleParameter(
                datasourceUrl, "sslmode", "verify_identity");
        boolean urlHasConflictingLegacyTls = !hasAcceptedSingleParameter(
                        datasourceUrl, "usessl", "true")
                || !hasAcceptedSingleParameter(
                        datasourceUrl, "verifyservercertificate", "true")
                || !hasAcceptedSingleParameter(datasourceUrl, "servertimezone", "utc")
                || !hasAcceptedSingleParameter(datasourceUrl, "connectiontimezone", "utc")
                || !hasAcceptedSingleParameter(
                        datasourceUrl, "forceconnectiontimezonetosession", "true");
        if (blank(datasourceUrl)
                || urlHasConflictingSslMode
                || urlHasConflictingLegacyTls
                || !"verify_identity".equalsIgnoreCase(configuredSslMode)
                || !"UTC".equalsIgnoreCase(connectionTimeZone)
                || !Boolean.parseBoolean(forceSessionTimeZone)
                || blank(datasourceUsername)
                || blank(datasourcePassword)
                || !validTruststore(
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl"),
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType"),
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword"))) {
            fail();
        }
        if (blank(jwtProperties.secretBase64())
                || blank(viewerHmacSecret)
                || viewerHmacSecret.getBytes(StandardCharsets.UTF_8).length < 32
                || !validJwtSecret(jwtProperties.secretBase64())) {
            fail();
        }
        String address = environment.getProperty("server.address", "127.0.0.1");
        if (!"127.0.0.1".equals(address) && !"localhost".equalsIgnoreCase(address)) {
            fail();
        }
        String endpoint = s3Properties.getEndpoint();
        if (!blank(endpoint)) {
            fail();
        }
    }

    /**
     * Validate raw production configuration before the application context refreshes.
     *
     * <p>This is called by {@link ProdConfigurationInitializer}; the component-level validation
     * above remains as a defense-in-depth check against a later property mutation.
     */
    static void validateEnvironment(Environment environment) {
        if (!hasProfile(environment, "prod")) {
            return;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("local".equals(profile) || "test".equals(profile)) {
                fail();
            }
        }
        if (!ZoneId.systemDefault().normalized().equals(ZoneOffset.UTC)) {
            fail();
        }

        String backend = environment.getProperty("zeroverse.upload.backend");
        String bucket = environment.getProperty("zeroverse.upload.s3.bucket");
        String region = environment.getProperty("zeroverse.upload.s3.region");
        String prefix = environment.getProperty("zeroverse.upload.s3.prefix");
        String endpoint = environment.getProperty("zeroverse.upload.s3.endpoint", "");
        String pathStyle = environment.getProperty("zeroverse.upload.s3.path-style", "false");
        Duration attemptTimeout = configuredDuration(
                environment, "zeroverse.upload.s3.attempt-timeout");
        Duration apiCallTimeout = configuredDuration(
                environment, "zeroverse.upload.s3.api-call-timeout");
        if (!"s3".equalsIgnoreCase(backend)
                || unresolved(bucket)
                || unresolved(region)
                || unresolved(prefix)
                || !prefix.startsWith("zeroverse/")
                || !prefix.endsWith("/")
                || prefix.contains("..")
                || !blank(endpoint)
                || !"false".equalsIgnoreCase(pathStyle)
                || !validS3Timeouts(attemptTimeout, apiCallTimeout)) {
            fail();
        }

        URI origin = parseHttpsOrigin(environment.getProperty("zeroverse.public-origin"));
        if (!containsOnlyOrigin(
                        configuredOrigins(environment.getProperty("zeroverse.cors.allowed-origins")), origin)
                || !containsOnlyOrigin(
                        configuredOrigins(environment.getProperty("zeroverse.auth.cookie.allowed-origins")), origin)) {
            fail();
        }

        if (!"true".equalsIgnoreCase(
                        environment.getProperty("zeroverse.auth.cookie.secure", ""))
                || !"strict".equalsIgnoreCase(
                        environment.getProperty("zeroverse.auth.cookie.same-site", ""))) {
            fail();
        }

        String url = environment.getProperty("spring.datasource.url");
        String sslMode = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.sslMode");
        String connectionTimeZone = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.connectionTimeZone");
        String forceSessionTimeZone = environment.getProperty(
                "spring.datasource.hikari.data-source-properties.forceConnectionTimeZoneToSession");
        if (unresolved(url)
                || !hasAcceptedSingleParameter(url, "sslmode", "verify_identity")
                || !hasAcceptedSingleParameter(url, "usessl", "true")
                || !hasAcceptedSingleParameter(url, "verifyservercertificate", "true")
                || !hasAcceptedSingleParameter(url, "servertimezone", "utc")
                || !hasAcceptedSingleParameter(url, "connectiontimezone", "utc")
                || !hasAcceptedSingleParameter(
                        url, "forceconnectiontimezonetosession", "true")
                || !"verify_identity".equalsIgnoreCase(sslMode)
                || !"UTC".equalsIgnoreCase(connectionTimeZone)
                || !"true".equalsIgnoreCase(forceSessionTimeZone)
                || unresolved(environment.getProperty("spring.datasource.username"))
                || unresolved(environment.getProperty("spring.datasource.password"))
                || !validTruststore(
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreUrl"),
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStoreType"),
                        environment.getProperty(
                                "spring.datasource.hikari.data-source-properties.trustCertificateKeyStorePassword"))) {
            fail();
        }

        if (!validJwtSecret(environment.getProperty("zeroverse.jwt.secret-base64"))
                || unresolved(environment.getProperty("zeroverse.upload.viewer-hmac-secret"))
                || environment.getProperty("zeroverse.upload.viewer-hmac-secret").getBytes(StandardCharsets.UTF_8).length
                        < 32) {
            fail();
        }
        String address = environment.getProperty("server.address", "127.0.0.1");
        if (!"127.0.0.1".equals(address) && !"localhost".equalsIgnoreCase(address)) {
            fail();
        }
    }

    private static URI parseHttpsOrigin(String value) {
        URI uri = parseOriginOrNull(value);
        if (uri == null) {
            fail();
        }
        return uri;
    }

    private static boolean containsOnlyOrigin(List<String> values, URI expected) {
        if (values == null || values.size() != 1 || expected == null) {
            return false;
        }
        URI candidate = parseOriginOrNull(values.get(0));
        return candidate != null && originKey(expected).equals(originKey(candidate));
    }

    private static boolean isLocalHost(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("\\.$", "");
        return "localhost".equals(normalized)
                || normalized.endsWith(".localhost")
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || normalized.startsWith("127.")
                || normalized.startsWith("[::1]");
    }

    private static String originKey(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443)
                || ("http".equalsIgnoreCase(uri.getScheme()) && port == 80);
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://"
                + uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "")
                + (defaultPort ? "" : ":" + port);
    }

    private static boolean validTruststore(String value, String type, String password) {
        return !unresolved(value)
                && value.startsWith("file:")
                && ("PKCS12".equalsIgnoreCase(type) || "JKS".equalsIgnoreCase(type))
                && !unresolved(password);
    }

    private static boolean validS3Timeouts(Duration attemptTimeout, Duration apiCallTimeout) {
        return attemptTimeout != null
                && apiCallTimeout != null
                && !attemptTimeout.isZero()
                && !attemptTimeout.isNegative()
                && !apiCallTimeout.isZero()
                && !apiCallTimeout.isNegative()
                && attemptTimeout.compareTo(Duration.ofSeconds(5)) <= 0
                && apiCallTimeout.compareTo(Duration.ofSeconds(10)) <= 0
                && attemptTimeout.compareTo(apiCallTimeout) <= 0;
    }

    private static Duration configuredDuration(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (unresolved(value)) {
            return null;
        }
        try {
            return DurationStyle.detectAndParse(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean validJwtSecret(String value) {
        if (unresolved(value)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(value).length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean unresolved(String value) {
        return blank(value) || value.contains("${");
    }

    private static boolean hasProfile(Environment environment, String expected) {
        for (String profile : environment.getActiveProfiles()) {
            if (expected.equals(profile)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> configuredOrigins(String value) {
        if (unresolved(value)) {
            return List.of();
        }
        List<String> origins = new ArrayList<>();
        for (String item : value.split(",", -1)) {
            origins.add(item.trim());
        }
        return origins;
    }

    private static URI parseOriginOrNull(String value) {
        if (unresolved(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            String path = uri.getRawPath();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || blank(host)
                    || uri.getUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !"/".equals(path))
                    || isLocalHost(host)) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean hasAcceptedSingleParameter(String url, String key, String expected) {
        List<String> values = queryParameterValues(url, key);
        return values.isEmpty()
                || (values.size() == 1 && expected.equalsIgnoreCase(values.get(0)));
    }

    private static List<String> queryParameterValues(String url, String expectedKey) {
        if (blank(url)) {
            return List.of();
        }
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return List.of();
        }
        int fragmentStart = url.indexOf('#', queryStart + 1);
        String query = url.substring(queryStart + 1, fragmentStart < 0 ? url.length() : fragmentStart);
        Map<String, List<String>> parameters = new HashMap<>();
        for (String part : query.split("&", -1)) {
            if (part.isEmpty()) {
                continue;
            }
            int separator = part.indexOf('=');
            String rawKey = separator < 0 ? part : part.substring(0, separator);
            String rawValue = separator < 0 ? "" : part.substring(separator + 1);
            String decodedKey = decodeQueryPart(rawKey).toLowerCase(Locale.ROOT);
            String decodedValue = decodeQueryPart(rawValue);
            parameters.computeIfAbsent(decodedKey, ignored -> new ArrayList<>()).add(decodedValue);
        }
        return parameters.getOrDefault(expectedKey.toLowerCase(Locale.ROOT), Collections.emptyList());
    }

    private static String decodeQueryPart(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "#malformed#";
        }
    }

    private static void fail() {
        throw new IllegalStateException("production configuration preflight failed");
    }
}
