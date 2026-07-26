import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { BlogInitialSetupPage } from '../pages/BlogInitialSetupPage';
import { SettingsProfilePage } from '../pages/SettingsProfilePage';
import { BlogPage } from '../pages/BlogPage';
import { AuthProvider } from '../lib/authContext';
import { HeroBlogProvider, useHeroBlog } from '../lib/heroBlogContext';
import { resetApiClient } from '../lib/apiClient';

/**
 * M2 화면의 **행동** 계약.
 *
 * <p>폼 필드가 그려지는지가 아니라, 저장이 실제로 무엇을 하는지를 검증한다. 구조만 보는
 * 테스트는 `refreshUser()`를 지우거나 성공 표시를 지워도 통과한다 — 실제로 Gate 6 1차
 * 산출물에서 그 뮤테이션 3건이 모두 살아남았다.
 */

const USER = {
  id: 1,
  email: 'behave@test.dev',
  name: '테스터',
  nickname: 'behaver',
  role: 'USER' as const,
  profileImageUrl: null,
  defaultBlog: { id: 1, title: '테스터의 블로그', urlSlug: 'behaver', isSetupCompleted: false },
};

type Handler = (url: string, init?: RequestInit) => unknown;

function envelope(data: unknown) {
  return {
    ok: true,
    status: 200,
    text: async () => JSON.stringify({ success: true, data, error: null, timestamp: '' }),
    json: async () => ({ success: true, data, error: null, timestamp: '' }),
  } as unknown as Response;
}

function failure(status: number, code: string, message = '실패') {
  const body = { success: false, data: null, error: { code, message, details: [] }, timestamp: '' };
  return {
    ok: false,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  } as unknown as Response;
}

/** 라우트별 응답을 지정하고, 못 맞춘 경로는 명시적으로 실패시킨다. */
function stubFetch(routes: Array<[RegExp, Handler]>) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    for (const [pattern, handler] of routes) {
      if (pattern.test(url)) {
        return handler(url, init) as Response;
      }
    }
    throw new Error(`예상하지 못한 요청: ${url}`);
  });
}

const AUTH_ROUTES: Array<[RegExp, Handler]> = [
  [/\/auth\/refresh/, () => envelope({ accessToken: 't', tokenType: 'Bearer', expiresIn: 3600 })],
  [/\/auth\/me/, () => envelope(USER)],
];

let fetchMock: ReturnType<typeof stubFetch>;

beforeEach(() => {
  resetApiClient();
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

function install(routes: Array<[RegExp, Handler]>) {
  fetchMock = stubFetch([...routes, ...AUTH_ROUTES]);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function callsTo(pattern: RegExp) {
  return fetchMock.mock.calls.filter((c) => pattern.test(String(c[0])));
}

describe('/blog/setup — 초기 설정 행동', () => {
  /** 성공 후 세션을 갱신하지 않으면 SetupGuard가 낡은 상태를 보고 사용자를 setup으로 되돌린다. */
  it('설정에 성공하면 세션을 갱신하고 새 블로그 주소로 이동한다', async () => {
    install([
      [
        /\/blogs\/me\/initial-setup/,
        () =>
          envelope({
            id: 1,
            title: '나의 별',
            urlSlug: 'my-star',
            description: '소개',
            isSetupCompleted: true,
          }),
      ],
    ]);

    render(
      <MemoryRouter initialEntries={['/blog/setup']}>
        <AuthProvider>
          <HeroBlogProvider>
            <Routes>
              <Route path="/blog/setup" element={<BlogInitialSetupPage />} />
              <Route path="/blog/:blogSlug" element={<div>도착: 블로그 페이지</div>} />
            </Routes>
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() => expect(callsTo(/\/auth\/me/).length).toBeGreaterThan(0));
    const meCallsBeforeSubmit = callsTo(/\/auth\/me/).length;

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));

    // 이동했는가 — navigate를 지우면 여기서 걸린다.
    expect(await screen.findByText('도착: 블로그 페이지')).toBeInTheDocument();

    // 세션을 다시 읽었는가 — refreshUser()를 지우면 여기서 걸린다.
    await waitFor(() =>
      expect(callsTo(/\/auth\/me/).length).toBeGreaterThan(meCallsBeforeSubmit),
    );
  });

  it('이미 완료된 설정이면 BLOG_004 안내를 보여주고 이동하지 않는다', async () => {
    install([[/\/blogs\/me\/initial-setup/, () => failure(409, 'BLOG_004')]]);

    render(
      <MemoryRouter initialEntries={['/blog/setup']}>
        <AuthProvider>
          <HeroBlogProvider>
            <Routes>
              <Route path="/blog/setup" element={<BlogInitialSetupPage />} />
              <Route path="/blog/:blogSlug" element={<div>도착: 블로그 페이지</div>} />
            </Routes>
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));

    expect(await screen.findByText(/이미 초기 설정을 완료/)).toBeInTheDocument();
    expect(screen.queryByText('도착: 블로그 페이지')).not.toBeInTheDocument();
  });

  it('중복된 주소면 BLOG_002 안내를 보여준다', async () => {
    install([[/\/blogs\/me\/initial-setup/, () => failure(409, 'BLOG_002')]]);

    render(
      <MemoryRouter initialEntries={['/blog/setup']}>
        <AuthProvider>
          <HeroBlogProvider>
            <BlogInitialSetupPage />
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));

    expect(await screen.findByText(/이미 사용 중인 주소/)).toBeInTheDocument();
  });
});

