package com.zeroverse.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeroverse.domain.upload.service.UploadService;
import com.zeroverse.domain.user.dto.UserSettingsDtos;
import com.zeroverse.domain.user.dto.UserSettingsDtos.UpdateProfileRequest;
import com.zeroverse.domain.user.entity.User;
import com.zeroverse.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserSettingsProfileBindingTest {

    @Test
    void managedProfileUrlIsBoundBeforeUserSnapshotIsSaved() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "profile-binding@test", "encoded", "Profile User", "profile-user",
                LocalDate.of(1990, 1, 1));
        String imageUrl = "/api/v1/uploads/00000000-0000-0000-0000-000000000001/content";
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl(imageUrl)).thenReturn(true);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);
        UserSettingsDtos.UserProfileResponse response = service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "profile-user", null, null, imageUrl));

        verify(userRepository).findByIdAndDeletedAtIsNullForUpdate(1L);
        verify(uploadService).bindProfileImage(1L, imageUrl);
        assertThat(response.profileImageUrl()).isEqualTo(imageUrl);
    }

    @Test
    void clearingManagedProfileUrlDetachesItsBinding() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "profile-clear@test", "encoded", "Profile User", "profile-clear",
                LocalDate.of(1990, 1, 1));
        String imageUrl = "/api/v1/uploads/00000000-0000-0000-0000-000000000002/content";
        user.updateProfile("Profile User", "profile-clear", null, null, imageUrl);
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl(imageUrl)).thenReturn(true);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);
        UserSettingsDtos.UserProfileResponse response = service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "profile-clear", null, null, null));

        verify(userRepository).findByIdAndDeletedAtIsNullForUpdate(1L);
        verify(uploadService).bindProfileImage(1L, null);
        assertThat(response.profileImageUrl()).isNull();
    }

    @Test
    void unchangedLegacyExternalProfileUrlRemainsReadableWithoutBinding() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "legacy-profile@test", "encoded", "Profile User", "legacy-profile",
                LocalDate.of(1990, 1, 1));
        String legacyUrl = "https://legacy.example/avatar.jpg";
        user.updateProfile("Profile User", "legacy-profile", null, null, legacyUrl);
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl(legacyUrl)).thenReturn(false);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);
        UserSettingsDtos.UserProfileResponse response = service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "legacy-profile", null, null, legacyUrl));

        assertThat(response.profileImageUrl()).isEqualTo(legacyUrl);
        verify(uploadService, never()).bindProfileImage(anyLong(), any());
        verify(userRepository).findByIdAndDeletedAtIsNullForUpdate(1L);
    }

    @Test
    void rejectsNewExternalProfileUrlWithUpload004() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "new-external@test", "encoded", "Profile User", "new-external",
                LocalDate.of(1990, 1, 1));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl("https://new.example/avatar.jpg")).thenReturn(false);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);

        assertThatThrownBy(() -> service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "new-external", null, null,
                        "https://new.example/avatar.jpg")))
                .isInstanceOf(com.zeroverse.common.exception.BusinessException.class)
                .extracting(com.zeroverse.common.exception.BusinessException.class::cast)
                .extracting(com.zeroverse.common.exception.BusinessException::getErrorCode)
                .isEqualTo(com.zeroverse.common.exception.ErrorCode.UPLOAD_004);
        verify(uploadService, never()).bindProfileImage(anyLong(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBlankProfileUrlInsteadOfTreatingItAsClear() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "blank-profile@test", "encoded", "Profile User", "blank-profile",
                LocalDate.of(1990, 1, 1));
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl(" ")).thenReturn(false);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);

        assertThatThrownBy(() -> service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "blank-profile", null, null, " ")))
                .isInstanceOf(com.zeroverse.common.exception.BusinessException.class)
                .extracting(com.zeroverse.common.exception.BusinessException.class::cast)
                .extracting(com.zeroverse.common.exception.BusinessException::getErrorCode)
                .isEqualTo(com.zeroverse.common.exception.ErrorCode.UPLOAD_004);
        verify(uploadService, never()).bindProfileImage(anyLong(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsReplacingManagedProfileUrlWithExternalValue() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UploadService uploadService = mock(UploadService.class);
        User user = User.register(
                "managed-external@test", "encoded", "Profile User", "managed-external",
                LocalDate.of(1990, 1, 1));
        String currentUrl = "/api/v1/uploads/00000000-0000-0000-0000-000000000003/content";
        user.updateProfile("Profile User", "managed-external", null, null, currentUrl);
        when(userRepository.findByIdAndDeletedAtIsNullForUpdate(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByNicknameAndIdNotAndDeletedAtIsNull(any(), anyLong()))
                .thenReturn(false);
        when(uploadService.isManagedImageUrl(currentUrl)).thenReturn(true);
        when(uploadService.isManagedImageUrl("https://new.example/avatar.jpg")).thenReturn(false);

        UserSettingsService service = new UserSettingsService(
                userRepository, passwordEncoder, uploadService);

        assertThatThrownBy(() -> service.updateProfile(1L,
                new UpdateProfileRequest("Profile User", "managed-external", null, null,
                        "https://new.example/avatar.jpg")))
                .isInstanceOf(com.zeroverse.common.exception.BusinessException.class)
                .extracting(com.zeroverse.common.exception.BusinessException.class::cast)
                .extracting(com.zeroverse.common.exception.BusinessException::getErrorCode)
                .isEqualTo(com.zeroverse.common.exception.ErrorCode.UPLOAD_004);
        verify(uploadService, never()).bindProfileImage(anyLong(), any());
        verify(userRepository, never()).saveAndFlush(any());
    }
}
