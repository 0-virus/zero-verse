package com.zeroverse.domain.upload.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/** SHA-256 checksums used by the S3 upload contract. */
public final class UploadChecksum {

    private static final int SHA256_BYTES = 32;
    private static final int ENCODED_LENGTH = 44;

    private UploadChecksum() {}

    public static String sha256Base64(byte[] bytes) {
        if (bytes == null) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JRE", e);
        }
    }

    /**
     * Validates, without trimming or case-folding, a padded base64 SHA-256 value.
     */
    public static String canonicalize(String value) {
        if (value == null || value.length() != ENCODED_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length != SHA256_BYTES
                    || !Base64.getEncoder().encodeToString(decoded).equals(value)) {
                throw new BusinessException(ErrorCode.VALIDATION_001);
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_001);
        }
    }
}
