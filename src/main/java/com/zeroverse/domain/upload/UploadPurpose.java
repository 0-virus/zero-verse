package com.zeroverse.domain.upload;

import java.util.Locale;

/** 이미지가 연결될 리소스의 종류. 로컬 M4는 이 세 목적만 허용한다. */
public enum UploadPurpose {
    PROFILE_IMAGE,
    POST_THUMBNAIL,
    POST_IMAGE;

    public static UploadPurpose from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return value.trim().toUpperCase(Locale.ROOT).equals(value.trim())
                    ? valueOf(value.trim())
                    : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
