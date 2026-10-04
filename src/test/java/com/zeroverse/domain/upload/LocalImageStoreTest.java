package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.service.LocalImageStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalImageStoreTest {

    private static final int MAX_BYTES = 5_242_880;
    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };
    private static final byte[] PNG = {
        (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10,
        0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1
    };
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0};
    private static final byte[] WEBP = {
        'R', 'I', 'F', 'F', 12, 0, 0, 0, 'W', 'E', 'B', 'P'
    };

    @TempDir
    Path tempDirectory;

    @Test
    void storesAndReadsAnImageAtTheExactFiveMiBBoundary() throws Exception {
        LocalImageStore store = store();
        byte[] bytes = padded(JPEG, MAX_BYTES);
        UUID id = UUID.randomUUID();

        LocalImageStore.StoredImage stored = store.store(
                id, new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes));

        assertThat(stored.storageKey()).isEqualTo(id.toString());
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.size()).isEqualTo(MAX_BYTES);
        assertThat(store.read(stored.storageKey())).isEqualTo(bytes);
        assertThat(new LocalImageStore(new UploadProperties(tempDirectory.toString()))
                        .read(stored.storageKey()))
                .isEqualTo(bytes);
        assertThat(Files.exists(tempDirectory.resolve(id.toString()))).isTrue();
    }

    @Test
    void acceptsAllFourDeclaredAndDetectedImageMimeTypes() {
        LocalImageStore store = store();
        java.util.Map<String, byte[]> fixtures = java.util.Map.of(
                "image/jpeg", JPEG,
                "image/png", PNG,
                "image/webp", WEBP,
                "image/gif", GIF);

        fixtures.forEach((contentType, bytes) -> {
            LocalImageStore.StoredImage stored = store.store(
                    UUID.randomUUID(),
                    new MockMultipartFile("file", "image", contentType, bytes));
            assertThat(stored.contentType()).isEqualTo(contentType);
            assertThat(stored.size()).isEqualTo(bytes.length);
        });
    }

    @Test
    void rejectsOneByteOverTheFiveMiBLimitBeforeWriting() throws Exception {
        LocalImageStore store = store();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> store.store(
                id,
                new MockMultipartFile(
                        "file", "too-large.jpg", "image/jpeg", padded(JPEG, MAX_BYTES + 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_002);

        assertThat(filesIn(tempDirectory)).isEmpty();
    }

    @Test
    void neverDeletesAnExistingFileWhenCreateNewCollides() throws Exception {
        LocalImageStore store = store();
        UUID id = UUID.randomUUID();
        Path existing = tempDirectory.resolve(id.toString());
        byte[] existingBytes = {9, 8, 7};
        Files.write(existing, existingBytes);

        assertThatThrownBy(() -> store.store(
                id, new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG)))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.COMMON_500);
        assertThat(Files.readAllBytes(existing)).isEqualTo(existingBytes);
    }

    @Test
    void rejectsAnEmptyFileAsAnInvalidSize() throws Exception {
        LocalImageStore store = store();

        assertThatThrownBy(() -> store.store(
                UUID.randomUUID(), new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0])))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_002);

        assertThat(filesIn(tempDirectory)).isEmpty();
    }

    @Test
    void rejectsWhenDeclaredMimeDoesNotMatchDetectedBytes() {
        LocalImageStore store = store();

        assertThatThrownBy(() -> store.store(
                UUID.randomUUID(), new MockMultipartFile("file", "wrong.png", "image/jpeg", PNG)))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_001);
    }

    @Test
    void rejectsAStorageKeyThatEscapesTheConfiguredDirectory() {
        LocalImageStore store = store();

        assertThatThrownBy(() -> store.read("../outside"))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_003);
    }

    private LocalImageStore store() {
        UploadProperties properties = new UploadProperties();
        properties.setDirectory(tempDirectory.toString());
        return new LocalImageStore(properties);
    }

    private static byte[] padded(byte[] prefix, int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        return bytes;
    }

    private static java.util.List<Path> filesIn(Path directory) throws Exception {
        if (!Files.exists(directory)) {
            return java.util.List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.toList();
        }
    }
}
