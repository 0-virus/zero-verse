package com.zeroverse.u0;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.tika.Tika;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectOwnership;
import software.amazon.awssdk.services.s3.model.OwnershipControls;
import software.amazon.awssdk.services.s3.model.OwnershipControlsRule;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.PublicAccessBlockConfiguration;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetPublicAccessBlockRequest;
import software.amazon.awssdk.services.s3.model.GetBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.GetBucketOwnershipControlsRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/**
 * U0-only S3 verification harness.
 *
 * <p>This is intentionally separate from the product application. It creates one
 * unique private test bucket per process, keeps presigned URLs in memory, and
 * exposes only the fixed local browser-manifest endpoints.</p>
 */
public final class U0Harness implements AutoCloseable {
    private static final String HARNESS_ORIGIN = "http://127.0.0.1:14567";
    private static final String HARNESS_HOST = "127.0.0.1:14567";
    private static final int HARNESS_PORT = 14567;
    private static final Region REGION = Region.US_EAST_1;
    private static final String ACCESS_KEY = "test";
    private static final String SECRET_KEY = "test";
    private static final long MAX_BYTES = 5_242_880L;
    private static final Duration PUT_SIGNATURE_TTL = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final DateTimeFormatter BUCKET_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private enum Provider {
        LOCALSTACK("LocalStack", "http://127.0.0.1:14566"),
        SEAWEEDFS("SeaweedFS", "http://127.0.0.1:14568");

        private final String label;
        private final URI endpoint;

        Provider(String label, String endpoint) {
            this.label = label;
            this.endpoint = URI.create(endpoint);
        }

        private static Provider fromArgs(String[] args) {
            return Arrays.asList(args).contains("--seaweedfs") ? SEAWEEDFS : LOCALSTACK;
        }
    }

    private final Provider provider;
    private final URI s3Endpoint;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final HttpClient http;
    private final Tika tika = new Tika();
    private final String bucket;
    private final Path repoRoot;
    private final Map<String, Fixture> fixtures;
    private final Fixture png;
    private final Fixture jpeg;
    private final Map<String, UploadPlan> uploadPlans = new LinkedHashMap<>();
    private final List<CaseEntry> cases = new ArrayList<>();
    private final String preseedKey;
    private final byte[] preseedBytes;
    private final String preseedChecksum;
    private URI signedGetUrl;
    private URI invalidSignatureUrl;
    private URI expiryUrl;
    private Instant expiryAt;
    private boolean casesBuilt;
    private HttpServer server;

    private record Fixture(String id, String fileName, String contentType, byte[] bytes) {
    }

    private enum PlanKind {
        VALID,
        MIME_SPOOF,
        EXPECT_REJECTED,
        REPLAY_FIRST,
        REPLAY_SECOND
    }

    private record UploadPlan(
            String id,
            String key,
            URI url,
            Map<String, String> headers,
            byte[] body,
            byte[] expectedBytes,
            String declaredType,
            String expectedChecksum,
            PlanKind kind) {
    }

    private record CaseEntry(
            String id,
            String method,
            URI url,
            Map<String, String> headers,
            String bodyBase64,
            String contentType,
            List<Integer> expectedStatuses,
            Integer waitMs) {
    }

    private record Check(String id, boolean passed, String detail) {
    }

    private record BoundedObject(byte[] bytes, String detectedType) {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Provider provider = Provider.fromArgs(args);
        U0Harness harness = new U0Harness(provider);
        Runtime.getRuntime().addShutdownHook(new Thread(harness::close, "u0-shutdown"));
        harness.startServer();
        System.out.println("U0 harness ready: " + HARNESS_ORIGIN);
        System.out.println("U0 provider=" + provider.label + "; S3 endpoint fixed to loopback "
                + provider.endpoint.getHost() + ":" + provider.endpoint.getPort()
                + "; signed URL/token values are not printed.");

        if (Arrays.asList(args).contains("--self-check")) {
            int exit = harness.runSelfCheck();
            harness.close();
            if (exit != 0) {
                System.exit(exit);
            }
            return;
        }

        synchronized (harness) {
            harness.wait();
        }
    }

    public U0Harness() throws Exception {
        this(Provider.LOCALSTACK);
    }

