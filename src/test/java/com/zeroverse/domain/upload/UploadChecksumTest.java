package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.upload.service.UploadChecksum;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class UploadChecksumTest {

    @Test
    void acceptsOnlyCanonicalPaddedSha256Base64() {
        String checksum = UploadChecksum.sha256Base64("zeroverse".getBytes(StandardCharsets.UTF_8));

        assertThat(checksum).hasSize(44);
        assertThat(UploadChecksum.canonicalize(checksum)).isEqualTo(checksum);
    }

    @Test
    void rejectsWhitespaceOrNonCanonicalChecksumInsteadOfNormalizingIt() {
        String checksum = UploadChecksum.sha256Base64("zeroverse".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> UploadChecksum.canonicalize(" " + checksum))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.VALIDATION_001);
        assertThatThrownBy(() -> UploadChecksum.canonicalize(checksum.substring(0, 43)))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.VALIDATION_001);
    }
}
