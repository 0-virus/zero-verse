import { useEffect, useState } from 'react';
import { FormField } from '../components/ui/FormField';
import { Button } from '../components/ui/Button';
import { Panel } from '../components/ui/Panel';
import { getProfile, updateProfile, changePassword } from '../features/settings/settingsApi';
import { useAuth } from '../lib/authContext';
import { ApiRequestError } from '../lib/apiClient';

interface ProfileForm {
  name: string;
  nickname: string;
  bio: string;
  birthDate: string;
  profileImageUrl: string;
}

/**
 * `/settings` 프로필 및 비밀번호 설정 페이지.
 *
 * - 프로필 카드: name, nickname, bio, birthDate, profileImageUrl, email(비활성)
 * - 비밀번호 카드: 현재 비밀번호, 새 비밀번호
 *
 * 프로필 저장과 비밀번호 변경은 **독립된 상태**를 가진다 — 한쪽 실패가 다른 쪽의 성공 표시를
 * 지우면 사용자는 방금 저장한 것이 취소된 줄 안다.
 *
 * <p>디자인 §8.7의 <b>위험 구역(회원 탈퇴)은 구현하지 않는다</b>. 대응 FR/API가 없어 M2 범위
 * 밖이며, 비활성 장식으로라도 두면 없는 기능을 예고하게 된다(RISK-0004).
 */
export function SettingsProfilePage() {
  const { user, refreshUser } = useAuth();

  // 프로필 폼 상태
  const [profileForm, setProfileForm] = useState<ProfileForm>({
    name: '',
    nickname: '',
    bio: '',
    birthDate: '',
    profileImageUrl: '',
  });
  const [profileLoading, setProfileLoading] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);
  const [profileSuccess, setProfileSuccess] = useState(false);

  // 비밀번호 폼 상태
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
  });
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  // 초기 로드
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const profile = await getProfile();
        setProfileForm({
          name: profile.name || '',
          nickname: profile.nickname || '',
          bio: profile.bio || '',
          birthDate: profile.birthDate || '',
          profileImageUrl: profile.profileImageUrl || '',
        });
      } catch {
        setProfileError('프로필을 불러올 수 없습니다.');
      }
    };

    if (user) {
      loadProfile();
    }
  }, [user]);

  const handleProfileChange = (field: keyof ProfileForm, value: string) => {
    setProfileForm((prev) => ({ ...prev, [field]: value }));
    setProfileSuccess(false);
  };

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError(null);
    setProfileSuccess(false);
    setProfileLoading(true);

    try {
      await updateProfile({
        name: profileForm.name,
        nickname: profileForm.nickname,
        bio: profileForm.bio || undefined,
        birthDate: profileForm.birthDate || undefined,
        profileImageUrl: profileForm.profileImageUrl || undefined,
      });

      // 세션 갱신
      await refreshUser();
      setProfileSuccess(true);
      // 3초 후 메시지 숨기기
      setTimeout(() => setProfileSuccess(false), 3000);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        setProfileError(err.message || '프로필 저장에 실패했습니다.');
      } else {
        setProfileError('프로필 저장 중 오류가 발생했습니다.');
      }
    } finally {
      setProfileLoading(false);
    }
  };

  const handlePasswordChange = (field: string, value: string) => {
    setPasswordForm((prev) => ({ ...prev, [field]: value }));
    setPasswordSuccess(false);
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError(null);
    setPasswordSuccess(false);
    setPasswordLoading(true);

    try {
      await changePassword({
        currentPassword: passwordForm.currentPassword,
        newPassword: passwordForm.newPassword,
      });

      setPasswordSuccess(true);
      // 입력창 초기화
      setPasswordForm({ currentPassword: '', newPassword: '' });
      // 3초 후 메시지 숨기기
      setTimeout(() => setPasswordSuccess(false), 3000);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.code === 'USER_005') {
          setPasswordError('현재 비밀번호가 일치하지 않습니다.');
        } else {
          setPasswordError(err.message || '비밀번호 변경에 실패했습니다.');
        }
      } else {
        setPasswordError('비밀번호 변경 중 오류가 발생했습니다.');
      }
    } finally {
      setPasswordLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <h1 className="sr-only">프로필 설정</h1>
      {/* 프로필 카드 */}
      <Panel title="프로필" tone="primary">
        <form onSubmit={handleProfileSubmit} className="space-y-4 px-5 py-6">
          <div className="grid grid-cols-[110px_1fr] gap-4">
            {/* 좌: 아바타 슬롯 */}
            <div className="flex flex-col items-center gap-2">
              <div className="flex h-24 w-24 items-center justify-center border-[3px] border-ink bg-surface">
                <span className="text-3xl">🪐</span>
              </div>
              <button type="button" className="text-xs text-text-muted hover:text-ink">
                변경
              </button>
            </div>

            {/* 우: 프로필 필드 */}
            <div className="space-y-4">
              <FormField
                label="닉네임"
                type="text"
                value={profileForm.nickname}
                onChange={(e) => handleProfileChange('nickname', e.target.value)}
                required
              />
              <FormField
                label="이메일"
                type="email"
                value={user?.email || ''}
                disabled
              />
              <FormField
                label="이름"
                type="text"
                value={profileForm.name}
                onChange={(e) => handleProfileChange('name', e.target.value)}
                required
              />
              <div>
                <label htmlFor="bio" className="mb-[5px] block text-xs font-bold">
                  한 줄 소개
                </label>
                <textarea
                  id="bio"
                  value={profileForm.bio}
                  onChange={(e) => handleProfileChange('bio', e.target.value)}
                  className="w-full border-2 border-ink bg-surface px-3 py-2.5 text-[13px] outline-0"
                  rows={3}
                  maxLength={200}
                />
              </div>
            </div>
          </div>

          {profileError && (
            <div className="border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
              {profileError}
            </div>
          )}

          {profileSuccess && (
            <div className="border-2 border-success bg-success-bg px-4 py-3 text-sm text-success">
              프로필이 저장되었습니다.
            </div>
          )}

          <Button
            variant="primary"
            size="md"
            type="submit"
            disabled={profileLoading}
            className="w-full"
          >
            {profileLoading ? '저장 중...' : '저장'}
          </Button>
        </form>
      </Panel>

      {/* 비밀번호 변경 카드 */}
      <Panel title="비밀번호 변경" tone="primary">
        <form onSubmit={handlePasswordSubmit} className="space-y-4 px-5 py-6">
          <div className="grid grid-cols-2 gap-4">
            <FormField
              label="현재 비밀번호"
              type="password"
              value={passwordForm.currentPassword}
              onChange={(e) => handlePasswordChange('currentPassword', e.target.value)}
              required
            />
            <FormField
              label="새 비밀번호"
              type="password"
              value={passwordForm.newPassword}
              onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
              hint="8~64자, 영문·숫자·특수문자"
              required
            />
          </div>

          {passwordError && (
            <div className="border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
              {passwordError}
            </div>
          )}

          {passwordSuccess && (
            <div className="border-2 border-success bg-success-bg px-4 py-3 text-sm text-success">
              비밀번호가 변경되었습니다.
            </div>
          )}

          <Button
            variant="ink"
            size="md"
            type="submit"
            disabled={passwordLoading}
          >
            {passwordLoading ? '변경 중...' : '변경'}
          </Button>
        </form>
      </Panel>
    </div>
  );
}
