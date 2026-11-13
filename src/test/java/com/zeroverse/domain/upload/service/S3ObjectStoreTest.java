package com.zeroverse.domain.upload.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeroverse.domain.upload.config.S3StorageProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class S3ObjectStoreTest {

    @AfterEach
    void clearSdkCredentials() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
    }

    @Test
    void exposesEveryBrowserSettableSignedHeaderAndOmitsBrowserControlledHeaders() {
        System.setProperty("aws.accessKeyId", "AKIAZERO23TEST");
        System.setProperty("aws.secretAccessKey", "zeroverse-test-secret");
        S3StorageProperties properties = new S3StorageProperties();
        properties.setBucket("zero-verse-s3");
        properties.setRegion("ap-northeast-2");
        properties.setEndpoint("http://127.0.0.1:4566");
        properties.setPathStyle(true);
        S3ObjectStore store = new S3ObjectStore(properties);
        try {
            String checksum = UploadChecksum.sha256Base64("bytes".getBytes(StandardCharsets.UTF_8));
            S3ObjectStore.PresignedPut put = store.presignPut(
                    properties.getBucket(), "zeroverse/dev/test/object", "image/jpeg", 5, checksum);

            assertThat(put.url()).startsWith("http://127.0.0.1:4566/");
            assertThat(put.headers().keySet().stream()
                    .map(key -> key.toLowerCase(Locale.ROOT)))
                    .contains("content-type", "x-amz-checksum-sha256", "if-none-match")
                    .doesNotContain("host", "content-length", "cache-control");
        } finally {
            store.closeClients();
        }
    }
}