describe('/settings — 프로필·비밀번호 독립 상태', () => {
  function renderSettings() {
    return render(
      <MemoryRouter initialEntries={['/settings']}>
        <AuthProvider>
          <HeroBlogProvider>
            <SettingsProfilePage />
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );
  }

  it('프로필 저장에 성공하면 저장 완료를 표시한다', async () => {
    install([
      [/\/users\/me$/, (_u, init) => (init?.method === 'PUT' ? envelope(USER) : envelope(USER))],
    ]);

    renderSettings();

    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '저장' }));

    // setProfileSuccess(true)를 지우면 여기서 걸린다.
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();
  });

  /**
   * 계획이 명시한 요구다. 한쪽 실패가 다른 쪽 상태를 지우면 사용자는 방금 저장한 것이
   * 취소된 줄 안다.
   */
  it('비밀번호 변경이 실패해도 프로필 저장 성공 표시는 남는다', async () => {
    install([
      [/\/users\/me\/password/, () => failure(400, 'USER_005')],
      [/\/users\/me$/, () => envelope(USER)],
    ]);

    renderSettings();

    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '저장' }));
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/현재 비밀번호/), 'OldPass123!');
    await user.type(screen.getByLabelText(/새 비밀번호/), 'NewPass123!');

    // "변경"이라는 이름의 버튼이 여러 개다(아바타 변경 등). 비밀번호 폼으로 범위를 좁힌다.
    const passwordForm = screen.getByLabelText(/현재 비밀번호/).closest('form');
    expect(passwordForm).not.toBeNull();
    await user.click(within(passwordForm as HTMLElement).getByRole('button', { name: '변경' }));

    // 비밀번호 쪽 오류가 뜨고,
    await waitFor(() =>
      expect(
        within(passwordForm as HTMLElement).getByText(/비밀번호가 (일치하지|올바르지)/),
      ).toBeInTheDocument(),
    );
    // 프로필 쪽 성공은 그대로 남아 있어야 한다.
    expect(screen.getByText('프로필이 저장되었습니다.')).toBeInTheDocument();
  });

  it('닉네임이 중복이면 USER_002 안내를 보여준다', async () => {
    install([
      [
        /\/users\/me$/,
        (_u, init) => (init?.method === 'PUT' ? failure(409, 'USER_002') : envelope(USER)),
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: '저장' }));

    expect(await screen.findByText(/닉네임/)).toBeInTheDocument();
  });

  it('이메일 입력은 비활성이다', async () => {
    install([[/\/users\/me$/, () => envelope(USER)]]);

    renderSettings();

    const email = await screen.findByLabelText(/이메일/);
    expect(email).toBeDisabled();
  });
});

describe('/blog/:slug — 공개 블로그 히어로 연동', () => {
  /** 히어로가 읽는 값을 그대로 드러내는 프로브. AppShell 전체를 띄우지 않고 계약만 본다. */
  function HeroProbe() {
    const { blog } = useHeroBlog();
    return <div data-testid="hero">{blog ? `${blog.title}|${blog.description}` : '없음'}</div>;
  }

  function renderBlog(slug: string) {
    return render(
      <MemoryRouter initialEntries={[`/blog/${slug}`]}>
        <HeroBlogProvider>
          <HeroProbe />
          <Routes>
            <Route path="/blog/:blogSlug" element={<BlogPage />} />
          </Routes>
        </HeroBlogProvider>
      </MemoryRouter>,
    );
  }

  it('조회한 블로그의 제목·소개가 히어로에 반영된다', async () => {
    install([
      [
        /\/blogs\/slug\/public-star/,
        () =>
          envelope({
            id: 1,
            title: '공개된 별',
            urlSlug: 'public-star',
            description: '항해 기록',
            owner: { id: 2, nickname: 'owner', name: '소유자', profileImageUrl: null, bio: null },
          }),
      ],
    ]);

    renderBlog('public-star');

    // setHeroBlog(data)를 지우면 여기서 걸린다.
    await waitFor(() =>
      expect(screen.getByTestId('hero')).toHaveTextContent('공개된 별|항해 기록'),
    );
  });

  it('없는 블로그면 404 상태를 보여주고 히어로를 채우지 않는다', async () => {
    install([[/\/blogs\/slug\/missing/, () => failure(404, 'BLOG_001')]]);

    renderBlog('missing');

    await waitFor(() => expect(screen.getByTestId('hero')).toHaveTextContent('없음'));
  });
});
