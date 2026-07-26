import { useEffect, useState } from 'react';
import { FormField } from '../components/ui/FormField';
import { Button } from '../components/ui/Button';
import { Panel } from '../components/ui/Panel';
import { getProfile, updateProfile, changePassword } from '../features/settings/settingsApi';
import { getBlog, updateBlog } from '../features/blog/blogApi';
import { useAuth } from '../lib/authContext';
import { ApiRequestError } from '../lib/apiClient';

interface ProfileForm {
  name: string;
  nickname: string;
  bio: string;
  birthDate: string;
  profileImageUrl: string;
}

interface BlogForm {
  title: string;
  urlSlug: string;
  description: string;
}

/**
 * `/settings` 프로필 · 블로그 · 비밀번호 설정 페이지.
 *
 * - 프로필 카드: nickname, email(비활성), name, bio, birthDate, profileImageUrl (FR-SETTINGS-01)
 * - 블로그 카드: title, urlSlug, description (FR-SETTINGS-03)
 * - 비밀번호 카드: 현재 비밀번호, 새 비밀번호 (FR-SETTINGS-02)
 *
 * <p>세 카드는 **독립된 상태와 독립된 저장 버튼**을 가진다. 한쪽 실패가 다른 쪽의 성공 표시를
 * 지우면 사용자는 방금 저장한 것이 취소된 줄 안다. 저장 시 각 카드는 <b>자기 API만</b> 호출한다.
 *
 * <p>`한 줄 소개`는 사용자 자기소개(`user.bio`)이고, 블로그 카드의 `블로그 소개`는
 * `blog.description`이다. 서로 다른 필드라 라벨을 분리했다.
 *
 * <p>slug 입력을 두는 이유: 초기 설정 화면이 "설정에서 나중에 변경할 수 있어요"라고 약속하며
 * (ADR-0004), FR-SETTINGS-03의 수정 가능 필드에 `url_slug`가 포함된다. 디자인 정본의 설정
 * 화면에는 slug 입력이 그려져 있지 않으나, 그 사실만으로 요구사항 필드를 지우지 않는다.
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

  // 블로그 폼 상태
  const [blogForm, setBlogForm] = useState<BlogForm>({
    title: '',
    urlSlug: '',
    description: '',
  });
  const [blogLoading, setBlogLoading] = useState(false);
  const [blogError, setBlogError] = useState<string | null>(null);
  const [blogSuccess, setBlogSuccess] = useState(false);

  // 비밀번호 폼 상태
  const [passwordForm, setPasswordForm] = useState({
    currentPassword: '',
    newPassword: '',
  });
  const [passwordLoading, setPasswordLoading] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [passwordSuccess, setPasswordSuccess] = useState(false);

  // 초기 로드 — 프로필과 블로그는 서로 독립이라 한쪽 실패가 다른 쪽을 비우지 않는다.
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

    const loadBlog = async () => {
      try {
        const blog = await getBlog();
        setBlogForm({
          title: blog.title || '',
          urlSlug: blog.urlSlug || '',
          description: blog.description || '',
        });
      } catch {
        setBlogError('블로그 정보를 불러올 수 없습니다.');
      }
    };

    if (user) {
      loadProfile();
      loadBlog();
    }
  }, [user]);

  const handleProfileChange = (field: keyof ProfileForm, value: string) => {
    setProfileForm((prev) => ({ ...prev, [field]: value }));
    setProfileSuccess(false);
  };

  const handleBlogChange = (field: keyof BlogForm, value: string) => {
    setBlogForm((prev) => ({ ...prev, [field]: value }));
    setBlogSuccess(false);
  };

  const handleBlogSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBlogError(null);
    setBlogSuccess(false);
    setBlogLoading(true);

    try {
      const updated = await updateBlog({
        title: blogForm.title,
        urlSlug: blogForm.urlSlug,
        description: blogForm.description || undefined,
      });

      // 서버가 정규화한 값을 그대로 되비춘다.
      setBlogForm({
        title: updated.title || '',
        urlSlug: updated.urlSlug || '',
        description: updated.description || '',
      });
      setBlogSuccess(true);
      setTimeout(() => setBlogSuccess(false), 3000);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        if (err.code === 'BLOG_002') {
          setBlogError('이미 사용 중인 주소입니다. 다른 주소를 입력하세요.');
        } else if (err.code === 'BLOG_003') {
          setBlogError('유효하지 않은 주소 형식입니다. 영문 소문자·숫자·하이픈, 3~30자를 입력하세요.');
        } else {
          setBlogError(err.message || '블로그 저장에 실패했습니다.');
        }
      } else {
        setBlogError('블로그 저장 중 오류가 발생했습니다.');
      }
    } finally {
      setBlogLoading(false);
    }
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

            {/* 우: 프로필 필드 — 정본 §8.7의 `90px 1fr` 그리드(라벨이 입력 왼쪽). */}
            <div className="flex flex-col gap-[14px]">
              <FormField
                label="닉네임"
                type="text"
                value={profileForm.nickname}
                onChange={(e) => handleProfileChange('nickname', e.target.value)}
                orientation="inline"
                surface="warm"
                required
              />
              <FormField
                label="이메일"
                type="email"
                value={user?.email || ''}
                orientation="inline"
                disabled
              />
              <FormField
                label="이름"
                type="text"
                value={profileForm.name}
                onChange={(e) => handleProfileChange('name', e.target.value)}
                orientation="inline"
                surface="warm"
                required
              />
              {/* textarea는 라벨을 위쪽에 맞춘다(정본 `align-items:start` + 라벨 `padding-top:8px`). */}
              <div className="grid grid-cols-[90px_1fr] items-start gap-3">
                <label htmlFor="bio" className="pt-2 text-[13px] font-semibold text-text-muted">
                  한 줄 소개
                </label>
                <textarea
                  id="bio"
                  value={profileForm.bio}
                  onChange={(e) => handleProfileChange('bio', e.target.value)}
                  className="min-h-14 w-full resize-y border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0"
                  maxLength={200}
                />
              </div>
              <FormField
                label="생년월일"
                type="date"
                value={profileForm.birthDate}
                onChange={(e) => handleProfileChange('birthDate', e.target.value)}
                orientation="inline"
                surface="warm"
              />
              <FormField
                label="프로필 이미지"
                type="url"
                value={profileForm.profileImageUrl}
                onChange={(e) => handleProfileChange('profileImageUrl', e.target.value)}
                placeholder="https://example.com/avatar.png"
                orientation="inline"
                surface="warm"
              />
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

      {/* 블로그 설정 카드 (FR-SETTINGS-03) */}
      <Panel title="블로그" tone="primary">
        <form onSubmit={handleBlogSubmit} className="flex flex-col gap-[14px] px-5 py-6">
          <FormField
            label="블로그 이름"
            type="text"
            value={blogForm.title}
            onChange={(e) => handleBlogChange('title', e.target.value)}
            maxLength={200}
            orientation="inline"
            surface="warm"
            required
          />

          <FormField
            label="주소 (slug)"
            type="text"
            value={blogForm.urlSlug}
            onChange={(e) => handleBlogChange('urlSlug', e.target.value)}
            maxLength={30}
            prefix="zeroverse.dev/blog/"
            hint="영문 소문자·숫자·하이픈, 3~30자. 주소를 바꾸면 기존 링크는 더 이상 열리지 않아요."
            orientation="inline"
            surface="warm"
            required
          />

          <div className="grid grid-cols-[90px_1fr] items-start gap-3">
            <label
              htmlFor="blog-description"
              className="pt-2 text-[13px] font-semibold text-text-muted"
            >
              블로그 소개
            </label>
            <textarea
              id="blog-description"
              value={blogForm.description}
              onChange={(e) => handleBlogChange('description', e.target.value)}
              className="min-h-14 w-full resize-y border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0"
              maxLength={200}
            />
          </div>

          {blogError && (
            <div className="border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
              {blogError}
            </div>
          )}

          {blogSuccess && (
            <div className="border-2 border-success bg-success-bg px-4 py-3 text-sm text-success">
              블로그 정보가 저장되었습니다.
            </div>
          )}

          <Button
            variant="primary"
            size="md"
            type="submit"
            disabled={blogLoading}
            className="w-full"
          >
            {blogLoading ? '저장 중...' : '저장'}
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
