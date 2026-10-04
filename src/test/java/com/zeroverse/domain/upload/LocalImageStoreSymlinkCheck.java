package com.zeroverse.domain.upload;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.service.LocalImageStore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.web.multipart.MultipartFile;

/**
 * Linux/Docker용 독립 symlink 보안 검사.
 *
 * <p>Windows JUnit은 OS 권한 때문에 symlink를 만들 수 없으므로 이 클래스는 Gradle/JUnit 생명주기와
 * 분리된 executable main으로 실행한다. 실제 {@link LocalImageStore} 클래스는 최신 bootJar에서 로드하고,
 * fixture와 결과 파일은 컨테이너의 {@code /tmp} 아래에만 만든다.
 */
public final class LocalImageStoreSymlinkCheck {

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };

    private LocalImageStoreSymlinkCheck() {}

    public static void main(String[] args) throws Exception {
        Path fixtureRoot = Files.createTempDirectory(Path.of("/tmp"), "zeroverse-upload-symlink-");
        try {
            verifyTargetFileSymlink(fixtureRoot);
            verifyRootSymlink(fixtureRoot);
            verifyAncestorSymlink(fixtureRoot);
            System.out.println("SYMLINK_CHECK=PASS");
        } finally {
            deleteFixture(fixtureRoot);
            System.out.println("FIXTURE_CLEANED=" + !Files.exists(fixtureRoot));
        }
    }

    private static void verifyTargetFileSymlink(Path fixtureRoot) throws Exception {
        Path storage = Files.createDirectories(fixtureRoot.resolve("target-file-storage"));
        Path outside = fixtureRoot.resolve("outside-target.bin");
        byte[] marker = new byte[] {11, 22, 33, 44};
        Files.write(outside, marker);

        UUID id = UUID.randomUUID();
        Files.createSymbolicLink(storage.resolve(id.toString()), outside);
        LocalImageStore store = storeAt(storage);

        expectCode(() -> store.read(id.toString()), ErrorCode.UPLOAD_003, "target symlink read");
        expectCode(() -> store.store(id, multipart()), ErrorCode.COMMON_500, "target symlink write");
        assertBytes(outside, marker, "target symlink outside marker");
        System.out.println("TARGET_FILE_SYMLINK=read:UPLOAD_003,write:COMMON_500,marker:UNCHANGED");
    }

    private static void verifyRootSymlink(Path fixtureRoot) throws Exception {
        Path realRoot = Files.createDirectories(fixtureRoot.resolve("real-root"));
        Path marker = realRoot.resolve("marker.bin");
        byte[] markerBytes = new byte[] {55, 66, 77};
        Files.write(marker, markerBytes);
        Path linkedRoot = fixtureRoot.resolve("linked-root");
        Files.createSymbolicLink(linkedRoot, realRoot);

        LocalImageStore store = storeAt(linkedRoot);
        UUID id = UUID.randomUUID();
        expectCode(() -> store.read(id.toString()), ErrorCode.UPLOAD_003, "root symlink read");
        expectCode(() -> store.store(id, multipart()), ErrorCode.COMMON_500, "root symlink write");
        assertBytes(marker, markerBytes, "root symlink marker");
        assertThat(!Files.exists(realRoot.resolve(id.toString())), "root symlink wrote outside root");
        System.out.println("ROOT_SYMLINK=read:UPLOAD_003,write:COMMON_500,marker:UNCHANGED");
    }

    private static void verifyAncestorSymlink(Path fixtureRoot) throws Exception {
        Path realParent = Files.createDirectories(fixtureRoot.resolve("real-parent"));
        Path marker = realParent.resolve("marker.bin");
        byte[] markerBytes = new byte[] {88, 99, 100};
        Files.write(marker, markerBytes);
        Path linkedParent = fixtureRoot.resolve("linked-parent");
        Files.createSymbolicLink(linkedParent, realParent);
        Path configuredRoot = linkedParent.resolve("uploads");

        LocalImageStore store = storeAt(configuredRoot);
        UUID id = UUID.randomUUID();
        expectCode(() -> store.read(id.toString()), ErrorCode.UPLOAD_003, "ancestor symlink read");
        expectCode(() -> store.store(id, multipart()), ErrorCode.COMMON_500, "ancestor symlink write");
        assertBytes(marker, markerBytes, "ancestor symlink marker");
        assertThat(!Files.exists(realParent.resolve("uploads")), "ancestor symlink created outside root");
        System.out.println("ANCESTOR_SYMLINK=read:UPLOAD_003,write:COMMON_500,marker:UNCHANGED");
    }

    private static LocalImageStore storeAt(Path directory) {
        return new LocalImageStore(new UploadProperties(directory.toString()));
    }

    private static MultipartFile multipart() {
        return new BytesMultipartFile(JPEG);
    }

    private static void expectCode(ThrowingAction action, ErrorCode expected, String description)
            throws Exception {
        try {
            action.run();
        } catch (BusinessException e) {
            if (e.getErrorCode() != expected) {
                throw new AssertionError(
                        description + " expected " + expected + " but was " + e.getErrorCode(), e);
            }
            return;
        }
        throw new AssertionError(description + " did not reject");
    }

    private static void assertBytes(Path path, byte[] expected, String description) throws IOException {
        byte[] actual = Files.readAllBytes(path);
        if (!java.util.Arrays.equals(actual, expected)) {
            throw new AssertionError(description + " changed");
        }
    }

    private static void assertThat(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void deleteFixture(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final class BytesMultipartFile implements MultipartFile {
        private final byte[] bytes;

        private BytesMultipartFile(byte[] bytes) {
            this.bytes = bytes.clone();
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return "image.jpg";
        }

        @Override
        public String getContentType() {
            return "image/jpeg";
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File destination) throws IOException {
            Files.write(
                    destination.toPath(),
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        }
    }
}
