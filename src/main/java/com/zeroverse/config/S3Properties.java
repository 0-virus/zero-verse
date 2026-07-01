package com.zeroverse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {
    private String bucketName;
    private String region;
    private String accessKey;
    private String secretKey;
    private String endpoint;
    private Boolean usePathStyle;

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Boolean getUsePathStyle() {
        return usePathStyle;
    }

    public void setUsePathStyle(Boolean usePathStyle) {
        this.usePathStyle = usePathStyle;
    }
}