    private U0Harness(Provider provider) throws Exception {
        this.provider = provider;
        this.s3Endpoint = provider.endpoint;
        requireFixedLoopbackEndpoint(provider);
        this.repoRoot = findRepoRoot();
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY));
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        this.s3 = S3Client.builder()
                .region(REGION)
                .endpointOverride(s3Endpoint)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        this.presigner = S3Presigner.builder()
                .region(REGION)
                .endpointOverride(s3Endpoint)
                .credentialsProvider(credentials)
                .serviceConfiguration(s3Configuration)
                .build();
        this.fixtures = buildFixtures();
        this.png = fixtures.get("png");
        this.jpeg = fixtures.get("jpeg");
        this.bucket = createPrivateBucket();
        this.preseedBytes = png.bytes();
        this.preseedChecksum = sha256Base64(preseedBytes);
        this.preseedKey = "u0/preseed-" + UUID.randomUUID() + ".png";
        putPreseedObject();
    }

    private void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", HARNESS_PORT), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/cases", this::handleCases);
        server.createContext("/verify", this::handleVerify);
        ThreadFactory daemonThreads = runnable -> {
            Thread thread = new Thread(runnable, "u0-http");
            thread.setDaemon(true);
            return thread;
        };
        server.setExecutor(Executors.newCachedThreadPool(daemonThreads));
        server.start();
    }

    private String createPrivateBucket() {
        String run = BUCKET_TIME.format(Instant.now()) + "-" + UUID.randomUUID().toString().replace("-", "");
        String name = "zeroverse-u0-" + run.toLowerCase(Locale.ROOT);
        s3.createBucket(CreateBucketRequest.builder().bucket(name).build());
        try {
            s3.putPublicAccessBlock(PutPublicAccessBlockRequest.builder()
                    .bucket(name)
                    .publicAccessBlockConfiguration(PublicAccessBlockConfiguration.builder()
                            .blockPublicAcls(true)
                            .ignorePublicAcls(true)
                            .blockPublicPolicy(true)
                            .restrictPublicBuckets(true)
                            .build())
                    .build());
        } catch (S3Exception exception) {
            if (provider != Provider.SEAWEEDFS || exception.statusCode() != 501) {
                throw exception;
            }
            System.out.println("CAPABILITY provider=SeaweedFS feature=PublicAccessBlock UNSUPPORTED HTTP501");
        }
        try {
            s3.putBucketOwnershipControls(PutBucketOwnershipControlsRequest.builder()
                    .bucket(name)
                    .ownershipControls(OwnershipControls.builder()
                            .rules(OwnershipControlsRule.builder()
                                    .objectOwnership(ObjectOwnership.BUCKET_OWNER_ENFORCED)
                                    .build())
                            .build())
                    .build());
        } catch (S3Exception exception) {
            if (provider != Provider.SEAWEEDFS || exception.statusCode() != 501) {
                throw exception;
            }
            System.out.println("CAPABILITY provider=SeaweedFS feature=OwnershipControls UNSUPPORTED HTTP501");
        }
        s3.putBucketCors(PutBucketCorsRequest.builder()
                .bucket(name)
                .corsConfiguration(CORSConfiguration.builder()
                        .corsRules(CORSRule.builder()
                                .allowedOrigins(HARNESS_ORIGIN)
                                .allowedMethods("GET", "PUT", "HEAD")
                                .allowedHeaders("*")
                                .exposeHeaders("ETag", "x-amz-checksum-sha256", "Content-Length", "Content-Type")
                                .maxAgeSeconds(3000)
                                .build())
                        .build())
                .build());
        return name;
    }

    private void putPreseedObject() {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(preseedKey)
                        .contentLength((long) preseedBytes.length)
                        .contentType(png.contentType())
                        .checksumSHA256(preseedChecksum)
                        .cacheControl("private, no-store")
                        .build(),
                RequestBody.fromBytes(preseedBytes));
    }

    private synchronized void ensureCases() {
        if (casesBuilt) {
            return;
        }

        Fixture webp = fixtures.get("webp");
        Fixture gif = fixtures.get("gif");
        UploadPlan validJpeg = addUpload("valid-jpeg", jpeg.bytes(), jpeg.bytes(), jpeg.contentType(), PlanKind.VALID, List.of(200), null);
        UploadPlan validPng = addUpload("valid-png", png.bytes(), png.bytes(), png.contentType(), PlanKind.VALID, List.of(200), null);
        addUpload("valid-webp", webp.bytes(), webp.bytes(), webp.contentType(), PlanKind.VALID, List.of(200), null);
        addUpload("valid-gif", gif.bytes(), gif.bytes(), gif.contentType(), PlanKind.VALID, List.of(200), null);

        byte[] maxBytes = padded(png.bytes(), MAX_BYTES);
        byte[] overMaxBytes = padded(png.bytes(), MAX_BYTES + 1);
        addUpload("valid-5mb", maxBytes, maxBytes, png.contentType(), PlanKind.VALID, List.of(200), null);
        addUpload("over-5mb-signed-at-max", overMaxBytes, overMaxBytes, png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), null,
                MAX_BYTES);
        addUpload("empty-signed-at-one", new byte[0], new byte[0], png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), null, 1);
        addUpload("expired-put", png.bytes(), png.bytes(), png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), 3000,
                png.bytes().length, Duration.ofSeconds(1));

        addUpload("mime-spoof-jpeg-as-png", jpeg.bytes(), jpeg.bytes(), png.contentType(), PlanKind.MIME_SPOOF, List.of(200), null);
        addUpload("same-length-byte-tamper", mutate(png.bytes()), png.bytes(), png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), null);

        UploadPlan signedType = addUpload("signed-content-type-tamper", png.bytes(), png.bytes(), png.contentType(), PlanKind.EXPECT_REJECTED, List.of(403), null);
        replaceHeader(signedType, "content-type", "image/jpeg");
        UploadPlan signedChecksum = addUpload("signed-checksum-tamper", png.bytes(), png.bytes(), png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), null);
        replaceHeader(signedChecksum, "x-amz-checksum-sha256", sha256Base64(new byte[]{1, 2, 3}));
        UploadPlan missingChecksum = addUpload("signed-checksum-missing", png.bytes(), png.bytes(), png.contentType(), PlanKind.EXPECT_REJECTED, List.of(400, 403), null);
        removeHeader(missingChecksum, "x-amz-checksum-sha256");

        UploadPlan replayFirst = addUpload("replay-first", png.bytes(), png.bytes(), png.contentType(), PlanKind.REPLAY_FIRST, List.of(200), null);
        addReplayCase("replay-second-overwrite", replayFirst, PlanKind.REPLAY_SECOND, List.of(409, 412));

        signedGetUrl = presignGet(preseedKey, Duration.ofMinutes(5));
        invalidSignatureUrl = mutateSignature(signedGetUrl);
        expiryAt = Instant.now().plusSeconds(1);
        expiryUrl = presignGet(preseedKey, Duration.ofSeconds(1));
        cases.add(new CaseEntry("signed-get-positive", "GET", signedGetUrl, Map.of(), "", "", List.of(200), null));
        cases.add(new CaseEntry("signed-get-invalid-signature", "GET", invalidSignatureUrl, Map.of(), "", "", List.of(403), null));
        cases.add(new CaseEntry("signed-get-expired", "GET", expiryUrl, Map.of(), "", "", List.of(403), 3000));
        cases.add(new CaseEntry("unsigned-private-get", "GET", objectUri(preseedKey), Map.of(), "", "", List.of(403), null));
        casesBuilt = true;
    }

    private UploadPlan addUpload(
            String id,
            byte[] body,
            byte[] expectedBytes,
            String declaredType,
            PlanKind kind,
            List<Integer> expectedStatuses,
            Integer waitMs) {
        return addUpload(id, body, expectedBytes, declaredType, kind, expectedStatuses, waitMs, body.length);
    }

    private UploadPlan addUpload(
            String id,
            byte[] body,
            byte[] expectedBytes,
            String declaredType,
            PlanKind kind,
            List<Integer> expectedStatuses,
            Integer waitMs,
            long signedLength) {
        return addUpload(id, body, expectedBytes, declaredType, kind, expectedStatuses, waitMs, signedLength, PUT_SIGNATURE_TTL);
    }

    private UploadPlan addUpload(
            String id,
            byte[] body,
            byte[] expectedBytes,
            String declaredType,
            PlanKind kind,
            List<Integer> expectedStatuses,
            Integer waitMs,
            long signedLength,
            Duration signatureDuration) {
        String extension = declaredType.substring(declaredType.indexOf('/') + 1);
        String key = "u0/" + id + "-" + UUID.randomUUID() + "." + extension;
        String checksum = sha256Base64(expectedBytes);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(signedLength)
                .contentType(declaredType)
                .checksumSHA256(checksum)
                .ifNoneMatch("*")
                .cacheControl("private, no-store")
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(request)
                .build());
        assertSignedPutHeaders(presigned);
        URI url = toUri(presigned.url());
        UploadPlan plan = new UploadPlan(id, key, url, browserHeaders(presigned), body, expectedBytes,
                declaredType, checksum, kind);
        uploadPlans.put(id, plan);
        cases.add(toCase(plan, expectedStatuses, waitMs));
        return plan;
    }

    private void addReplayCase(String id, UploadPlan original, PlanKind kind, List<Integer> expectedStatuses) {
        UploadPlan replay = new UploadPlan(id, original.key(), original.url(), original.headers(), original.body(), original.expectedBytes(),
                original.declaredType(), original.expectedChecksum(), kind);
        uploadPlans.put(id, replay);
        cases.add(toCase(replay, expectedStatuses, null));
    }

    private CaseEntry toCase(UploadPlan plan, List<Integer> expectedStatuses, Integer waitMs) {
        return new CaseEntry(plan.id(), "PUT", plan.url(), plan.headers(), Base64.getEncoder().encodeToString(plan.body()),
                plan.declaredType(), expectedStatuses, waitMs);
    }

    private void assertSignedPutHeaders(PresignedPutObjectRequest presigned) {
        String signedHeaders = queryParameter(toUri(presigned.url()), "X-Amz-SignedHeaders");
        Set<String> lower = Arrays.stream(signedHeaders.split(";"))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Set<String> required = Set.of("content-length", "content-type", "if-none-match", "x-amz-checksum-sha256", "cache-control");
        if (!lower.containsAll(required)) {
            throw new IllegalStateException("presigned PUT did not sign required headers");
        }
    }

    private Map<String, String> browserHeaders(PresignedPutObjectRequest presigned) {
        Map<String, String> result = new LinkedHashMap<>();
        presigned.httpRequest().headers().forEach((name, values) -> {
            if (name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length") || values.isEmpty()) {
                return;
            }
            result.put(name, values.get(0));
        });
        if (result.keySet().stream().anyMatch(name -> name.equalsIgnoreCase("host") || name.equalsIgnoreCase("content-length"))) {
            throw new IllegalStateException("browser manifest must not set Host or Content-Length");
        }
        return Collections.unmodifiableMap(result);
    }

    private void replaceHeader(UploadPlan plan, String header, String value) {
        Map<String, String> headers = new LinkedHashMap<>(plan.headers());
        removeHeader(headers, header);
        headers.put(header, value);
        uploadPlans.put(plan.id(), new UploadPlan(plan.id(), plan.key(), plan.url(), Collections.unmodifiableMap(headers), plan.body(),
                plan.expectedBytes(), plan.declaredType(), plan.expectedChecksum(), plan.kind()));
        replaceCase(plan.id(), uploadPlans.get(plan.id()));
    }

    private void removeHeader(UploadPlan plan, String header) {
        Map<String, String> headers = new LinkedHashMap<>(plan.headers());
        removeHeader(headers, header);
        UploadPlan updated = new UploadPlan(plan.id(), plan.key(), plan.url(), Collections.unmodifiableMap(headers), plan.body(),
                plan.expectedBytes(), plan.declaredType(), plan.expectedChecksum(), plan.kind());
        uploadPlans.put(plan.id(), updated);
        replaceCase(plan.id(), updated);
    }

    private static void removeHeader(Map<String, String> headers, String header) {
        headers.keySet().removeIf(name -> name.equalsIgnoreCase(header));
    }

    private void replaceCase(String id, UploadPlan plan) {
        for (int i = 0; i < cases.size(); i++) {
            if (cases.get(i).id().equals(id)) {
                CaseEntry old = cases.get(i);
                cases.set(i, toCase(plan, old.expectedStatuses(), old.waitMs()));
                return;
            }
        }
        throw new IllegalStateException("manifest case not found");
    }

    private URI presignGet(String key, Duration ttl) {
        PresignedGetObjectRequest presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build());
        return toUri(presigned.url());
    }

    private URI mutateSignature(URI original) {
        String[] parameters = original.getRawQuery().split("&");
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].startsWith("X-Amz-Signature=")) {
                String value = parameters[i].substring("X-Amz-Signature=".length());
                char last = value.charAt(value.length() - 1);
                char replacement = last == '0' ? '1' : '0';
                parameters[i] = "X-Amz-Signature=" + value.substring(0, value.length() - 1) + replacement;
                return URI.create(original.getScheme() + "://" + original.getRawAuthority() + original.getRawPath() + "?" + String.join("&", parameters));
            }
        }
        throw new IllegalStateException("presigned GET signature parameter missing");
    }

    private URI objectUri(String key) {
        return URI.create(s3Endpoint + "/" + bucket + "/" + key);
    }

    private int runSelfCheck() {
        ensureCases();
        boolean allPassed = true;
        for (CaseEntry entry : cases) {
            if (entry.waitMs() != null) {
                sleep(entry.waitMs());
            }
            int status = requestStatus(entry);
            boolean passed = entry.expectedStatuses().contains(status);
            allPassed &= passed;
            System.out.printf("CASE %-34s %s status=%d expected=%s%n", entry.id(), passed ? "PASS" : "FAIL", status, entry.expectedStatuses());
        }
        for (Check check : verifyChecks()) {
            allPassed &= check.passed();
            System.out.printf("VERIFY %-32s %s %s%n", check.id(), check.passed() ? "PASS" : "FAIL", check.detail());
        }
        System.out.println("U0 self-check result=" + (allPassed ? "PASS" : "FAIL") + "; browser CUA evidence remains separate.");
        return allPassed ? 0 : 1;
    }

    private int requestStatus(CaseEntry entry) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(entry.url())
                    .timeout(Duration.ofSeconds(30))
                    .header("Origin", HARNESS_ORIGIN);
            entry.headers().forEach(builder::header);
            if (entry.method().equals("PUT")) {
                builder.PUT(HttpRequest.BodyPublishers.ofByteArray(Base64.getDecoder().decode(entry.bodyBase64())));
            } else if (entry.method().equals("GET")) {
                builder.GET();
            } else {
                return -1;
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception exception) {
            return -1;
        }
    }

    private synchronized List<Check> verifyChecks() {
        ensureCases();
        List<Check> checks = new ArrayList<>();
        checks.add(verifyPublicAccessBlock());
        checks.add(verifyOwnershipControls());
        checks.add(verifyCors());
        checks.add(new Check("signed-put-required-headers", true,
                "SDK presigned PUT query includes Content-Length, Content-Type, checksum, If-None-Match, Cache-Control; browser headers omit Host/Content-Length."));
        checks.add(verifyHttpStatus("signed-get-positive", signedGetUrl, 200));
        checks.add(verifyHttpStatus("signed-get-invalid-signature", invalidSignatureUrl, 403));
        checks.add(verifyExpiry());
        checks.add(verifyUnsignedPrivateGet());
        checks.add(verifyCorsPreflight());
        checks.add(verifyWrongOriginPreflight());
        checks.add(verifyInput("empty-input-precheck", new byte[0], png.contentType(), false));
        checks.add(verifyInput("over-max-input-precheck", new byte[(int) MAX_BYTES + 1], png.contentType(), false));
        checks.add(verifyInput("unsupported-type-precheck", png.bytes(), "image/svg+xml", false));
        checks.add(verifyStoredObject("preseed-head-checksum-tika", preseedKey, preseedBytes, png.contentType(), preseedChecksum, false));

        Set<String> checkedKeys = new java.util.HashSet<>();
        for (UploadPlan plan : uploadPlans.values()) {
            if (!checkedKeys.add(plan.key())) {
                continue;
            }
            if (plan.kind() == PlanKind.VALID || plan.kind() == PlanKind.MIME_SPOOF || plan.kind() == PlanKind.REPLAY_FIRST) {
                checks.add(verifyStoredObject(plan.id() + "-head-bounded-get", plan.key(), plan.expectedBytes(), plan.declaredType(), plan.expectedChecksum(), plan.kind() == PlanKind.MIME_SPOOF));
            } else if (plan.kind() == PlanKind.EXPECT_REJECTED) {
                checks.add(verifyRejectedObject(plan));
            }
        }
        return checks;
    }

    private Check verifyPublicAccessBlock() {
        try {
            PublicAccessBlockConfiguration config = s3.getPublicAccessBlock(GetPublicAccessBlockRequest.builder().bucket(bucket).build())
                    .publicAccessBlockConfiguration();
            boolean passed = config != null
                    && Boolean.TRUE.equals(config.blockPublicAcls())
                    && Boolean.TRUE.equals(config.ignorePublicAcls())
                    && Boolean.TRUE.equals(config.blockPublicPolicy())
                    && Boolean.TRUE.equals(config.restrictPublicBuckets());
            return new Check("bucket-public-access-block", passed, passed ? "all four public-access-block flags are true" : "one or more public-access-block flags are not true");
        } catch (S3Exception exception) {
            String detail = provider == Provider.SEAWEEDFS && exception.statusCode() == 501
                    ? "CAPABILITY provider=SeaweedFS feature=PublicAccessBlock UNSUPPORTED HTTP501"
                    : "configuration read failed: HTTP" + exception.statusCode();
            return new Check("bucket-public-access-block", false, detail);
        } catch (Exception exception) {
            return new Check("bucket-public-access-block", false, "configuration read failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyOwnershipControls() {
        try {
            List<OwnershipControlsRule> rules = s3.getBucketOwnershipControls(GetBucketOwnershipControlsRequest.builder().bucket(bucket).build())
                    .ownershipControls().rules();
            boolean passed = rules.size() == 1 && rules.get(0).objectOwnership() == ObjectOwnership.BUCKET_OWNER_ENFORCED;
            return new Check("bucket-ownership-enforced", passed, passed ? "BUCKET_OWNER_ENFORCED" : "ownership control is not exact");
        } catch (S3Exception exception) {
            String detail = provider == Provider.SEAWEEDFS && exception.statusCode() == 501
                    ? "CAPABILITY provider=SeaweedFS feature=OwnershipControls UNSUPPORTED HTTP501"
                    : "configuration read failed: HTTP" + exception.statusCode();
            return new Check("bucket-ownership-enforced", false, detail);
        } catch (Exception exception) {
            return new Check("bucket-ownership-enforced", false, "configuration read failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyCors() {
        try {
            List<CORSRule> rules = s3.getBucketCors(GetBucketCorsRequest.builder().bucket(bucket).build()).corsRules();
            boolean passed = rules.size() == 1
                    && rules.get(0).allowedOrigins().equals(List.of(HARNESS_ORIGIN))
                    && Set.copyOf(rules.get(0).allowedMethods()).equals(Set.of("GET", "PUT", "HEAD"))
                    && rules.get(0).allowedHeaders().contains("*");
            return new Check("bucket-cors-exact-origin", passed, passed ? "only http://127.0.0.1:14567 is allowed" : "CORS origin/method/header rule is not exact");
        } catch (Exception exception) {
            return new Check("bucket-cors-exact-origin", false, "configuration read failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyHttpStatus(String id, URI url, int expected) {
        CaseEntry entry = new CaseEntry(id, "GET", url, Map.of(), "", "", List.of(expected), null);
        int status = requestStatus(entry);
        return new Check(id, status == expected, "status=" + status + ", expected=" + expected);
    }

    private Check verifyExpiry() {
        long wait = expiryAt == null ? 0 : Duration.between(Instant.now(), expiryAt).toMillis() + 500;
        if (wait > 0) {
            sleep((int) Math.min(wait, 2000));
        }
        return verifyHttpStatus("signed-get-expired", expiryUrl, 403);
    }

    private Check verifyUnsignedPrivateGet() {
        CaseEntry entry = new CaseEntry("unsigned-private-get", "GET", objectUri(preseedKey), Map.of(), "", "", List.of(403), null);
        int status = requestStatus(entry);
        boolean passed = status == 403;
        String detail = passed
                ? "unsigned private GET returned 403"
                : "unsigned private GET returned status=" + status + "; " + provider.label
                        + " did not enforce the expected private-object denial, so this is a real failure";
        return new Check("unsigned-private-get-403", passed, detail);
    }

    private Check verifyCorsPreflight() {
        try {
            HttpRequest request = HttpRequest.newBuilder(objectUri(preseedKey))
                    .timeout(Duration.ofSeconds(10))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", HARNESS_ORIGIN)
                    .header("Access-Control-Request-Method", "PUT")
                    .header("Access-Control-Request-Headers", "content-type,if-none-match,x-amz-checksum-sha256,cache-control")
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            String allowOrigin = response.headers().firstValue("access-control-allow-origin").orElse("");
            boolean passed = (response.statusCode() == 200 || response.statusCode() == 204) && HARNESS_ORIGIN.equals(allowOrigin);
            return new Check("cors-preflight-exact-origin", passed, "status=" + response.statusCode() + ", allow-origin=" + (HARNESS_ORIGIN.equals(allowOrigin) ? "exact" : "mismatch"));
        } catch (Exception exception) {
            return new Check("cors-preflight-exact-origin", false, "preflight failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyWrongOriginPreflight() {
        try {
            String wrongOrigin = "http://127.0.0.1:14568";
            HttpRequest request = HttpRequest.newBuilder(objectUri(preseedKey))
                    .timeout(Duration.ofSeconds(10))
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .header("Origin", wrongOrigin)
                    .header("Access-Control-Request-Method", "PUT")
                    .header("Access-Control-Request-Headers", "content-type,if-none-match,x-amz-checksum-sha256,cache-control")
                    .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            String allowOrigin = response.headers().firstValue("access-control-allow-origin").orElse("");
            boolean passed = !"*".equals(allowOrigin) && !wrongOrigin.equals(allowOrigin);
            return new Check("cors-preflight-wrong-origin-rejected", passed, "status=" + response.statusCode() + ", allow-origin=" + (passed ? "not-allowed" : "wrong-origin-allowed"));
        } catch (Exception exception) {
            return new Check("cors-preflight-wrong-origin-rejected", false, "preflight failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyInput(String id, byte[] bytes, String contentType, boolean expectedValid) {
        boolean valid = bytes.length >= 1 && bytes.length <= MAX_BYTES && ALLOWED_TYPES.contains(contentType);
        boolean passed = valid == expectedValid;
        return new Check(id, passed, "local bounds/type precheck valid=" + valid + "; this is not a product endpoint result");
    }

    private Check verifyStoredObject(String id, String key, byte[] expectedBytes, String expectedType, String expectedChecksum, boolean expectMimeMismatch) {
        try {
            HeadObjectResponse head = s3.headObject(request -> request.bucket(bucket).key(key).checksumMode(ChecksumMode.ENABLED));
            boolean sizeAndHead = head.contentLength() != null
                    && head.contentLength() == expectedBytes.length
                    && expectedType.equals(head.contentType())
                    && expectedChecksum.equals(head.checksumSHA256());
            BoundedObject bounded = readBounded(key);
            String byteChecksum = sha256Base64(bounded.bytes());
            boolean byteChecks = bounded.bytes().length <= MAX_BYTES && expectedChecksum.equals(byteChecksum);
            boolean mimeMatches = expectedType.equals(bounded.detectedType());
            boolean passed = expectMimeMismatch
                    ? sizeAndHead && byteChecks && !mimeMatches
                    : sizeAndHead && byteChecks && mimeMatches;
            String detail = "headSize=" + head.contentLength()
                    + ", headType=" + (expectedType.equals(head.contentType()) ? "expected" : "mismatch")
                    + ", headChecksum=" + (expectedChecksum.equals(head.checksumSHA256()) ? "expected" : "missing-or-mismatch")
                    + ", boundedBytes=" + bounded.bytes().length
                    + ", tika=" + bounded.detectedType()
                    + (expectMimeMismatch ? ", spoof-rejected=" + (!mimeMatches) : "");
            return new Check(id, passed, detail);
        } catch (Exception exception) {
            if (isMissing(exception)) {
                return new Check(id, false, "object is not present; browser PUT has not completed or storage rejected it");
            }
            return new Check(id, false, "HEAD/bounded GET failed: " + exception.getClass().getSimpleName());
        }
    }

    private Check verifyRejectedObject(UploadPlan plan) {
        try {
            s3.headObject(request -> request.bucket(bucket).key(plan.key()).checksumMode(ChecksumMode.ENABLED));
            return new Check(plan.id() + "-rejection", false, "object exists after a case expected to be rejected");
        } catch (Exception exception) {
            if (isMissing(exception)) {
                return new Check(plan.id() + "-rejection", true, "object absent after the signed negative case");
            }
            return new Check(plan.id() + "-rejection", false, "HEAD failed without authoritative missing-object status: " + exception.getClass().getSimpleName());
        }
    }

    private BoundedObject readBounded(String key) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=0-" + MAX_BYTES)
                .build();
        try (InputStream input = s3.getObject(request)) {
            byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
            return new BoundedObject(bytes, tika.detect(bytes));
        }
    }

    private void handleRoot(HttpExchange exchange) {
        if (!validateRequest(exchange)) {
            return;
        }
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, "text/plain; charset=UTF-8", "method not allowed".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        URI requestUri = exchange.getRequestURI();
        if (!"/".equals(requestUri.getPath()) || requestUri.getRawQuery() != null) {
            send(exchange, 404, "text/plain; charset=UTF-8", "not found".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        Path html = repoRoot.resolve("frontend/u0/index.html").normalize();
        if (!html.startsWith(repoRoot) || !Files.isRegularFile(html)) {
            send(exchange, 404, "text/plain; charset=UTF-8", "frontend/u0/index.html is unavailable".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        try {
            send(exchange, 200, "text/html; charset=UTF-8", Files.readAllBytes(html), false);
        } catch (IOException exception) {
            send(exchange, 500, "text/plain; charset=UTF-8", "frontend file read failed".getBytes(StandardCharsets.UTF_8), false);
        }
    }

    private void handleCases(HttpExchange exchange) {
        if (!validateRequest(exchange)) {
            return;
        }
        if (!exchange.getRequestMethod().equals("GET") || !"/cases".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"method not allowed\"}".getBytes(StandardCharsets.UTF_8), true);
            return;
        }
        try {
            ensureCases();
            send(exchange, 200, "application/json; charset=UTF-8", manifestJson().getBytes(StandardCharsets.UTF_8), true);
        } catch (Exception exception) {
            send(exchange, 500, "application/json; charset=UTF-8", "{\"error\":\"manifest unavailable\"}".getBytes(StandardCharsets.UTF_8), true);
        }
    }

    private void handleVerify(HttpExchange exchange) {
        if (!validateRequest(exchange)) {
            return;
        }
        if (!exchange.getRequestMethod().equals("GET") || !"/verify".equals(exchange.getRequestURI().getPath())) {
            send(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"method not allowed\"}".getBytes(StandardCharsets.UTF_8), true);
            return;
        }
        try {
            send(exchange, 200, "application/json; charset=UTF-8", checksJson(verifyChecks()).getBytes(StandardCharsets.UTF_8), true);
        } catch (Exception exception) {
            send(exchange, 500, "application/json; charset=UTF-8", "{\"error\":\"verification unavailable\"}".getBytes(StandardCharsets.UTF_8), true);
        }
    }

    private boolean validateRequest(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String fetchSite = exchange.getRequestHeaders().getFirst("Sec-Fetch-Site");
        boolean fetchSiteAllowed = fetchSite == null || fetchSite.equals("same-origin") || fetchSite.equals("none");
        if (host == null || !HARNESS_HOST.equalsIgnoreCase(host)
                || (origin != null && !HARNESS_ORIGIN.equals(origin))
                || !fetchSiteAllowed) {
            send(exchange, 403, "application/json; charset=UTF-8", "{\"error\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8), false);
            return false;
        }
        return true;
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body, boolean cors) {
        try {
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType);
            headers.set("Cache-Control", "no-store");
            headers.set("Pragma", "no-cache");
            headers.set("X-Content-Type-Options", "nosniff");
            if (cors && HARNESS_ORIGIN.equals(exchange.getRequestHeaders().getFirst("Origin"))) {
                headers.set("Access-Control-Allow-Origin", HARNESS_ORIGIN);
                headers.set("Vary", "Origin");
            }
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } catch (IOException ignored) {
            // The browser may close a request while verification is running.
        } finally {
            exchange.close();
        }
    }

    private String manifestJson() {
        StringBuilder json = new StringBuilder("{\"cases\":[");
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            CaseEntry entry = cases.get(i);
            json.append("{\"id\":").append(jsonString(entry.id()))
                    .append(",\"method\":").append(jsonString(entry.method()))
                    .append(",\"url\":").append(jsonString(entry.url().toString()))
                    .append(",\"headers\":").append(jsonMap(entry.headers()))
                    .append(",\"bodyBase64\":").append(jsonString(entry.bodyBase64()))
                    .append(",\"contentType\":").append(jsonString(entry.contentType()))
                    .append(",\"expectedStatuses\":[");
            for (int statusIndex = 0; statusIndex < entry.expectedStatuses().size(); statusIndex++) {
                if (statusIndex > 0) {
                    json.append(',');
                }
                json.append(entry.expectedStatuses().get(statusIndex));
            }
            json.append(']');
            if (entry.waitMs() != null) {
                json.append(",\"waitMs\":").append(entry.waitMs());
            }
            json.append('}');
        }
        return json.append("]}").toString();
    }

    private static String checksJson(List<Check> checks) {
        StringBuilder json = new StringBuilder("{\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            Check check = checks.get(i);
            json.append("{\"id\":").append(jsonString(check.id()))
                    .append(",\"passed\":").append(check.passed())
                    .append(",\"detail\":").append(jsonString(check.detail())).append('}');
        }
        return json.append("]}").toString();
    }

    private static String jsonMap(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (i++ > 0) {
                json.append(',');
            }
            json.append(jsonString(entry.getKey())).append(':').append(jsonString(entry.getValue()));
        }
        return json.append('}').toString();
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        return json.append('"').toString();
    }

    private static String queryParameter(URI uri, String name) {
        for (String parameter : uri.getRawQuery().split("&")) {
            int equals = parameter.indexOf('=');
            if (equals > 0 && parameter.substring(0, equals).equalsIgnoreCase(name)) {
                return java.net.URLDecoder.decode(parameter.substring(equals + 1), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("presigned query parameter missing");
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (Exception exception) {
            throw new IllegalStateException("presigned URL cannot be represented as URI", exception);
        }
    }

    private static void requireFixedLoopbackEndpoint(Provider provider) throws Exception {
        URI endpoint = provider.endpoint;
        if (!"http".equalsIgnoreCase(endpoint.getScheme())
                || !"127.0.0.1".equals(endpoint.getHost())
                || (provider == Provider.LOCALSTACK && endpoint.getPort() != 14566)
                || (provider == Provider.SEAWEEDFS && endpoint.getPort() != 14568)
                || !InetAddress.getByName(endpoint.getHost()).isLoopbackAddress()) {
            throw new IllegalStateException("S3 endpoint must be one of the fixed loopback provider endpoints");
        }
    }

    private static Path findRepoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path candidate = Files.isRegularFile(current.resolve("AGENTS.md")) ? current : current.resolve("../..").normalize();
        if (!Files.isRegularFile(candidate.resolve("AGENTS.md"))) {
            throw new IllegalStateException("repository root is not the current directory or fixed gradle/u0 parent");
        }
        return candidate;
    }

    private static Map<String, Fixture> buildFixtures() throws IOException {
        Map<String, Fixture> result = new LinkedHashMap<>();
        result.put("jpeg", new Fixture("jpeg", "fixture.jpg", "image/jpeg", imageBytes("jpg")));
        result.put("png", new Fixture("png", "fixture.png", "image/png", imageBytes("png")));
        result.put("gif", new Fixture("gif", "fixture.gif", "image/gif", imageBytes("gif")));
        byte[] webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAEAmJaQAA3AA/v89WAAAAA==");
        String detectedWebp = new Tika().detect(webp);
        if (!"image/webp".equals(detectedWebp)) {
            throw new IllegalStateException("built-in WebP fixture detected as " + detectedWebp);
        }
        result.put("webp", new Fixture("webp", "fixture.webp", "image/webp", webp));
        return Collections.unmodifiableMap(result);
    }

    private static byte[] imageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xE85D75);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IOException("image writer unavailable for " + format);
        }
        return output.toByteArray();
    }

    private static byte[] padded(byte[] prefix, long size) {
        if (size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("fixture is too large");
        }
        byte[] result = Arrays.copyOf(prefix, (int) size);
        for (int i = prefix.length; i < result.length; i++) {
            result[i] = (byte) (i * 31);
        }
        return result;
    }

    private static byte[] mutate(byte[] input) {
        byte[] result = input.clone();
        result[result.length / 2] ^= 0x01;
        return result;
    }

    private static String sha256Base64(byte[] bytes) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean isMissing(Exception exception) {
        if (exception instanceof NoSuchKeyException) {
            return true;
        }
        if (exception instanceof S3Exception s3Exception) {
            return s3Exception.statusCode() == 404
                    || "NoSuchKey".equals(s3Exception.awsErrorDetails() == null ? null : s3Exception.awsErrorDetails().errorCode());
        }
        return false;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(Math.max(0, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        presigner.close();
        s3.close();
        notifyAll();
    }
}
