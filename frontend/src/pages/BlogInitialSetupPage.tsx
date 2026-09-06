import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { OnboardingScaffold } from './OnboardingScaffold';
import { FormField } from '../components/ui/FormField';
import { Button } from '../components/ui/Button';
import { useAuth } from '../lib/authContext';
import { getBlog, initialSetup } from '../features/blog/blogApi';
import {
  createCategory,
  getAllCategories,
  type Category,
} from '../features/category/categoryApi';
import type { InitialSetupResponse } from '../features/settings/types';
import { ApiRequestError } from '../lib/apiClient';

const DEFAULT_START_CATEGORIES = ['백엔드', '프론트엔드', '회고'];

function normalizedName(name: string): string {
  return name.trim().toLocaleLowerCase();
}

function hasRootCategory(categories: Category[], name: string): boolean {
  const normalized = normalizedName(name);
  return categories.some(
    (category) => category.parentId === null && normalizedName(category.name) === normalized,
  );
}

function categorySetupError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'CAT_005') return '같은 이름의 시작 카테고리가 이미 있습니다.';
    if (error.code === 'CAT_004') return '시작 카테고리를 저장할 권한이 없습니다.';
    if (error.code === 'CAT_007') return '카테고리 순서가 바뀌었습니다. 다시 시도해 주세요.';
    return error.message || '시작 카테고리를 저장하지 못했습니다.';
  }
  return '시작 카테고리를 저장하지 못했습니다.';
}

/**
 * 초기 설정이 성공한 뒤에만 호출한다. 생성 응답이 유실되어도 먼저 GET으로 확인하고,
 * 이미 생긴 이름은 보존한 채 남은 루트만 순차 생성한다.
 */
async function ensureStartCategories(blogId: number, names: string[]): Promise<void> {
  const desired = Array.from(
    new Map(
      names
        .map((name) => name.trim())
        .filter(Boolean)
        .map((name) => [normalizedName(name), name] as const),
    ).values(),
  );
  let categories = await getAllCategories(blogId, true);

  for (const name of desired) {
    if (hasRootCategory(categories, name)) continue;

    const roots = categories.filter((category) => category.parentId === null);
    const displayOrder = Math.max(-1, ...roots.map((category) => category.displayOrder)) + 1;
    try {
      const created = await createCategory(blogId, {
        name,
        parentId: null,
        type: 'GENERAL',
        displayOrder,
      });
      categories = [...categories, created];
    } catch (createError) {
      // 네트워크 응답 손실·동시 생성 모두 GET으로 판정한다. 초기 설정 endpoint는 재호출하지 않는다.
      try {
        categories = await getAllCategories(blogId, true);
      } catch {
        throw createError;
      }
      if (!hasRootCategory(categories, name)) throw createError;
    }
  }
}

/**
 * `/blog/setup` 블로그 초기 설정 페이지.
 *
 * 필드: 블로그 이름, 주소(slug), 한 줄 소개, 시작 카테고리 칩
 * 성공: initial-setup 1회 → 카테고리 조회·보정 → refreshUser() → `/blog/{urlSlug}` 이동
 * 실패: 서버 오류(409, 형식·예약어 위반 등) 표시
 */
