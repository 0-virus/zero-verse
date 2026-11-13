package com.zeroverse.domain.upload.service;

import com.zeroverse.domain.upload.config.S3StorageProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ChecksumMode;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

/** Narrow S3 boundary. It never logs or exposes credentials, URLs, or object locations. */
@Component
@ConditionalOnProperty(prefix = "zeroverse.upload", name = "backend", havingValue = "s3")
public class S3ObjectStore {

    public static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    public static final int MAX_BYTES = 5_242_880;

    private final S3StorageProperties properties;
    private final ConcurrentMap<String, S3Client> clients = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, S3Presigner> presigners = new ConcurrentHashMap<>();

    public S3ObjectStore(S3StorageProperties properties) {
        this.properties = properties;
        if (properties.getRegion() == null || properties.getRegion().isBlank()) {
            throw new IllegalStateException("S3 region is required when S3 storage is enabled");
        }
        ClientOverrideConfiguration override = ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(nonNullDuration(properties.getAttemptTimeout(), Duration.ofSeconds(5)))
                .apiCallTimeout(nonNullDuration(properties.getApiCallTimeout(), Duration.ofSeconds(10)))
                .build();
        // Build the configured region eagerly so a malformed production region fails before serving.
        clients.put(properties.getRegion(), buildClient(properties.getRegion(), override));
        presigners.put(properties.getRegion(), buildPresigner(properties.getRegion()));
    }

    public PresignedPut presignPut(
            String bucket,
            String key,
            String contentType,
            long contentLength,
            String checksumSha256) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .checksumSHA256(checksumSha256)
                .ifNoneMatch("*")
                .build();
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_TTL)
                .putObjectRequest(put)
                .build();
        PresignedPutObjectRequest presigned = presigners.get(properties.getRegion()).presignPutObject(request);
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            // Host and Content-Length are browser-controlled. The browser supplies the exact
            // request length from File.size; every other signed header must be echoed by FE.
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                if (values == null || values.size() != 1) {
                    throw new IllegalStateException("unsupported multi-valued S3 signed header");
                }
                headers.put(name, values.get(0));
            }
        });
        return new PresignedPut(presigned.url().toString(), headers);
    }

    public void head(String bucket, String key) {
        head(properties.getRegion(), bucket, key);
    }

    public void head(String region, String bucket, String key) {
        try {
            clientFor(region).headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            throw new ObjectMissingException(true);
        } catch (S3Exception e) {
            if (isDefiniteMissing(e)) {
                throw new ObjectMissingException(true);
            }
            throw new StorageUnavailableException(e);
        } catch (SdkClientException e) {
            throw new StorageUnavailableException(e);
        } catch (RuntimeException e) {
            throw new StorageUnavailableException(e);
        }
    }

    public ObjectSnapshot get(String bucket, String key) {
        return get(properties.getRegion(), bucket, key);
    }

    public ObjectSnapshot get(String region, String bucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .checksumMode(ChecksumMode.ENABLED)
                .build();
        try (ResponseInputStream<GetObjectResponse> input = clientFor(region).getObject(request)) {
            byte[] bytes;
            try {
                bytes = readBounded(input);
            } catch (ObjectTooLargeException e) {
                input.abort();
                throw e;
            }
            String contentType = input.response().contentType();
            return new ObjectSnapshot(bytes, contentType);
        } catch (ObjectTooLargeException e) {
            throw e;
        } catch (NoSuchKeyException e) {
            throw new ObjectMissingException(true);
        } catch (S3Exception e) {
            if (isDefiniteMissing(e)) {
                throw new ObjectMissingException(true);
            }
            throw new StorageUnavailableException(e);
        } catch (SdkClientException e) {
            throw new StorageUnavailableException(e);
        } catch (IOException e) {
            throw new StorageUnavailableException(e);
        } catch (RuntimeException e) {
            throw new StorageUnavailableException(e);
        }
    }

    public void delete(String bucket, String key) {
        delete(properties.getRegion(), bucket, key);
    }

    public void delete(String region, String bucket, String key) {
        try {
            clientFor(region).deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (NoSuchKeyException e) {
            // DeleteObject is idempotent for a missing object.
        } catch (S3Exception e) {
            if (!isDefiniteMissing(e)) {
                throw new StorageUnavailableException(e);
            }
        } catch (SdkClientException e) {
            throw new StorageUnavailableException(e);
        } catch (RuntimeException e) {
            throw new StorageUnavailableException(e);
        }
    }

    private static boolean isDefiniteMissing(S3Exception e) {
        SdkHttpResponse response = e.awsErrorDetails() == null ? null : e.awsErrorDetails().sdkHttpResponse();
        int status = response == null ? e.statusCode() : response.statusCode();
        String errorCode = e.awsErrorDetails() == null ? "" : e.awsErrorDetails().errorCode();
        return status == 404 || "NoSuchKey".equals(errorCode) || "NotFound".equals(errorCode);
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        final long maxRead = (long) MAX_BYTES + 1;
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BYTES, 8192));
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while (total < maxRead
                && (read = input.read(buffer, 0, (int) Math.min(buffer.length, maxRead - total))) != -1) {
            total += read;
            if (total >= maxRead) {
                throw new ObjectTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private S3Client clientFor(String region) {
        if (region == null || region.isBlank()) {
            throw new StorageUnavailableException(new IllegalArgumentException("S3 object region is missing"));
        }
        return clients.computeIfAbsent(region, value -> buildClient(value,
                ClientOverrideConfiguration.builder()
                        .apiCallAttemptTimeout(nonNullDuration(properties.getAttemptTimeout(), Duration.ofSeconds(5)))
                        .apiCallTimeout(nonNullDuration(properties.getApiCallTimeout(), Duration.ofSeconds(10)))
                        .build()));
    }

    private S3Client buildClient(String region, ClientOverrideConfiguration override) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region))
                .overrideConfiguration(override)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    private S3Presigner buildPresigner(String region) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(region))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyle())
                        .build());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    @PreDestroy
    void closeClients() {
        clients.values().forEach(S3Client::close);
        presigners.values().forEach(S3Presigner::close);
    }

    private static Duration nonNullDuration(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    public record PresignedPut(String url, Map<String, String> headers) {
        public PresignedPut {
            Objects.requireNonNull(url, "url");
            headers = Map.copyOf(headers);
        }
    }

    public record ObjectSnapshot(byte[] bytes, String contentType) {}

    public static class ObjectMissingException extends RuntimeException {
        private final boolean definitelyMissing;

        public ObjectMissingException(boolean definitelyMissing) {
            this.definitelyMissing = definitelyMissing;
        }

        public boolean definitelyMissing() {
            return definitelyMissing;
        }
    }

    public static class StorageUnavailableException extends RuntimeException {
        public StorageUnavailableException(Throwable cause) {
            super(cause);
        }
    }

    public static class ObjectTooLargeException extends RuntimeException {}
}
