import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { Tabs } from '../components/ui/Tab';
import { Badge } from '../components/ui/Badge';
import { TagChip } from '../components/ui/TagChip';
import { Panel } from '../components/ui/Panel';
import { PostCard } from '../components/ui/PostCard';
import { FormField } from '../components/ui/FormField';
import { ListRow } from '../components/ui/ListRow';
import { Pagination } from '../components/ui/Pagination';
import { Avatar } from '../components/ui/Avatar';
import { Callout, CodeBlock, Prose } from '../components/ui/Prose';
import { Table } from '../components/ui/Table';
import { Modal } from '../components/ui/Modal';
import { TagInput } from '../components/ui/TagInput';

/** 공용 컴포넌트와 대표 페이지가 console error 없이 렌더되는지 확인한다. */
describe('렌더 스모크', () => {
  let errorSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it('공용 UI 14종을 렌더한다', () => {
    render(
      <MemoryRouter>
        <Button>버튼</Button>
        <Tabs items={[{ key: 'all', label: '전체' }]} activeKey="all" onChange={vi.fn()} />
        <Badge kind="PUBLIC" />
        <TagChip name="react" />
        <Panel title="PANEL">패널 본문</Panel>
        <PostCard title="글" meta="메타" commentCount={0} likeCount={0} />
        <FormField label="닉네임" />
        <ListRow title="사용자" subtitle="설명" />
        <Pagination page={0} totalPages={2} onChange={vi.fn()} />
        <Avatar emoji="🚀" />
        <Prose>
          <p>본문</p>
          <Callout>인용</Callout>
          <CodeBlock code="const a = 1;" />
        </Prose>
        <Table
          columns={[{ key: 'name', header: '이름', render: (r: { name: string }) => r.name }]}
          rows={[{ name: '홍길동' }]}
          rowKey={(r) => r.name}
        />
        <Modal open title="확인" onClose={vi.fn()}>
          모달 본문
        </Modal>
        <TagInput tags={['react']} onChange={vi.fn()} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: '버튼' })).toBeInTheDocument();
    expect(screen.getByRole('dialog', { name: '확인' })).toBeInTheDocument();
    expect(screen.getByRole('table')).toBeInTheDocument();
  });

  it('빈 Table은 빈 상태 메시지를 렌더한다', () => {
    render(
      <Table
        columns={[{ key: 'name', header: '이름', render: (r: { name: string }) => r.name }]}
        rows={[]}
        rowKey={(r) => r.name}
      />,
    );

    expect(screen.getByText('항목이 없습니다.')).toBeInTheDocument();
  });

  it('닫힌 Modal은 렌더하지 않는다', () => {
    const { container } = render(
      <Modal open={false} title="확인" onClose={vi.fn()}>
        본문
      </Modal>,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
