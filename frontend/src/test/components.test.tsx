import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Tabs } from '../components/ui/Tab';
import { TagChip } from '../components/ui/TagChip';
import { Panel } from '../components/ui/Panel';
import { FormField } from '../components/ui/FormField';
import { ListRow } from '../components/ui/ListRow';
import { Avatar } from '../components/ui/Avatar';
import { Callout, CodeBlock, Prose } from '../components/ui/Prose';
import { Table } from '../components/ui/Table';
import { Modal } from '../components/ui/Modal';
import { TagInput } from '../components/ui/TagInput';

describe('Tabs', () => {
  const items = [
    { key: 'all', label: '전체' },
    { key: 'post', label: '글' },
  ];

  it('활성 탭만 aria-selected=true다', () => {
    render(<Tabs items={items} activeKey="post" onChange={vi.fn()} />);

    expect(screen.getByRole('tab', { name: '글' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: '전체' })).toHaveAttribute('aria-selected', 'false');
  });

  it('탭 클릭 시 key로 콜백한다', async () => {
    const onChange = vi.fn();
    render(<Tabs items={items} activeKey="all" onChange={onChange} />);

    await userEvent.click(screen.getByRole('tab', { name: '글' }));

    expect(onChange).toHaveBeenCalledWith('post');
  });
});

