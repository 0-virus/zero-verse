import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button } from '../components/ui/Button';

describe('Button', () => {
  it('클릭 시 핸들러를 호출한다', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>확인</Button>);

    await userEvent.click(screen.getByRole('button', { name: '확인' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('disabled면 클릭해도 핸들러를 호출하지 않는다', async () => {
    const onClick = vi.fn();
    render(
      <Button onClick={onClick} disabled>
        확인
      </Button>,
    );

    const button = screen.getByRole('button', { name: '확인' });
    expect(button).toBeDisabled();
    await userEvent.click(button);

    expect(onClick).not.toHaveBeenCalled();
  });

  it.each(['primary', 'neutral', 'ink', 'inert', 'danger', 'warning', 'accentSm'] as const)(
    '%s variant를 렌더한다',
    (variant) => {
      render(<Button variant={variant}>버튼</Button>);
      expect(screen.getByRole('button', { name: '버튼' })).toHaveAttribute('data-variant', variant);
    },
  );
});
