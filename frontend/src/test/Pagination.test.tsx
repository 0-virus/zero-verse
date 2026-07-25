import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Pagination } from '../components/ui/Pagination';

describe('Pagination', () => {
  it('현재 페이지를 aria-current로 표시한다', () => {
    render(<Pagination page={1} totalPages={3} onChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '2 페이지' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('button', { name: '1 페이지' })).not.toHaveAttribute('aria-current');
  });

  it('첫 페이지에서 이전 버튼이 비활성이다', () => {
    render(<Pagination page={0} totalPages={3} onChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeEnabled();
  });

  it('마지막 페이지에서 다음 버튼이 비활성이다', () => {
    render(<Pagination page={2} totalPages={3} onChange={vi.fn()} />);

    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeEnabled();
  });

  it('페이지 번호를 클릭하면 해당 0-base 인덱스로 콜백한다', async () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onChange={onChange} />);

    await userEvent.click(screen.getByRole('button', { name: '3 페이지' }));

    expect(onChange).toHaveBeenCalledWith(2);
  });

  it('totalPages가 0이면 렌더하지 않는다', () => {
    const { container } = render(<Pagination page={0} totalPages={0} onChange={vi.fn()} />);
    expect(container).toBeEmptyDOMElement();
  });
});
