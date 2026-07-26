import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { OnboardingScaffold } from './OnboardingScaffold';
import { FormField } from '../components/ui/FormField';
import { Button } from '../components/ui/Button';
import { useAuth } from '../lib/authContext';
import { initialSetup } from '../features/blog/blogApi';
import { ApiRequestError } from '../lib/apiClient';

/**
 * `/blog/setup` 블로그 초기 설정 페이지.
 *
 * 필드: 블로그 이름, 주소(slug), 한 줄 소개
 * 성공: refreshUser() → `/blog/{urlSlug}` 이동
 * 실패: 서버 오류(409, 형식·예약어 위반 등) 표시
 */
export function BlogInitialSetupPage() {
  const navigate = useNavigate();
  const { refreshUser } = useAuth();

  const [title, setTitle] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const result = await initialSetup({
        title: title || undefined,
        urlSlug: slug || undefined,
        description: description || undefined,
      });

      // 세션 갱신 후 블로그 페이지로 이동
      await refreshUser();
      navigate(`/blog/${result.urlSlug}`);
    } catch (err) {
      if (err instanceof ApiRequestError) {
        // 서버 오류 메시지 표시
        if (err.code === 'BLOG_004') {
          setError('이미 초기 설정을 완료했습니다.');
        } else if (err.code === 'BLOG_003') {
          setError('유효하지 않은 주소 형식입니다. 영문 소문자·숫자·하이픈, 3~30자를 입력하세요.');
        } else if (err.code === 'BLOG_002') {
          setError('이미 사용 중인 주소입니다. 다른 주소를 입력하세요.');
        } else {
          setError(err.message || '초기 설정에 실패했습니다.');
        }
      } else {
        setError('초기 설정 중 오류가 발생했습니다.');
      }
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <OnboardingScaffold
      eyebrow="STEP 1 OF 1"
      title="나의 별 이름 짓기"
      description="블로그를 만들어야 유니버스 항해를 시작할 수 있어요."
    >
      {/* 패딩은 OnboardingScaffold 본문이 준다(정본 24px 32px). 필드 간격은 정본 16px. */}
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormField
          label="블로그 이름"
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="예: 내 삶의 기록"
          maxLength={200}
        />

        <FormField
          label="주소 (slug)"
          type="text"
          value={slug}
          onChange={(e) => setSlug(e.target.value)}
          placeholder="my-blog"
          maxLength={30}
          prefix="zeroverse.dev/blog/"
          hint="영문 소문자·숫자·하이픈, 3~30자. 설정에서 나중에 변경할 수 있어요."
        />

        <FormField
          label="한 줄 소개"
          type="text"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="이 블로그를 소개하는 한 줄"
          maxLength={200}
        />

        {error && (
          <div className="border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
            {error}
          </div>
        )}

        <Button
          variant="primary"
          size="submit"
          type="submit"
          disabled={isLoading}
          className="mt-1"
        >
          {isLoading ? '항해 시작 중...' : '항해 시작하기 ✦'}
        </Button>
      </form>
    </OnboardingScaffold>
  );
}
