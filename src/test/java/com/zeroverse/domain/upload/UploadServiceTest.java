package com.zeroverse.domain.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zeroverse.common.exception.BusinessException;
import com.zeroverse.common.exception.ErrorCode;
import com.zeroverse.domain.blog.entity.Blog;
import com.zeroverse.domain.post.entity.Post;
import com.zeroverse.domain.post.entity.PostImage;
import com.zeroverse.domain.post.repository.PostImageRepository;
import com.zeroverse.domain.post.service.PostAccessPolicy;
import com.zeroverse.domain.upload.config.UploadProperties;
import com.zeroverse.domain.upload.dto.UploadDtos.ImageContent;
import com.zeroverse.domain.upload.dto.UploadDtos.UploadResponse;
import com.zeroverse.domain.upload.entity.ImageUpload;
import com.zeroverse.domain.upload.repository.ImageUploadRepository;
import com.zeroverse.domain.upload.service.LocalImageStore;
import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

class UploadServiceTest {

    private static final byte[] JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0,
        (byte) 0xFF, (byte) 0xD9
    };

    @TempDir
    Path tempDirectory;

    private ImageUploadRepository imageUploadRepository;
    private PostImageRepository postImageRepository;
    private UserRepository userRepository;
    private PostAccessPolicy postAccessPolicy;
    private EntityManager entityManager;
    private LocalImageStore localImageStore;
    private UploadService service;
    private User owner;

    @BeforeEach
    void setUp() {
        imageUploadRepository = mock(ImageUploadRepository.class);
        userRepository = mock(UserRepository.class);
        postAccessPolicy = mock(PostAccessPolicy.class);
        postImageRepository = mock(PostImageRepository.class);
        entityManager = mock(EntityManager.class);

        owner = mock(User.class);
        when(owner.getId()).thenReturn(1L);
        when(owner.isActive()).thenReturn(true);
        when(owner.isDeleted()).thenReturn(false);
        when(owner.getProfileImageUrl()).thenReturn(null);
        when(userRepository.findByIdAndDeletedAtIsNull(anyLong())).thenReturn(Optional.of(owner));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(anyLong())).thenReturn(Optional.of(owner));

        UploadProperties properties = new UploadProperties(tempDirectory.toString());
        localImageStore = new LocalImageStore(properties);
        service = new UploadService(
                imageUploadRepository,
                userRepository,
                localImageStore,
                properties,
                postAccessPolicy,
                postImageRepository,
                entityManager);
    }

    @Test
    void createsMetadataAfterTheFileIsStored() {
        UploadResponse response = service.upload(
                1L, new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG),
                UploadPurpose.PROFILE_IMAGE);

        assertThat(response.id()).isNotNull();
        assertThat(response.imageUrl()).isEqualTo(
                "/api/v1/uploads/" + response.id() + "/content");
        assertThat(response.contentType()).isEqualTo("image/jpeg");
        assertThat(response.size()).isEqualTo(JPEG.length);
        assertThat(response.purpose()).isEqualTo(UploadPurpose.PROFILE_IMAGE);
        assertThat(Files.exists(tempDirectory.resolve(response.id().toString()))).isTrue();
    }

    @Test
    void removesOnlyThisRequestFileWhenMetadataFlushFails() {
        when(imageUploadRepository.saveAndFlush(any(ImageUpload.class)))
                .thenThrow(new DataIntegrityViolationException("metadata failure"));

        assertThatThrownBy(() -> service.upload(
                1L, new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG),
                UploadPurpose.PROFILE_IMAGE))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(filesIn(tempDirectory)).isEmpty();
    }

    @Test
    void allowsOnlyTheOwnerToReadAnUnboundUpload() throws Exception {
        UUID id = UUID.randomUUID();
        LocalImageStore.StoredImage stored = localImageStore.store(
                id, new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG));
        ImageUpload metadata = ImageUpload.create(
                id, owner, UploadPurpose.POST_IMAGE, stored.storageKey(),
                "/api/v1/uploads/" + id + "/content", stored.contentType(), stored.size());
        when(imageUploadRepository.findById(id)).thenReturn(Optional.of(metadata));

        ImageContent ownerContent = service.read(id, 1L);
        assertThat(ownerContent.bytes()).isEqualTo(JPEG);

        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(mock(User.class)));
        assertThatThrownBy(() -> service.read(id, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_003);
    }

    @Test
    void rejectsMetadataThatPointsAtAnotherUuidStorageKey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID differentStorageId = UUID.randomUUID();
        LocalImageStore.StoredImage stored = localImageStore.store(
                differentStorageId,
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG));
        ImageUpload metadata = ImageUpload.create(
                id, owner, UploadPurpose.POST_IMAGE, stored.storageKey(),
                "/api/v1/uploads/" + id + "/content", stored.contentType(), stored.size());
        when(imageUploadRepository.findById(id)).thenReturn(Optional.of(metadata));

        assertThatThrownBy(() -> service.read(id, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_003);
    }

    @Test
    void permitsAProfileImageOnlyWhileItIsTheCurrentProfileConnection() throws Exception {
        UUID id = UUID.randomUUID();
        String url = "/api/v1/uploads/" + id + "/content";
        when(owner.getProfileImageUrl()).thenReturn(url);
        LocalImageStore.StoredImage stored = localImageStore.store(
                id, new MockMultipartFile("file", "photo.jpg", "image/jpeg", JPEG));
        ImageUpload metadata = ImageUpload.create(
                id, owner, UploadPurpose.PROFILE_IMAGE, stored.storageKey(), url,
                stored.contentType(), stored.size());
        metadata.bindToProfile(1L, LocalDateTime.now());
        when(imageUploadRepository.findById(id)).thenReturn(Optional.of(metadata));

        assertThat(service.read(id, null).bytes()).isEqualTo(JPEG);
    }

    @Test
    void refusesToBindAnAlreadyBoundImageToAnotherPost() {
        Post firstPost = post(10L);
        Post secondPost = post(11L);
        UUID id = UUID.randomUUID();
        String url = "/api/v1/uploads/" + id + "/content";
        ImageUpload metadata = ImageUpload.create(
                id, owner, UploadPurpose.POST_IMAGE, id.toString(), url,
                "image/jpeg", JPEG.length);
        metadata.bindToPost(firstPost, ImageUpload.RESOURCE_BODY, LocalDateTime.now());
        when(imageUploadRepository.findCurrentlyBoundByPostId(10L)).thenReturn(List.of());
        when(imageUploadRepository.findCurrentlyBoundByPostId(11L)).thenReturn(List.of());
        when(imageUploadRepository.findByImageUrlForUpdate(url)).thenReturn(Optional.of(metadata));
        when(postImageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(postImage(firstPost, url)));
        when(postImageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(11L))
                .thenReturn(List.of(postImage(secondPost, url)));

        service.bindPostImages(1L, firstPost, Set.of(url), null);
        assertThatThrownBy(() -> service.bindPostImages(1L, secondPost, Set.of(url), null))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_004);
    }

    @Test
    void reattachesADetachedImageOnlyToItsOriginalPost() {
        Post originalPost = post(10L);
        UUID id = UUID.randomUUID();
        String url = "/api/v1/uploads/" + id + "/content";
        LocalDateTime firstBoundAt = LocalDateTime.now().minusMinutes(2);
        ImageUpload metadata = ImageUpload.create(
                id, owner, UploadPurpose.POST_IMAGE, id.toString(), url,
                "image/jpeg", JPEG.length);
        metadata.bindToPost(originalPost, ImageUpload.RESOURCE_BODY, firstBoundAt);
        metadata.detach(LocalDateTime.now().minusMinutes(1));
        when(imageUploadRepository.findCurrentlyBoundByPostId(10L)).thenReturn(List.of());
        when(imageUploadRepository.findByImageUrlForUpdate(url)).thenReturn(Optional.of(metadata));
        when(postImageRepository.findByPostIdAndDeletedAtIsNullOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(postImage(originalPost, url)));

        service.bindPostImages(1L, originalPost, Set.of(url), null);

        assertThat(metadata.isCurrentlyBound()).isTrue();
        assertThat(metadata.getBoundAt()).isEqualTo(firstBoundAt);
    }

    @Test
    void rejectsAnExternalUrlAsANewProfileConnection() {
        assertThatThrownBy(() -> service.bindProfileImage(1L, "https://cdn.example/avatar.jpg"))
                .isInstanceOf(BusinessException.class)
                .extracting(BusinessException.class::cast)
                .extracting(BusinessException::getErrorCode)
                .isEqualTo(ErrorCode.UPLOAD_004);
    }

    private Post post(Long id) {
        Post post = mock(Post.class);
        Blog blog = mock(Blog.class);
        when(post.getId()).thenReturn(id);
        when(post.getUser()).thenReturn(owner);
        when(post.getBlog()).thenReturn(blog);
        when(post.isDeleted()).thenReturn(false);
        when(blog.isDeleted()).thenReturn(false);
        return post;
    }

    private PostImage postImage(Post post, String url) {
        return PostImage.create(post, url, null, 0);
    }

    private static java.util.List<Path> filesIn(Path directory) {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> paths = Files.list(directory)) {
            return paths.toList();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
