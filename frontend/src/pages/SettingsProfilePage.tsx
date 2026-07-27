import { useEffect, useRef, useState } from 'react';
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

  /**
   * 사용자가 손댄 폼은 재조회 결과로 덮지 않는다.
   *
   * <p>프로필 저장은 `refreshUser()`를 부르고 `/auth/me` 응답은 매번 **새 객체**로 들어온다
   * (`authContext`의 `setUser(me)`). 로드 effect가 `user` 객체 전체에 의존하면 저장할 때마다
   * effect가 다시 돌아 아직 저장하지 않은 다른 카드의 입력을 서버 값으로 덮어쓴다 —
   * 블로그 제목·slug를 고쳐 놓고 프로필을 저장하면 입력이 조용히 사라졌다.
   * 의존성을 `user.id`로 좁히고, 그래도 남는 늦은 응답 경합은 이 플래그로 막는다.
   */
  const profileDirty = useRef(false);
  const blogDirty = useRef(false);

  /**
   * 편집 횟수. 요청을 보낼 때 값을 캡처해 두고 응답이 왔을 때 그대로인지 본다.
   *
   * <p>입력은 저장 중에도 활성이라 사용자가 `저장`을 누른 뒤 응답이 오기 전에 계속 칠 수 있다.
   * 그때 응답으로 폼을 통째로 갈아끼우거나 dirty를 무조건 풀면 방금 친 글자가 사라진다.
   * revision이 그대로일 때만 서버 값을 반영하고 dirty를 해제한다.
   */
  const profileRevision = useRef(0);
  const blogRevision = useRef(0);

  /**
   * 조회 세대. dirty 플래그만으로는 부족하다 — 초기 GET이 느릴 때 사용자가 값을 넣고 저장까지
   * 마치면 dirty가 풀리는데, 그 뒤 도착한 **PUT 이전 상태를 읽은 GET**이 방금 저장한 값을 옛날
   * 값으로 되돌린다. 저장이 성공하면 세대를 올려 진행 중이던 조회를 무효로 만든다.
   *
   * <p><b>카드마다 따로 센다.</b> 하나를 공유하면 프로필 저장이 아직 도착하지 않은 블로그 조회까지
   * 폐기한다 — `refreshUser()`는 `userId`를 바꾸지 않아 effect도 다시 돌지 않으므로, 그 카드는
   * 새로고침 전까지 빈 채로 남는다. 카드 독립성을 깨는 실제 로드 실패다.
   */
  const profileLoadGeneration = useRef(0);
  const blogLoadGeneration = useRef(0);

  /**
   * 조회가 끝나기 전에는 저장할 수 없다.
   *
   * <p>`PUT`은 부분 수정이 아니라 **전체 교체**다. 조회가 느린 동안 폼은 빈 상태로 그려지는데,
   * 그때 한 필드만 채워 저장하면 아직 화면에 오지 못한 `bio`·`birthDate`·`profileImageUrl`·
   * `description`이 전부 비어 있는 채로 전송되어 서버의 기존 값이 지워진다. 화면에 보이지도
   * 않은 값을 사용자가 지울 수는 없어야 하므로, 로드 완료 전에는 저장 자체를 막는다.
   */
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [blogLoaded, setBlogLoaded] = useState(false);

  // 초기 로드 — 프로필과 블로그는 서로 독립이라 한쪽 실패가 다른 쪽을 비우지 않는다.
  const userId = user?.id;
  useEffect(() => {
    const profileGeneration = ++profileLoadGeneration.current;
    const blogGeneration = ++blogLoadGeneration.current;

    const loadProfile = async () => {
      try {
        const profile = await getProfile();
        if (profileLoadGeneration.current !== profileGeneration) return;
        // 저장이 이미 끝난 뒤(dirty=false, 세대는 그대로)라면 폼은 최신이다. 값만 덮지 않고
        // 로드 완료 표시는 올려 저장이 가능하게 한다.
        if (!profileDirty.current) {
          setProfileForm({
            name: profile.name || '',
            nickname: profile.nickname || '',
            bio: profile.bio || '',
            birthDate: profile.birthDate || '',
            profileImageUrl: profile.profileImageUrl || '',
          });
        }
        setProfileLoaded(true);
      } catch {
        setProfileError('프로필을 불러올 수 없습니다.');
      }
    };

    const loadBlog = async () => {
      try {
        const blog = await getBlog();
        if (blogLoadGeneration.current !== blogGeneration) return;
        if (!blogDirty.current) {
          setBlogForm({
            title: blog.title || '',
            urlSlug: blog.urlSlug || '',
            description: blog.description || '',
          });
        }
        setBlogLoaded(true);
      } catch {
        setBlogError('블로그 정보를 불러올 수 없습니다.');
      }
    };

    if (userId != null) {
      loadProfile();
      loadBlog();
    }
  }, [userId]);

  const handleProfileChange = (field: keyof ProfileForm, value: string) => {
    profileDirty.current = true;
    profileRevision.current += 1;
    setProfileForm((prev) => ({ ...prev, [field]: value }));
    setProfileSuccess(false);
  };

  const handleBlogChange = (field: keyof BlogForm, value: string) => {
    blogDirty.current = true;
    blogRevision.current += 1;
    setBlogForm((prev) => ({ ...prev, [field]: value }));
    setBlogSuccess(false);
  };

  const handleBlogSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBlogError(null);
    setBlogSuccess(false);
    setBlogLoading(true);

    const revisionAtSubmit = blogRevision.current;

    try {
      const updated = await updateBlog({
        title: blogForm.title,
        urlSlug: blogForm.urlSlug,
        description: blogForm.description || undefined,
      });

      blogLoadGeneration.current += 1;
      // 저장을 누른 뒤에도 계속 입력했다면 그 편집이 최신이다. 서버 응답으로 덮지 않는다.
      if (blogRevision.current === revisionAtSubmit) {
        blogDirty.current = false;
        // 서버가 정규화한 값을 그대로 되비춘다.
        setBlogForm({
          title: updated.title || '',
          urlSlug: updated.urlSlug || '',
          description: updated.description || '',
        });
      }
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

    const revisionAtSubmit = profileRevision.current;

    try {
      await updateProfile({
        name: profileForm.name,
        nickname: profileForm.nickname,
        bio: profileForm.bio || undefined,
        birthDate: profileForm.birthDate || undefined,
        profileImageUrl: profileForm.profileImageUrl || undefined,
      });

      // 저장 이전 상태를 읽고 있던 **프로필** 조회는 이제 낡았다. 블로그 조회는 건드리지 않는다.
      profileLoadGeneration.current += 1;
      // 요청을 보낸 뒤에도 계속 입력했다면 아직 저장되지 않은 편집이 남아 있다 — dirty를 유지한다.
      if (profileRevision.current === revisionAtSubmit) {
        profileDirty.current = false;
      }
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
            disabled={profileLoading || !profileLoaded}
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
            disabled={blogLoading || !blogLoaded}
            className="w-full"
          >
            {blogLoading ? '저장 중...' : '저장'}
          </Button>
        </form>
      </Panel>

      {/* 비밀번호 변경 카드 */}
      <Panel title="비밀번호 변경" tone="primary">
        {/* 정본 §8.7: 두 입력과 `변경` 버튼이 한 행(`align-items:flex-end`), 패딩 20px 24px. */}
        <form onSubmit={handlePasswordSubmit} className="space-y-4 px-6 py-5">
          <div className="flex items-end gap-3">
            <FormField
              className="flex-1"
              label="현재 비밀번호"
              type="password"
              value={passwordForm.currentPassword}
              onChange={(e) => handlePasswordChange('currentPassword', e.target.value)}
              labelTone="muted"
              surface="warm"
              required
            />
            <FormField
              className="flex-1"
              label="새 비밀번호"
              type="password"
              value={passwordForm.newPassword}
              onChange={(e) => handlePasswordChange('newPassword', e.target.value)}
              labelTone="muted"
              surface="warm"
              // 정본은 이 자리에 별도 힌트 줄이 없다. 정책 문구는 placeholder로 넣어
              // 한 행 정렬(`items-end`)을 깨지 않으면서 실제 정책(8~64자)을 알린다.
              placeholder="8~64자, 영문·숫자·특수문자"
              required
            />
            <Button variant="ink" size="md" type="submit" disabled={passwordLoading}>
              {passwordLoading ? '변경 중...' : '변경'}
            </Button>
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
        </form>
      </Panel>
    </div>
  );
}
