package com.zeroverse.domain.upload.service;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.upload.config.UploadProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 파일 바이트 검증과 Git 제외 로컬 저장을 담당한다. 경로 자체는 API 응답에 노출하지 않는다. */
@Component
public class LocalImageStore {

    public static final int MAX_BYTES = 5_242_880;
    public static final Set<String> SUPPORTED_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final UploadProperties properties;
    private final Tika tika;

    @Autowired
    public LocalImageStore(UploadProperties properties) {
        this(properties, new Tika());
    }

    LocalImageStore(UploadProperties properties, Tika tika) {
        this.properties = properties;
        this.tika = tika;
    }

    /** 실제 multipart stream을 bounded read한 뒤 MIME을 확인하고 UUID 파일로 저장한다. */
    public StoredImage store(UUID id, MultipartFile file) {
        if (id == null || file == null) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        }

        byte[] bytes;
        try (InputStream input = file.getInputStream()) {
            bytes = readBounded(input);
        } catch (TooLargeException e) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_001);
        }

        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.UPLOAD_002);
        }

        String declaredType = normalizeType(file.getContentType());
        String detectedType = normalizeType(tika.detect(bytes));
        if (!SUPPORTED_TYPES.contains(declaredType)
                || !SUPPORTED_TYPES.contains(detectedType)
                || !declaredType.equals(detectedType)) {
            throw new BusinessException(ErrorCode.UPLOAD_001);
        }

        String storageKey = id.toString();
        Path target = resolveStoragePath(storageKey, false);
        boolean created = false;
        try {
            Files.createDirectories(rootPath());
            if (hasSymlinkAncestor(rootPath())
                    || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("storage target already exists");
            }
            try (FileChannel channel = FileChannel.open(
                    target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            return new StoredImage(storageKey, declaredType, bytes.length);
        } catch (IOException e) {
            if (created) {
                deleteQuietly(target);
            }
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /** 저장된 파일을 bounded read한다. DB에 없는 경로·symlink·과대 파일은 동일하게 404로 숨긴다. */
    public byte[] read(String storageKey) {
        Path target = resolveStoragePath(storageKey, true);
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        try (InputStream input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            return readBounded(input);
        } catch (TooLargeException | IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
    }

    /** DB metadata와 실제 파일 길이·MIME이 모두 일치하는지 확인한 binary read. */
    public byte[] readValidated(String storageKey, String expectedType, long expectedSize) {
        byte[] bytes = read(storageKey);
        String detectedType = normalizeType(tika.detect(bytes));
        if (bytes.length != expectedSize
                || !SUPPORTED_TYPES.contains(detectedType)
                || !normalizeType(expectedType).equals(detectedType)) {
            throw new BusinessException(ErrorCode.UPLOAD_003);
        }
        return bytes;
    }

    /** 요청 중 생성한 파일을 정리한다. symlink는 절대 따라가지 않는다. */
    public void delete(String storageKey) {
        Path target;
        try {
            target = resolveStoragePath(storageKey, true);
        } catch (BusinessException e) {
            return;
        }
        if (hasSymlinkAncestor(rootPath()) || Files.isSymbolicLink(target)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // rollback 정리 실패는 원래 DB/검증 오류를 가리지 않아야 한다.
        }
    }

    private Path rootPath() {
        return properties.directoryPath().toAbsolutePath().normalize();
    }

    private Path resolveStoragePath(String storageKey, boolean missingIs404) {
        UUID id;
        try {
            id = UUID.fromString(storageKey);
        } catch (RuntimeException e) {
            throw new BusinessException(missingIs404 ? ErrorCode.UPLOAD_003 : ErrorCode.COMMON_500);
        }
        if (!id.toString().equals(storageKey)) {
            throw new BusinessException(missingIs404 ? ErrorCode.UPLOAD_003 : ErrorCode.COMMON_500);
        }

        Path root = rootPath();
        if (hasSymlinkAncestor(root)) {
            throw new BusinessException(missingIs404 ? ErrorCode.UPLOAD_003 : ErrorCode.COMMON_500);
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root) || !root.equals(target.getParent())) {
            throw new BusinessException(missingIs404 ? ErrorCode.UPLOAD_003 : ErrorCode.COMMON_500);
        }
        return target;
    }

    private static String normalizeType(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private static byte[] readBounded(InputStream input)
            throws IOException, TooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_BYTES, 8192));
        byte[] buffer = new byte[8192];
        int read;
        long total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new TooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void deleteQuietly(Path target) {
        try {
            if (target != null
                    && !Files.isSymbolicLink(target)
                    && !hasSymlinkAncestor(target)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // keep the original failure as the externally visible error
        }
    }

    /**
     * 설정된 저장 경로의 어느 구성요소도 symlink를 통과하지 않는지 확인한다.
     * 존재하지 않는 하위 구성요소는 {@code Files.isSymbolicLink}가 false를 반환하므로
     * 최초 저장 시에도 안전하게 검사할 수 있다.
     */
    private static boolean hasSymlinkAncestor(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        if (current == null) {
            current = Path.of("");
        }
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                return true;
            }
        }
        return false;
    }

    private static final class TooLargeException extends Exception {
        private TooLargeException() {}
    }

    public record StoredImage(String storageKey, String contentType, long size) {}
}
