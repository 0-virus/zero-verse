import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { UniverseListCard } from './UniverseListCard';
import type { UniverseRelationItem } from './types';

const ITEM: UniverseRelationItem = {
  user: { id: 7, nickname: '별사람', profileImageUrl: null, blogSlug: 'star-person' },
  updatedAt: '2026-11-07T00:00:00Z',
};

describe('UniverseListCard', () => {
  it('로딩·빈 상태·검색·페이지·행 액션을 접근 가능한 카드로 표현한다', async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    const onPageChange = vi.fn();
    const onAction = vi.fn();
    const { rerender } = render(
      <UniverseListCard
        title="연결된 별"
        kind="friends"
        items={[ITEM]}
        page={0}
        totalPages={2}
        hasPrevious={false}
        hasNext
        search=""
        onSearch={onSearch}
        onPageChange={onPageChange}
        onAction={onAction}
        actionLabel="연결 끊기"
      />,
    );

    const heading = screen.getByRole('heading', { name: /연결된 별/ });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveClass('text-[13px]', 'tracking-[2px]');
    expect(heading.parentElement?.parentElement).toHaveClass('px-5', 'py-3');
    expect(screen.getByRole('img', { name: '별사람의 아바타' })).toHaveStyle({
      width: '34px',
      height: '34px',
    });
    expect(screen.getByText('별사람')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '연결 끊기: 별사람' }));
    expect(onAction).toHaveBeenCalledWith(ITEM);

    const search = screen.getByRole('searchbox', { name: '연결된 별 검색' });
    await user.type(search, 'neo');
    await user.keyboard('{Enter}');
    expect(onSearch).toHaveBeenCalledWith('neo');
    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    expect(onPageChange).toHaveBeenCalledWith(1);

    rerender(
      <UniverseListCard
        title="연결된 별"
        kind="friends"
        items={[]}
        page={0}
        totalPages={0}
        hasPrevious={false}
        hasNext={false}
        search=""
        onSearch={onSearch}
        onPageChange={onPageChange}
        loading
        onAction={onAction}
      />,
    );
    expect(screen.getByText('불러오는 중...')).toBeInTheDocument();
  });

  it('받은 신청 카드는 강조 헤더·카운트 배지·38px 아바타를 쓴다', () => {
    render(
      <UniverseListCard
        title="받은 유니버스 신청"
        kind="received"
        items={[{
          requestId: 19,
          user: { id: 19, nickname: '받은 별', profileImageUrl: null, blogSlug: 'received' },
          createdAt: '2026-11-07T00:00:00Z',
        }]}
        page={0}
        totalPages={1}
        hasPrevious={false}
        hasNext={false}
        search=""
        onSearch={vi.fn()}
        onPageChange={vi.fn()}
        count={1}
      />,
    );

    const heading = screen.getByRole('heading', { name: /받은 유니버스 신청/ });
    expect(heading.parentElement?.parentElement).toHaveClass('bg-surface-raise');
    expect(screen.getByText('1')).toHaveClass('bg-accent', 'border-2', 'border-ink', 'px-2', 'py-px', 'text-[11px]');
    expect(screen.getByRole('img', { name: '받은 별의 아바타' })).toHaveStyle({
      width: '38px',
      height: '38px',
    });
  });
});
