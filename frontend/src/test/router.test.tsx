import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from '../routes/router';

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AppRoutes />
    </MemoryRouter>,
  );
}

const ROUTES: Array<[string, string]> = [
  ['/', '유니버스 새 소식'],
  ['/signin', '로그인'],
  ['/signup', '회원가입'],
  ['/blog/setup', '블로그 초기 설정'],
  ['/blog/my-blog', '블로그'],
  ['/blog/my-blog/42', '게시글'],
  ['/write', '글쓰기'],
  ['/edit/42', '글 수정'],
  ['/settings', '프로필 설정'],
  ['/settings/universe', '유니버스 관리'],
  ['/settings/posts', '글·카테고리 관리'],
  ['/search', '검색'],
  ['/notifications', '알림 센터'],
  ['/admin', '관리자'],
  ['/admin/users/7', '사용자 상세'],
];

describe('AppRoutes', () => {
  it.each(ROUTES)('%s 는 "%s" 화면을 렌더한다', (path, heading) => {
    renderAt(path);
    expect(screen.getByRole('heading', { level: 1, name: heading })).toBeInTheDocument();
  });

  it('15개 라우트를 모두 검증한다', () => {
    expect(ROUTES).toHaveLength(15);
  });

  it('알 수 없는 경로는 명시적 not-found 화면을 렌더한다', () => {
    renderAt('/this/does/not/exist');
    expect(
      screen.getByRole('heading', { level: 1, name: '페이지를 찾을 수 없습니다' }),
    ).toBeInTheDocument();
  });
});