describe('TagChip', () => {
  it('항상 #을 접두로 표시한다', () => {
    render(<TagChip name="react" />);
    expect(screen.getByText('#react')).toBeInTheDocument();
  });

  it('onClick이 없으면 버튼이 아니다', () => {
    render(<TagChip name="react" />);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('onClick이 있으면 버튼으로 렌더되고 호출된다', async () => {
    const onClick = vi.fn();
    render(<TagChip name="spring" onClick={onClick} />);

    await userEvent.click(screen.getByRole('button', { name: '#spring' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });
});

describe('Panel', () => {
  it('제목 앞에 ■를 붙이고 액션을 렌더한다', () => {
    render(
      <Panel title="UNIVERSE" actions={<button type="button">더보기</button>}>
        본문
      </Panel>,
    );

    expect(screen.getByRole('heading', { level: 2, name: '■ UNIVERSE' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '더보기' })).toBeInTheDocument();
    expect(screen.getByText('본문')).toBeInTheDocument();
  });

  it('제목이 없으면 헤더를 렌더하지 않는다', () => {
    render(<Panel>본문만</Panel>);

    expect(screen.queryByRole('heading')).not.toBeInTheDocument();
    expect(screen.getByText('본문만')).toBeInTheDocument();
  });
});

describe('FormField', () => {
  it('라벨과 입력이 연결되고 입력값이 전달된다', async () => {
    const onChange = vi.fn();
    render(<FormField label="닉네임" hint="2~20자" onChange={onChange} />);

    const input = screen.getByLabelText('닉네임');
    await userEvent.type(input, 'zero');

    expect(onChange).toHaveBeenCalled();
    expect(screen.getByText('2~20자')).toBeInTheDocument();
  });

  it('disabled면 입력할 수 없다', async () => {
    const onChange = vi.fn();
    render(<FormField label="이메일" disabled onChange={onChange} />);

    const input = screen.getByLabelText('이메일');
    expect(input).toBeDisabled();
    await userEvent.type(input, 'x');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('slug 접두 span을 렌더한다', () => {
    render(<FormField label="주소" prefix="zeroverse.dev/" />);
    expect(screen.getByText('zeroverse.dev/')).toBeInTheDocument();
  });
});

describe('ListRow', () => {
  it('아바타·제목·부제·액션을 렌더한다', () => {
    render(
      <ListRow
        avatar={<Avatar emoji="🚀" label="로켓" />}
        title="홍길동"
        subtitle="zeroverse.dev/hong"
        actions={<button type="button">수락</button>}
      />,
    );

    expect(screen.getByRole('img', { name: '로켓' })).toBeInTheDocument();
    expect(screen.getByText('홍길동')).toBeInTheDocument();
    expect(screen.getByText('zeroverse.dev/hong')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '수락' })).toBeInTheDocument();
  });

  it('부제와 액션이 없으면 렌더하지 않는다', () => {
    render(<ListRow title="이름만" />);

    expect(screen.getByText('이름만')).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('Avatar', () => {
  it.each([28, 34, 38] as const)('%dpx 크기를 적용한다', (size) => {
    render(<Avatar emoji="🌙" size={size} label={`아바타-${size}`} />);

    expect(screen.getByRole('img', { name: `아바타-${size}` })).toHaveStyle({
      width: `${size}px`,
      height: `${size}px`,
    });
  });

  it('배경색을 지정할 수 있다', () => {
    render(<Avatar emoji="🌙" background="#f9e3f2" label="핑크" />);
    expect(screen.getByRole('img', { name: '핑크' })).toHaveStyle({ background: '#f9e3f2' });
  });
});

describe('Prose', () => {
  it('본문·콜아웃·코드블록을 렌더한다', () => {
    render(
      <Prose>
        <p>본문 문단</p>
        <Callout>인용문</Callout>
        <CodeBlock code="const a = 1;" />
      </Prose>,
    );

    expect(screen.getByText('본문 문단')).toBeInTheDocument();
    expect(screen.getByText(/인용문/)).toBeInTheDocument();
    expect(screen.getByText('const a = 1;')).toBeInTheDocument();
  });

  it('콜아웃은 ★를 접두로 붙인다', () => {
    render(<Callout>주의</Callout>);
    expect(screen.getByText(/★/)).toBeInTheDocument();
  });
});

describe('Table', () => {
  type Row = { id: string; name: string };
  const columns = [
    { key: 'name', header: '이름', render: (r: Row) => r.name },
    { key: 'id', header: 'UID', render: (r: Row) => r.id },
  ];

  it('헤더와 행을 렌더한다', () => {
    render(
      <Table
        columns={columns}
        rows={[
          { id: '1', name: '홍길동' },
          { id: '2', name: '김철수' },
        ]}
        rowKey={(r) => r.id}
      />,
    );

    expect(screen.getByRole('columnheader', { name: '이름' })).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'UID' })).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText('김철수')).toBeInTheDocument();
  });

  it('빈 행이면 지정한 빈 상태 메시지를 렌더한다', () => {
    render(
      <Table columns={columns} rows={[]} rowKey={(r) => r.id} emptyMessage="사용자가 없습니다." />,
    );

    expect(screen.getByText('사용자가 없습니다.')).toBeInTheDocument();
  });
});

describe('Modal', () => {
  it('열림 상태에서 제목·본문·푸터를 렌더한다', () => {
    render(
      <Modal open title="삭제 확인" onClose={vi.fn()} footer={<button type="button">삭제</button>}>
        정말 삭제할까요?
      </Modal>,
    );

    expect(screen.getByRole('dialog', { name: '삭제 확인' })).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByText('정말 삭제할까요?')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '삭제' })).toBeInTheDocument();
  });

  it('닫기 버튼이 onClose를 호출한다', async () => {
    const onClose = vi.fn();
    render(
      <Modal open title="확인" onClose={onClose}>
        본문
      </Modal>,
    );

    await userEvent.click(screen.getByRole('button', { name: '닫기' }));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('본문 클릭은 onClose를 호출하지 않는다', async () => {
    const onClose = vi.fn();
    render(
      <Modal open title="확인" onClose={onClose}>
        본문
      </Modal>,
    );

    await userEvent.click(screen.getByText('본문'));

    expect(onClose).not.toHaveBeenCalled();
  });
});

describe('TagInput', () => {
  it('Enter로 태그를 추가하며 trim+lowercase로 정규화한다', async () => {
    const onChange = vi.fn();
    render(<TagInput tags={[]} onChange={onChange} />);

    await userEvent.type(screen.getByLabelText('태그 입력'), '  React  {enter}');

    expect(onChange).toHaveBeenCalledWith(['react']);
  });

  it('중복 태그는 추가하지 않는다', async () => {
    const onChange = vi.fn();
    render(<TagInput tags={['react']} onChange={onChange} />);

    await userEvent.type(screen.getByLabelText('태그 입력'), 'React{enter}');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('빈 값은 추가하지 않는다', async () => {
    const onChange = vi.fn();
    render(<TagInput tags={[]} onChange={onChange} />);

    await userEvent.type(screen.getByLabelText('태그 입력'), '   {enter}');

    expect(onChange).not.toHaveBeenCalled();
  });

  it('칩의 × 로 태그를 제거한다', async () => {
    const onChange = vi.fn();
    render(<TagInput tags={['react', 'spring']} onChange={onChange} />);

    await userEvent.click(screen.getByRole('button', { name: 'react 태그 제거' }));

    expect(onChange).toHaveBeenCalledWith(['spring']);
  });
});
