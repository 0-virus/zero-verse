package com.zeroverse.domain.upload.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 운영 S3 위치와 SDK timeout 설정. 자격증명은 AWS default provider chain에서만 읽는다. */
@Component
@ConfigurationProperties(prefix = "zeroverse.upload.s3")
public class S3StorageProperties {

    private String bucket;
    private String region;
    private String prefix;
    private String endpoint;
    private boolean pathStyle;
    private Duration attemptTimeout = Duration.ofSeconds(5);
    private Duration apiCallTimeout = Duration.ofSeconds(10);

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isPathStyle() {
        return pathStyle;
    }

    public void setPathStyle(boolean pathStyle) {
        this.pathStyle = pathStyle;
    }

    public Duration getAttemptTimeout() {
        return attemptTimeout;
    }

    public void setAttemptTimeout(Duration attemptTimeout) {
        this.attemptTimeout = attemptTimeout;
    }

    public Duration getApiCallTimeout() {
        return apiCallTimeout;
    }

    public void setApiCallTimeout(Duration apiCallTimeout) {
        this.apiCallTimeout = apiCallTimeout;
    }

    public String normalizedPrefix() {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String value = prefix.trim();
        return value.endsWith("/") ? value : value + "/";
    }
}