export function BlogInitialSetupPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { refreshUser } = useAuth();

  const [title, setTitle] = useState('');
  const [slug, setSlug] = useState('');
  const [description, setDescription] = useState('');
  const [startCategories, setStartCategories] = useState(DEFAULT_START_CATEGORIES);
  const [categoryInput, setCategoryInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [canContinueToManagement, setCanContinueToManagement] = useState(false);
  const completedBlog = useRef<InitialSetupResponse | null>(null);
  const completionRefreshStarted = useRef(false);

  const markCategoryRecovery = useCallback(() => {
    // 부분 실패가 확인되는 즉시 recovery state를 history에 남긴다. 버튼을 누르기
    // 전에 새로고침해도 완료된 setup을 관리 화면으로 안내할 수 있어야 한다.
    navigate('/blog/setup', {
      replace: true,
      state: { setupRecoveryTo: '/settings/posts' },
    });
    setCanContinueToManagement(true);
  }, [navigate]);

  useEffect(() => {
    const state = location.state;
    if (
      completionRefreshStarted.current ||
      !completedBlog.current ||
      typeof state !== 'object' ||
      state === null ||
      !('setupCompletionTo' in state) ||
      state.setupCompletionTo !== 'blog'
    ) {
      return;
    }

    completionRefreshStarted.current = true;
    let cancelled = false;
    setIsLoading(true);
    setError(null);

    void refreshUser()
      .then(() => {
        if (cancelled || !completedBlog.current) return;
        navigate(`/blog/${completedBlog.current.urlSlug}`, { replace: true, state: null });
      })
      .catch(() => {
        if (cancelled) return;
        completionRefreshStarted.current = false;
        markCategoryRecovery();
        setError(
          '블로그와 시작 카테고리는 저장되었습니다. 세션을 새로고침한 뒤 카테고리 관리에서 확인해 주세요.',
        );
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [location.state, markCategoryRecovery, navigate, refreshUser]);

  const addStartCategory = () => {
    const name = categoryInput.trim();
    if (!name) return;
    if (startCategories.some((category) => normalizedName(category) === normalizedName(name))) {
      setError('같은 이름의 시작 카테고리가 이미 있습니다.');
      return;
    }
    setStartCategories((categories) => [...categories, name]);
    setCategoryInput('');
    setError(null);
  };

  const continueToCategoryManagement = async () => {
    if (!completedBlog.current || isLoading) return;
    setIsLoading(true);
    setError(null);
    try {
      // 부분 실패 시 이미 history에 보존한 관리 경로 의도를 사용한다.
      // initial-setup은 재호출하지 않는다.
      await refreshUser();
    } catch {
      setError(
        '블로그 설정은 완료되었습니다. 세션을 새로고침한 뒤 카테고리 관리에서 이어서 추가해 주세요.',
      );
      setCanContinueToManagement(true);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setCanContinueToManagement(false);
    setIsLoading(true);

    try {
      let result = completedBlog.current;
      if (!result) {
        try {
          result = await initialSetup({
            title: title || undefined,
            urlSlug: slug || undefined,
            description: description || undefined,
          });
          completedBlog.current = result;
        } catch (setupError) {
          // 응답 유실 뒤 서버가 이미 커밋했을 수 있다. GET으로만 복구하고 setup을 재호출하지 않는다.
          if (!(setupError instanceof ApiRequestError) || setupError.code === 'BLOG_004') {
            try {
              const recovered = await getBlog();
              if (recovered.isSetupCompleted) {
                result = recovered;
                completedBlog.current = recovered;
              } else {
                throw setupError;
              }
            } catch {
              throw setupError;
            }
          } else {
            throw setupError;
          }
        }
      }

      if (!result) throw new Error('초기 설정 응답이 비어 있습니다.');

      try {
        await ensureStartCategories(result.id, startCategories);
      } catch (categoryError) {
        markCategoryRecovery();
        setError(
          `${categorySetupError(categoryError)} 블로그 설정은 완료되었으니 다시 시도하거나 카테고리 관리에서 이어서 추가하세요.`,
        );
        return;
      }

      try {
        // 모든 시작 카테고리를 확인한 뒤에만 세션을 갱신해 SetupGuard가 페이지를 먼저 unmount하지 않게 한다.
        navigate('/blog/setup', { replace: true, state: { setupCompletionTo: 'blog' } });
      } catch {
        markCategoryRecovery();
        setError(
          '블로그와 시작 카테고리는 저장되었습니다. 세션을 새로고침한 뒤 카테고리 관리에서 확인해 주세요.',
        );
      }
    } catch (err) {
      if (err instanceof ApiRequestError) {
        // 서버 오류 메시지 표시
        if (err.code === 'BLOG_004') {
          // 이미 완료된 설정이다 — 오류가 아니라 **상태 불일치 신호**로 다룬다.
          //
          // 서버는 커밋했는데 응답이 유실됐거나 refreshUser만 실패하면 클라이언트는 미완료로
          // 남는다. 재시도하면 BLOG_004가 돌아오는데, 여기서 메시지만 띄우면 SetupGuard가 다른
          // 보호 화면을 계속 `/blog/setup`으로 되돌려 사용자가 갇힌다(다른 탭에서 완료한 경우도 같다).
          // 세션을 다시 읽어 완료 상태를 확인하고 자기 블로그로 내보낸다.
          try {
            const blog = await getBlog();
            completedBlog.current = blog;
            await ensureStartCategories(blog.id, startCategories);
            navigate('/blog/setup', { replace: true, state: { setupCompletionTo: 'blog' } });
            return;
          } catch (recoveryError) {
            if (completedBlog.current) markCategoryRecovery();
            setError(
              recoveryError instanceof ApiRequestError && recoveryError.code.startsWith('CAT_')
                ? `${categorySetupError(recoveryError)} 블로그 설정은 완료되었으니 다시 시도하거나 카테고리 관리에서 이어서 추가하세요.`
                : '이미 초기 설정을 완료했습니다. 페이지를 새로고침해 주세요.',
            );
          }
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

        <fieldset disabled={isLoading} className="space-y-2">
          <legend className="mb-1 text-xs font-bold">시작 카테고리</legend>
          <p className="text-[11px] text-text-muted">
            처음부터 사용할 루트 카테고리입니다. 설정 후 카테고리 관리에서 이어서 편집할 수 있어요.
          </p>
          <div className="flex flex-wrap gap-2" aria-label="시작 카테고리 목록">
            {startCategories.map((category) => (
              <span
                key={category}
                className="inline-flex items-center gap-1 border-2 border-ink bg-surface-raise px-2.5 py-1.5 text-[11px] font-bold"
              >
                {category}
                <button
                  type="button"
                  aria-label={`${category} 시작 카테고리 삭제`}
                  onClick={() =>
                    setStartCategories((current) => current.filter((item) => item !== category))
                  }
                  className="font-bold text-text-muted hover:text-danger"
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          <div className="flex gap-2">
            <input
              aria-label="시작 카테고리 추가"
              value={categoryInput}
              onChange={(event) => setCategoryInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  addStartCategory();
                }
              }}
              placeholder="카테고리 이름"
              className="min-w-0 flex-1 border-2 border-ink bg-surface px-3 py-2 text-[12px] outline-0"
              maxLength={100}
            />
            <Button variant="inert" size="sm" type="button" onClick={addStartCategory}>
              + 추가
            </Button>
          </div>
        </fieldset>

        {error && (
          <div className="border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
            {error}
          </div>
        )}

        {canContinueToManagement && completedBlog.current && (
          <Button
            variant="neutral"
            size="md"
            type="button"
            disabled={isLoading}
            onClick={() => void continueToCategoryManagement()}
          >
            카테고리 관리에서 이어서 하기
          </Button>
        )}

        <Button
          variant="primary"
          size="submit"
          type="submit"
          disabled={isLoading}
          className="mt-1"
        >
          {isLoading ? '카테고리 준비 중...' : '항해 시작하기 ✦'}
        </Button>
      </form>
    </OnboardingScaffold>
  );
}
