import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Badge, CountBadge } from '../components/ui/Badge';

describe('Badge', () => {
  it('enum UNIVERSE의 화면 라벨은 "친구"다 (PRD §9-B)', () => {
    render(<Badge kind="UNIVERSE" />);

    expect(screen.getByText('친구')).toBeInTheDocument();
    expect(screen.queryByText('UNIVERSE')).not.toBeInTheDocument();
  });

  it.each([
    ['PUBLIC', 'PUBLIC'],
    ['PRIVATE', 'PRIVATE'],
    ['ACTIVE', 'ACTIVE'],
    ['SUSPENDED', 'SUSPENDED'],
  ] as const)('%s 배지는 %s로 표기한다', (kind, label) => {
    render(<Badge kind={kind} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('카운트가 0 이하면 카운트 배지를 렌더하지 않는다', () => {
    const { container, rerender } = render(<CountBadge count={0} />);
    expect(container).toBeEmptyDOMElement();

    rerender(<CountBadge count={3} />);
    expect(screen.getByText('3')).toBeInTheDocument();
  });
});
