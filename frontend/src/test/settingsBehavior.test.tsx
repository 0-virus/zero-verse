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

const BLOG = {
  id: 1,
  title: '테스터의 블로그',
  urlSlug: 'behaver',
  description: '기존 소개',
  isSetupCompleted: true,
};

/** 블로그 조회는 설정 화면이 항상 부르므로 기본 라우트로 깐다. 개별 테스트가 앞에서 덮어쓴다. */
const BLOG_GET: [RegExp, Handler] = [/\/blogs\/me$/, () => envelope(BLOG)];

describe('/settings — 프로필·블로그·비밀번호 독립 상태', () => {
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

  /**
   * `저장` 버튼은 프로필 카드와 블로그 카드에 각각 있다. 카드 안의 고유 필드로 form을
   * 좁혀야 엉뚱한 카드를 눌러 놓고 통과하는 일이 없다.
   */
  async function formOf(labelPattern: RegExp) {
    const field = await screen.findByLabelText(labelPattern);
    const form = field.closest('form');
    expect(form).not.toBeNull();
    return form as HTMLElement;
  }

  it('프로필 저장에 성공하면 저장 완료를 표시한다', async () => {
    install([
      [/\/users\/me$/, (_u, init) => (init?.method === 'PUT' ? envelope(USER) : envelope(USER))],
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));

    // setProfileSuccess(true)를 지우면 여기서 걸린다.
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();
  });

  /**
   * 입력은 처음엔 빈 값으로 그려지고 조회 응답이 도착해야 채워진다. 값이 들어오기를
   * 기다리지 않으면 로드를 통째로 지워도 통과한다.
   */
  async function awaitLoadedValue(labelPattern: RegExp, expected: string) {
    const field = await screen.findByLabelText(labelPattern);
    await waitFor(() => expect(field).toHaveValue(expected));
    return field;
  }

  it('블로그 설정을 불러와 입력에 채운다', async () => {
    install([[/\/users\/me$/, () => envelope(USER)], BLOG_GET]);

    renderSettings();

    await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    expect(screen.getByLabelText(/주소 \(slug\)/)).toHaveValue('behaver');
    expect(screen.getByLabelText(/블로그 소개/)).toHaveValue('기존 소개');
  });

  /** 저장이 실제로 PUT /blogs/me를 부르지 않으면 사용자는 저장됐다고 믿고 떠난다. */
  it('블로그 저장은 PUT /blogs/me를 호출하고 성공을 표시한다', async () => {
    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        (_u, init) =>
          init?.method === 'PUT'
            ? envelope({ ...BLOG, title: '새 이름', description: '새 소개' })
            : envelope(BLOG),
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    await user.clear(titleInput);
    await user.type(titleInput, '새 이름');

    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));

    expect(await screen.findByText('블로그 정보가 저장되었습니다.')).toBeInTheDocument();

    const puts = fetchMock.mock.calls.filter(
      (c) => String(c[0]).endsWith('/blogs/me') && (c[1] as RequestInit)?.method === 'PUT',
    );
    expect(puts).toHaveLength(1);
    expect(JSON.parse(String((puts[0][1] as RequestInit).body))).toMatchObject({
      title: '새 이름',
      urlSlug: 'behaver',
    });
    // 서버가 정규화한 값을 되비춘다.
    expect(screen.getByLabelText(/블로그 소개/)).toHaveValue('새 소개');
  });

  it('중복된 주소면 BLOG_002 안내를 보여준다', async () => {
    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        (_u, init) => (init?.method === 'PUT' ? failure(409, 'BLOG_002') : envelope(BLOG)),
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));

    expect(await screen.findByText(/이미 사용 중인 주소/)).toBeInTheDocument();
  });

  /** 카드별 상태가 결합돼 있으면 한쪽 실패가 다른 쪽 성공 표시를 지운다. */
  it('블로그 저장이 실패해도 프로필 저장 성공 표시는 남는다', async () => {
    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        (_u, init) => (init?.method === 'PUT' ? failure(409, 'BLOG_002') : envelope(BLOG)),
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();

    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));

    expect(await screen.findByText(/이미 사용 중인 주소/)).toBeInTheDocument();
    expect(screen.getByText('프로필이 저장되었습니다.')).toBeInTheDocument();
  });

  /** slug를 그대로 두고 제목만 바꾸는 저장이 막히면 안 된다(자기 제외 계약의 화면 쪽 확인). */
  it('slug를 바꾸지 않아도 저장이 성립한다', async () => {
    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        (_u, init) => (init?.method === 'PUT' ? envelope({ ...BLOG, title: '제목만' }) : envelope(BLOG)),
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    await user.clear(titleInput);
    await user.type(titleInput, '제목만');

    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));

    expect(await screen.findByText('블로그 정보가 저장되었습니다.')).toBeInTheDocument();
    expect(screen.getByLabelText(/주소 \(slug\)/)).toHaveValue('behaver');
  });

  it('생년월일과 프로필 이미지를 서버 값으로 표시한다', async () => {
    install([
      [
        /\/users\/me$/,
        () => envelope({ ...USER, birthDate: '1995-01-01', profileImageUrl: 'https://a.dev/x.png' }),
      ],
      BLOG_GET,
    ]);

    renderSettings();

    await awaitLoadedValue(/생년월일/, '1995-01-01');
    expect(screen.getByLabelText('프로필 이미지')).toHaveValue('https://a.dev/x.png');
  });

  /**
   * 표시만 확인하면 `onChange`나 PUT payload에서 두 필드를 빼도 통과한다.
   * FR-SETTINGS-01의 수정 경로를 실제로 잡으려면 요청 본문을 단정해야 한다.
   */
  it('생년월일·프로필 이미지 수정이 PUT 본문에 실린다', async () => {
    install([
      [
        /\/users\/me$/,
        () => envelope({ ...USER, birthDate: '1995-01-01', profileImageUrl: 'https://a.dev/x.png' }),
      ],
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const birth = await awaitLoadedValue(/생년월일/, '1995-01-01');
    await user.clear(birth);
    await user.type(birth, '2000-12-31');

    const image = screen.getByLabelText('프로필 이미지');
    await user.clear(image);
    await user.type(image, 'https://a.dev/new.png');

    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));

    await waitFor(() => {
      const puts = fetchMock.mock.calls.filter(
        (c) => String(c[0]).endsWith('/users/me') && (c[1] as RequestInit)?.method === 'PUT',
      );
      expect(puts).toHaveLength(1);
      expect(JSON.parse(String((puts[0][1] as RequestInit).body))).toMatchObject({
        birthDate: '2000-12-31',
        profileImageUrl: 'https://a.dev/new.png',
      });
    });
  });

  /**
   * 프로필 저장은 `refreshUser()`를 부르고 `/auth/me`는 매번 새 객체를 준다. 로드 effect가
   * `user` 객체 전체에 의존하면 그때 다시 돌아 아직 저장하지 않은 블로그 입력을 서버 값으로
   * 덮어쓴다 — 사용자가 고쳐 놓은 제목이 조용히 사라진다.
   */
  it('프로필을 저장해도 블로그 카드의 미저장 입력이 유지된다', async () => {
    install([
      [/\/users\/me$/, () => envelope(USER)],
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    await user.clear(titleInput);
    await user.type(titleInput, '아직 저장 안 한 제목');

    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();

    // refreshUser() 이후에도 편집 중이던 값이 남아 있어야 한다.
    await waitFor(() => expect(callsTo(/\/auth\/me/).length).toBeGreaterThan(1));
    expect(screen.getByLabelText(/블로그 이름/)).toHaveValue('아직 저장 안 한 제목');
  });

  /**
   * 계획이 명시한 요구다. 한쪽 실패가 다른 쪽 상태를 지우면 사용자는 방금 저장한 것이
   * 취소된 줄 안다.
   */
  it('비밀번호 변경이 실패해도 프로필 저장 성공 표시는 남는다', async () => {
    install([
      [/\/users\/me\/password/, () => failure(400, 'USER_005')],
      [/\/users\/me$/, () => envelope(USER)],
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));
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
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));

    expect(await within(profileForm).findByText(/닉네임/)).toBeInTheDocument();
  });

  it('이메일 입력은 비활성이다', async () => {
    install([[/\/users\/me$/, () => envelope(USER)], BLOG_GET]);

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
            owner: { id: 2, nickname: 'owner', profileImageUrl: null, bio: null },
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
