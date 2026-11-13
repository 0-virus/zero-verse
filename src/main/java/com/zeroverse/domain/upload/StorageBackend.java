package com.zeroverse.domain.upload;

/** 바이트가 보관된 저장소 유형. 기존 V3 행은 LOCAL로 보존한다. */
public enum StorageBackend {
    LOCAL,
    S3
}
