package com.zeroverse.domain.upload;

/** presigned PUT intent의 명시적 상태 전이. */
public enum UploadIntentStatus {
    PENDING,
    READY,
    DELETING,
    DELETED
}
