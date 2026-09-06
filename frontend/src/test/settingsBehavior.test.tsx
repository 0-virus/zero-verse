import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { BlogInitialSetupPage } from '../pages/BlogInitialSetupPage';
import { SettingsProfilePage } from '../pages/SettingsProfilePage';
import { BlogPage } from '../pages/BlogPage';
import { AppShell } from '../components/layout/AppShell';
import { AppRoutes } from '../routes/router';
import { AuthProvider, useAuth } from '../lib/authContext';
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

/** API 응답 파싱과 React 후속 갱신까지 기다릴 수 있도록 본문 읽기를 관측한다. */
function trackText(response: Response, onRead: () => void) {
  const readText = response.text.bind(response);
  return {
    ...response,
    text: async () => {
      const body = await readText();
      onRead();
      return body;
    },
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

/** M3 초기 설정의 GET → 누락 루트 순차 POST를 검증하기 위한 실제 응답 모형. */
function setupCategoryRoutes(): Array<[RegExp, Handler]> {
  const categories: Array<{
    id: number;
    parentId: null;
    name: string;
    type: 'GENERAL';
    displayOrder: number;
    postCount: number;
    children: [];
  }> = [];
  return [
    [
      /\/blogs\/1\/categories(?:\?|$)/,
      (_url, init) => {
        if (init?.method === 'POST') {
          const request = JSON.parse(String(init.body)) as {
            name: string;
            displayOrder: number;
          };
          const category = {
            id: categories.length + 10,
            parentId: null,
            name: request.name,
            type: 'GENERAL' as const,
            displayOrder: request.displayOrder,
            postCount: 0,
            children: [] as [],
          };
          categories.push(category);
          return envelope(category);
        }
        return envelope({
          items: categories,
          page: 0,
          size: 100,
          totalElements: categories.length,
          totalPages: 1,
          hasNext: false,
          hasPrevious: false,
        });
      },
    ],
  ];
}

function PathProbe() {
  const { pathname } = useLocation();
  return <div data-testid="pathname">{pathname}</div>;
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
      ...setupCategoryRoutes(),
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

  /**
   * BLOG_004는 오류가 아니라 **상태 불일치 신호**다. 서버는 커밋했는데 응답이 유실됐거나
   * `refreshUser`만 실패하면 클라이언트는 미완료로 남고, 재시도하면 BLOG_004가 돌아온다.
   * 그때 메시지만 띄우면 `SetupGuard`가 다른 화면을 계속 `/blog/setup`으로 되돌려 갇힌다.
   */
  it('이미 완료된 설정이면 BLOG_004에서 자기 블로그로 빠져나간다', async () => {
    install([
      [/\/blogs\/me\/initial-setup/, () => failure(409, 'BLOG_004')],
      [/\/blogs\/me$/, () => envelope({ ...BLOG, urlSlug: 'already-done' })],
      ...setupCategoryRoutes(),
    ]);

    render(
      <MemoryRouter initialEntries={['/blog/setup']}>
        <AuthProvider>
          <HeroBlogProvider>
            <PathProbe />
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

    expect(await screen.findByText('도착: 블로그 페이지')).toBeInTheDocument();
    expect(screen.getByTestId('pathname')).toHaveTextContent('/blog/already-done');
  });

  /** 복구 조회마저 실패하면 갇히지 않도록 안내라도 남겨야 한다. */
  it('BLOG_004 복구 조회가 실패하면 새로고침 안내를 보여준다', async () => {
    install([
      [/\/blogs\/me\/initial-setup/, () => failure(409, 'BLOG_004')],
      [/\/blogs\/me$/, () => failure(500, 'COMMON_500')],
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

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: /항해 시작하기/ }));

    expect(await screen.findByText(/새로고침/)).toBeInTheDocument();
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
    const saveButton = within(profileForm).getByRole('button', { name: '저장' });
    expect(saveButton).toHaveClass('self-start', 'px-[22px]');
    expect(saveButton).not.toHaveClass('w-full');
    await user.click(saveButton);

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

  /**
   * 조회가 실패하면 `fieldset`이 잠긴 채로 남는다. 재시도 수단이 없으면 일시적인 5xx 한 번으로
   * 페이지를 다시 열기 전까지 저장이 불가능해진다 — 잠금이 사용자를 가두면 안 된다.
   */
  it('블로그 조회가 실패해도 재시도하면 폼이 채워지고 잠금이 풀린다', async () => {
    let attempt = 0;
    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        (_u, init) => {
          if (init?.method === 'PUT') return envelope(BLOG);
          attempt += 1;
          return attempt === 1 ? failure(500, 'COMMON_500') : envelope(BLOG);
        },
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    expect(await screen.findByText('블로그 정보를 불러올 수 없습니다.')).toBeInTheDocument();

    const titleInput = screen.getByLabelText(/블로그 이름/);
    expect(titleInput).toBeDisabled();

    await user.click(screen.getByRole('button', { name: '블로그 정보 다시 불러오기' }));

    await waitFor(() => expect(titleInput).toBeEnabled());
    expect(titleInput).toHaveValue('테스터의 블로그');
    expect(screen.getByLabelText(/블로그 소개/)).toHaveValue('기존 소개');
  });

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

    const avatarImage = screen.getByAltText('behaver의 프로필 이미지');
    expect(avatarImage).toHaveAttribute('src', 'https://a.dev/x.png');
    const avatar = avatarImage.parentElement;
    expect(avatar).toHaveClass('h-[90px]', 'w-[90px]', 'bg-surface-raise');
  });

  it('프로필 이미지 변경 버튼은 URL 입력으로 포커스를 옮긴다', async () => {
    install([[/\/users\/me$/, () => envelope(USER)], BLOG_GET]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    const imageInput = screen.getByLabelText('프로필 이미지');
    expect(screen.getByRole('img', { name: '프로필 아바타' })).toHaveTextContent('🪐');
    await user.click(within(profileForm).getByRole('button', { name: '변경' }));

    expect(imageInput).toHaveFocus();
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
    expect(screen.getByAltText('behaver의 프로필 이미지')).toHaveAttribute(
      'src',
      'https://a.dev/new.png',
    );

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
   * 입력은 저장 중에도 활성이다. `저장`을 누른 뒤 응답이 오기 전에 계속 친 글자를 성공 응답이
   * 덮으면 방금 입력한 것이 사라진다. 응답을 붙잡아 두고 그 사이에 입력해 경합을 재현한다.
   */
  it('블로그 저장 중에 이어서 입력한 값이 성공 응답에 덮이지 않는다', async () => {
    let releasePut: (() => void) | null = null;
    const putArrived = new Promise<void>((resolve) => {
      releasePut = resolve;
    });

    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        async (_u, init) => {
          if (init?.method !== 'PUT') return envelope(BLOG);
          // 응답을 테스트가 풀어 줄 때까지 붙잡는다.
          await putArrived;
          return envelope({ ...BLOG, title: '서버가 돌려준 제목' });
        },
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    await user.clear(titleInput);
    await user.type(titleInput, '첫 번째 편집');

    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));

    // 요청이 나간 뒤, 응답이 오기 전에 이어서 입력한다.
    await user.clear(titleInput);
    await user.type(titleInput, '저장 후 이어서 친 제목');

    releasePut!();

    await waitFor(() =>
      expect(screen.getByText('블로그 정보가 저장되었습니다.')).toBeInTheDocument(),
    );
    expect(titleInput).toHaveValue('저장 후 이어서 친 제목');
  });

  /**
   * `PUT`은 전체 교체다. 조회가 끝나기 전 폼은 비어 있으므로, 그 상태로 저장하면 아직 화면에
   * 오지 못한 `description`·`bio` 등이 빈 값으로 전송돼 서버의 기존 값이 지워진다.
   * 화면에 보이지도 않은 값을 사용자가 지울 수는 없어야 하므로 **저장 자체가 막혀야 한다**.
   */
  it('조회 전에는 입력·저장이 잠기고, 끝나면 미편집 필드도 서버 값으로 채워진다', async () => {
    let releaseGet: (() => void) | null = null;
    const getHeld = new Promise<void>((resolve) => {
      releaseGet = resolve;
    });

    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        async (_u, init) => {
          if (init?.method === 'PUT') return envelope(BLOG);
          await getHeld;
          return envelope({ ...BLOG, description: '서버에 있던 소개' });
        },
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await screen.findByLabelText(/블로그 이름/);
    const blogForm = await formOf(/블로그 이름/);
    const saveButton = within(blogForm).getByRole('button', { name: '저장' });

    // 조회가 오지 않은 동안에는 입력도 저장도 잠겨 있다. 입력이 열려 있으면 한 글자만 쳐도
    // dirty가 서고, 그러면 도착한 응답이 폼을 채우지 못해 미편집 필드가 빈 채로 남는다.
    expect(titleInput).toBeDisabled();
    expect(saveButton).toBeDisabled();

    await user.type(titleInput, '조회 전 입력');
    expect(titleInput).toHaveValue('');

    // 조회가 도착하면 잠금이 풀리고 폼이 서버 값으로 채워진다.
    releaseGet!();
    await waitFor(() => expect(saveButton).toBeEnabled());
    expect(titleInput).toHaveValue('테스터의 블로그');
    expect(screen.getByLabelText(/블로그 소개/)).toHaveValue('서버에 있던 소개');

    // 제목만 고쳐 저장해도 건드리지 않은 소개가 그대로 실려야 한다.
    await user.clear(titleInput);
    await user.type(titleInput, '새 제목');
    await user.click(saveButton);

    await waitFor(() => {
      const puts = fetchMock.mock.calls.filter(
        (c) => String(c[0]).endsWith('/blogs/me') && (c[1] as RequestInit)?.method === 'PUT',
      );
      expect(puts).toHaveLength(1);
      expect(JSON.parse(String((puts[0][1] as RequestInit).body))).toMatchObject({
        title: '새 제목',
        description: '서버에 있던 소개',
      });
    });
  });

  /**
   * 조회 세대를 두 카드가 공유하면, 한쪽 저장이 아직 도착하지 않은 **다른 쪽** 조회까지 폐기한다.
   * `refreshUser()`는 `userId`를 바꾸지 않아 effect도 다시 돌지 않으므로 그 카드는 빈 채로 남는다.
   */
  it('프로필을 저장해도 지연된 블로그 조회는 정상적으로 반영된다', async () => {
    let releaseGet: (() => void) | null = null;
    const getHeld = new Promise<void>((resolve) => {
      releaseGet = resolve;
    });
    let markResolved: (() => void) | null = null;
    const getResolved = new Promise<void>((resolve) => {
      markResolved = resolve;
    });

    install([
      [/\/users\/me$/, () => envelope(USER)],
      [
        /\/blogs\/me$/,
        async () => {
          await getHeld;
          const response = envelope(BLOG);
          markResolved!();
          return response;
        },
      ],
    ]);

    renderSettings();

    const user = userEvent.setup();
    const profileForm = await formOf(/닉네임/);
    await user.click(within(profileForm).getByRole('button', { name: '저장' }));
    expect(await screen.findByText('프로필이 저장되었습니다.')).toBeInTheDocument();

    // 프로필 저장이 끝난 뒤 블로그 조회가 도착한다. 폐기되면 안 된다.
    releaseGet!();
    await getResolved;

    await waitFor(() =>
      expect(screen.getByLabelText(/블로그 이름/)).toHaveValue('테스터의 블로그'),
    );
  });

  it('블로그를 저장해도 지연된 프로필 조회는 정상적으로 반영된다', async () => {
    let releaseGet: (() => void) | null = null;
    const getHeld = new Promise<void>((resolve) => {
      releaseGet = resolve;
    });
    let markResolved: (() => void) | null = null;
    const getResolved = new Promise<void>((resolve) => {
      markResolved = resolve;
    });

    install([
      [
        /\/users\/me$/,
        async (_u, init) => {
          if (init?.method === 'PUT') return envelope(USER);
          await getHeld;
          const response = envelope({ ...USER, name: '조회로 채워진 이름' });
          markResolved!();
          return response;
        },
      ],
      BLOG_GET,
    ]);

    renderSettings();

    const user = userEvent.setup();
    const titleInput = await awaitLoadedValue(/블로그 이름/, '테스터의 블로그');
    await user.clear(titleInput);
    await user.type(titleInput, '새 제목');

    const blogForm = await formOf(/블로그 이름/);
    await user.click(within(blogForm).getByRole('button', { name: '저장' }));
    expect(await screen.findByText('블로그 정보가 저장되었습니다.')).toBeInTheDocument();

    releaseGet!();
    await getResolved;

    await waitFor(() =>
      expect(screen.getByLabelText('이름')).toHaveValue('조회로 채워진 이름'),
    );
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

  /**
   * `/blog/A` → `/blog/B`로 옮기면 A 요청이 아직 떠 있다. 늦게 도착한 A 응답이 B 화면을 A로
   * 되돌리거나(성공) 멀쩡한 B를 404로 바꾸면(실패) 사용자는 자기가 연 페이지를 잃는다.
   */
  it('이전 slug의 늦은 응답이 현재 화면을 덮어쓰지 않는다', async () => {
    let releaseA: (() => void) | null = null;
    const aHeld = new Promise<void>((resolve) => {
      releaseA = resolve;
    });
    let aParsed: (() => void) | null = null;
    const aResponseParsed = new Promise<void>((resolve) => {
      aParsed = resolve;
    });

    install([
      [
        /\/blogs\/slug\/a$/,
        async () => {
          await aHeld;
          return trackText(
            envelope({ ...BLOG, title: '이전 블로그 A', urlSlug: 'a' }),
            aParsed!,
          );
        },
      ],
      [/\/blogs\/slug\/b$/, () => envelope({ ...BLOG, title: '현재 블로그 B', urlSlug: 'b' })],
    ]);

    // 같은 라우터 안에서 실제로 이동해야 A의 effect cleanup이 도는 전환이 재현된다.
    function GoToB() {
      const navigate = useNavigate();
      return (
        <button type="button" onClick={() => navigate('/blog/b')}>
          B로 이동
        </button>
      );
    }

    render(
      <MemoryRouter initialEntries={['/blog/a']}>
        <HeroBlogProvider>
          <HeroProbe />
          <GoToB />
          <Routes>
            <Route path="/blog/:blogSlug" element={<BlogPage />} />
          </Routes>
        </HeroBlogProvider>
      </MemoryRouter>,
    );

    // A가 아직 응답하지 않은 상태에서 B로 이동한다.
    const user = userEvent.setup();
    await waitFor(() => expect(callsTo(/\/blogs\/slug\/a$/)).toHaveLength(1));
    await user.click(screen.getByRole('button', { name: 'B로 이동' }));

    await waitFor(() =>
      expect(screen.getByTestId('hero')).toHaveTextContent('현재 블로그 B'),
    );

    // 이제서야 A가 도착한다. 화면은 B 그대로여야 한다.
    await act(async () => {
      releaseA!();
      await aResponseParsed;
      // apiClient의 JSON 파싱과 BlogPage의 async continuation까지 flush한다.
      await Promise.resolve();
      await Promise.resolve();
    });

    await waitFor(() =>
      expect(screen.getByTestId('hero')).toHaveTextContent('현재 블로그 B'),
    );
    expect(screen.getByTestId('hero')).not.toHaveTextContent('이전 블로그 A');
  });

  it('이전 slug의 늦은 오류도 현재 화면을 덮어쓰지 않는다', async () => {
    let releaseA: (() => void) | null = null;
    const aHeld = new Promise<void>((resolve) => {
      releaseA = resolve;
    });
    let aParsed: (() => void) | null = null;
    const aResponseParsed = new Promise<void>((resolve) => {
      aParsed = resolve;
    });

    install([
      [
        /\/blogs\/slug\/a-error$/,
        async () => {
          await aHeld;
          return trackText(failure(404, 'BLOG_001'), aParsed!);
        },
      ],
      [
        /\/blogs\/slug\/b-error$/,
        () => envelope({ ...BLOG, title: '현재 블로그 B', urlSlug: 'b-error' }),
      ],
    ]);

    function GoToB() {
      const navigate = useNavigate();
      return (
        <button type="button" onClick={() => navigate('/blog/b-error')}>
          B로 이동
        </button>
      );
    }

    render(
      <MemoryRouter initialEntries={['/blog/a-error']}>
        <HeroBlogProvider>
          <HeroProbe />
          <GoToB />
          <Routes>
            <Route path="/blog/:blogSlug" element={<BlogPage />} />
          </Routes>
        </HeroBlogProvider>
      </MemoryRouter>,
    );

    const user = userEvent.setup();
    await waitFor(() => expect(callsTo(/\/blogs\/slug\/a-error$/)).toHaveLength(1));
    await user.click(screen.getByRole('button', { name: 'B로 이동' }));

    await waitFor(() =>
      expect(screen.getByTestId('hero')).toHaveTextContent('현재 블로그 B|기존 소개'),
    );

    await act(async () => {
      releaseA!();
      await aResponseParsed;
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(screen.getByTestId('hero')).toHaveTextContent('현재 블로그 B|기존 소개');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /**
   * REQUIREMENTS의 "블로그 헤더: 소유자 프로필". 소유자가 달라도 늘 같은 이모지가 나오면
   * 응답의 owner를 화면이 전혀 쓰지 않는다는 뜻이다 — 값을 넣기만 하고 렌더를 확인하지 않으면
   * 이 결함을 통과시킨다.
   */
  it('공개 블로그 히어로가 소유자 프로필 이미지를 렌더한다', async () => {
    install([
      [
        /\/blogs\/slug\/owned$/,
        () =>
          envelope({
            ...BLOG,
            urlSlug: 'owned',
            owner: {
              id: 2,
              nickname: '별지기',
              profileImageUrl: 'https://cdn.test/avatar.png',
              bio: null,
            },
          }),
      ],
    ]);

    render(
      <MemoryRouter initialEntries={['/blog/owned']}>
        <HeroBlogProvider>
          <AppShell>
            <Routes>
              <Route path="/blog/:blogSlug" element={<BlogPage />} />
            </Routes>
          </AppShell>
        </HeroBlogProvider>
      </MemoryRouter>,
    );

    const avatar = await screen.findByAltText('별지기의 프로필 이미지');
    expect(avatar).toHaveAttribute('src', 'https://cdn.test/avatar.png');
  });

  it('프로필 이미지가 없으면 이모지로 떨어진다', async () => {
    install([
      [
        /\/blogs\/slug\/noimage$/,
        () =>
          envelope({
            ...BLOG,
            urlSlug: 'noimage',
            owner: { id: 3, nickname: '이미지없음', profileImageUrl: null, bio: null },
          }),
      ],
    ]);

    render(
      <MemoryRouter initialEntries={['/blog/noimage']}>
        <HeroBlogProvider>
          <AppShell>
            <Routes>
              <Route path="/blog/:blogSlug" element={<BlogPage />} />
            </Routes>
          </AppShell>
        </HeroBlogProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByLabelText('이미지없음의 블로그 아바타')).toBeInTheDocument();
  });

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
    expect(screen.getByRole('alert')).toHaveTextContent('블로그를 찾을 수 없습니다.');
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
  });

  it('일시적인 500 오류는 재시도로 블로그를 다시 불러온다', async () => {
    let attempt = 0;
    install([
      [
        /\/blogs\/slug\/transient$/,
        () => {
          attempt += 1;
          return attempt === 1
            ? failure(500, 'COMMON_500')
            : envelope({ ...BLOG, title: '복구된 블로그', urlSlug: 'transient' });
        },
      ],
    ]);

    renderBlog('transient');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '블로그를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.',
    );
    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '다시 시도' }));

    await waitFor(() =>
      expect(screen.getByTestId('hero')).toHaveTextContent('복구된 블로그|기존 소개'),
    );
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument();
  });
});

describe('/ — 기본 블로그 내비게이션 연동', () => {
  it('로그인 후 기본 블로그 slug를 My Blog와 우측 패널에 반영하고 갱신한다', async () => {
    let meCalls = 0;
    const renamedUser = {
      ...USER,
      nickname: 'renamed-user',
      defaultBlog: { ...USER.defaultBlog, title: '이름 바뀐 별', urlSlug: 'renamed-blog' },
    };
    install([[/\/auth\/me$/, () => envelope(meCalls++ === 0 ? USER : renamedUser)]]);

    function RefreshUserButton() {
      const { refreshUser } = useAuth();
      return (
        <button type="button" onClick={() => void refreshUser()}>
          세션 다시 불러오기
        </button>
      );
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <HeroBlogProvider>
            <AppRoutes />
            <RefreshUserButton />
          </HeroBlogProvider>
        </AuthProvider>
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByRole('link', { name: /My Blog/ })).toHaveAttribute(
        'href',
        '/blog/behaver',
      ),
    );
    expect(screen.getByRole('link', { name: 'behaver' })).toBeInTheDocument();
    expect(screen.getByText('테스터의 블로그')).toBeInTheDocument();

    const user = userEvent.setup();
    await user.click(screen.getByRole('button', { name: '세션 다시 불러오기' }));

    await waitFor(() =>
      expect(screen.getByRole('link', { name: /My Blog/ })).toHaveAttribute(
        'href',
        '/blog/renamed-blog',
      ),
    );
    expect(screen.getByRole('link', { name: 'renamed-user' })).toBeInTheDocument();
    expect(screen.getByText('/blog/renamed-blog')).toBeInTheDocument();
    expect(screen.getByText('이름 바뀐 별')).toBeInTheDocument();
  });
});
