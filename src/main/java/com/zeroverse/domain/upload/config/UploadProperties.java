package com.zeroverse.domain.upload.config;

import java.nio.file.Path;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 로컬 M4 이미지 저장 경계. 운영 S3 설정과 섞이지 않도록 별도 prefix를 사용한다. */
@Component
@ConfigurationProperties(prefix = "zeroverse.upload")
public class UploadProperties {

    private String directory = ".local-data/uploads";

    public UploadProperties() {}

    public UploadProperties(String directory) {
        this.directory = directory;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public Path directoryPath() {
        if (directory == null || directory.isBlank()) {
            return Path.of(".local-data", "uploads").toAbsolutePath().normalize();
        }
        return Path.of(directory).toAbsolutePath().normalize();
    }

    public String contentUrl(UUID id) {
        return "/api/v1/uploads/" + id + "/content";
    }
}
