import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PostCard } from '../components/ui/PostCard';

describe('PostCard', () => {
  it('전달된 값만 렌더하고 샘플 데이터를 만들지 않는다', () => {
    render(
      <PostCard
        title="첫 번째 로그"
        meta="내 블로그/개발 · 2026-07-25"
        excerpt="발췌 내용"
        visibility="PUBLIC"
        tags={['react', 'spring']}
        commentCount={2}
        likeCount={5}
      />,
    );

    expect(screen.getByRole('heading', { level: 2, name: '첫 번째 로그' })).toBeInTheDocument();
    expect(screen.getByText('내 블로그/개발 · 2026-07-25')).toBeInTheDocument();
    expect(screen.getByText('발췌 내용')).toBeInTheDocument();
    expect(screen.getByText('PUBLIC')).toBeInTheDocument();
    expect(screen.getByText('#react')).toBeInTheDocument();
    expect(screen.getByText('#spring')).toBeInTheDocument();
    expect(screen.getByText('댓글 2 · 좋아요 5')).toBeInTheDocument();
  });

  it('태그가 없으면 태그 칩을 렌더하지 않는다', () => {
    render(<PostCard title="제목" meta="메타" commentCount={0} likeCount={0} />);

    expect(screen.queryByText(/^#/)).not.toBeInTheDocument();
    expect(screen.getByText('댓글 0 · 좋아요 0')).toBeInTheDocument();
  });

  it('클릭 핸들러를 호출한다', async () => {
    const onClick = vi.fn();
    render(
      <PostCard title="제목" meta="메타" commentCount={0} likeCount={0} onClick={onClick} />,
    );

    await userEvent.click(screen.getByRole('heading', { level: 2, name: '제목' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
