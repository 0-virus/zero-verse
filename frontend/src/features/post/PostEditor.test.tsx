import { beforeAll, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PostEditor } from './PostEditor';
import type { PostDetail, PostSummary } from './types';

const category = { id: 3, name: '개발', type: 'GENERAL' as const };
const draft: PostSummary = {
  id: 7,
  blogId: 1,
  blogSlug: 'orbit',
  blogTitle: '궤도역학',
  author: { id: 2, nickname: '작성자', profileImageUrl: null },
  category: { id: 3, name: '개발' },
  title: '저장된 초안',
  thumbnailUrl: null,
  visibility: 'PRIVATE',
  viewCount: 0,
  publishedAt: null,
  createdAt: '2026-10-03T00:00:00Z',
  updatedAt: '2026-10-03T01:00:00Z',
  tags: [],
  excerpt: '초안 미리보기',
};

// ProseMirror asks jsdom for browser-only geometry APIs while moving the caret.
// Keep this test focused on editor state rather than jsdom's absent layout engine.
beforeAll(() => {
  Object.defineProperty(document, 'elementFromPoint', {
    configurable: true,
    value: () => document.querySelector('[data-testid="post-editor-surface"]'),
  });
  Object.defineProperty(window, 'scrollBy', {
    configurable: true,
    value: () => undefined,
  });
  Object.defineProperty(Range.prototype, 'getClientRects', {
    configurable: true,
    value: () => [{ top: 0, bottom: 1, left: 0, right: 1 }],
  });
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({ top: 0, bottom: 1, left: 0, right: 1 }),
  });
});

function renderEditor(overrides: Partial<React.ComponentProps<typeof PostEditor>> = {}) {
  const onSave = vi.fn().mockResolvedValue(undefined as PostDetail | undefined);
  const onDraftSelect = vi.fn();
  const view = render(
    <PostEditor
      mode="create"
      blogId={1}
      categories={[category]}
      onSave={onSave}
      onDraftSelect={onDraftSelect}
      listDrafts={async () => ({
        items: [draft],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      })}
      {...overrides}
    />,
  );
  return { ...view, onSave, onDraftSelect };
}

describe('PostEditor', () => {
  it('실제 DEFAULT 카테고리가 있으면 null fallback 미분류를 중복 렌더하지 않는다', () => {
    renderEditor({
      categories: [
        { id: 1, name: '미분류', type: 'DEFAULT' },
        category,
      ],
    });

    expect(screen.getAllByRole('option', { name: '미분류' })).toHaveLength(1);
  });

  it('GENERAL이 DEFAULT보다 먼저 와도 실제 DEFAULT ID를 선택하고 payload에 보존한다', async () => {
    const user = userEvent.setup();
    const { onSave } = renderEditor({
      categories: [
        category,
        { id: 1, name: '미분류', type: 'DEFAULT' },
      ],
    });

    const categorySelect = screen.getByRole('combobox', { name: '카테고리' });
    expect(categorySelect).toHaveValue('1');
    await user.click(screen.getByRole('button', { name: '임시저장' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave.mock.calls[0][0]).toMatchObject({ categoryId: 1 });
  });

  it('toolbar와 본문을 실제 TipTap JSON/HTML snapshot으로 저장한다', async () => {
    const user = userEvent.setup();
    const { onSave } = renderEditor();

    await user.type(screen.getByRole('textbox', { name: '제목' }), '새 글');
    const editor = screen.getByTestId('post-editor-surface');
    await user.click(editor);
    await user.type(editor, '본문 내용');
    await user.type(screen.getByRole('textbox', { name: '태그' }), '#React react #Spring #spring');
    await user.click(screen.getByRole('button', { name: 'B' }));
    expect(screen.getByRole('button', { name: 'B' })).toHaveAttribute('aria-pressed', 'true');
    await user.click(screen.getByRole('button', { name: '발행하기 ✦' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const request = onSave.mock.calls[0][0] as {
      title: string;
      contentJson: Record<string, unknown>;
      contentHtml: string;
      publish: boolean;
      tagNames: string[];
    };
    expect(request.title).toBe('새 글');
    expect(request.publish).toBe(true);
    expect(request.tagNames).toEqual(['react', 'spring']);
    expect(JSON.stringify(request.contentJson)).toContain('본문 내용');
    expect(request.contentHtml).toContain('본문 내용');
  });

  it('Markdown 붙여넣기는 안전한 TipTap JSON으로 복원한다', async () => {
    const user = userEvent.setup();
    const { onSave } = renderEditor();
    const editor = screen.getByTestId('post-editor-surface');
    const clipboardData = {
      getData: () => '# Markdown 제목\n\n**강조**',
    };

    fireEvent.paste(editor, { clipboardData });
    await user.click(screen.getByRole('button', { name: '임시저장' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const request = onSave.mock.calls[0][0] as { contentJson: Record<string, unknown>; publish: boolean };
    expect(request.publish).toBe(false);
    expect(JSON.stringify(request.contentJson)).toContain('Markdown 제목');
    expect(JSON.stringify(request.contentJson)).toContain('강조');
  });

  it('일반 Markdown 붙여넣기는 선택 영역 앞뒤 본문을 보존하고 선택 위치에 삽입한다', async () => {
    const user = userEvent.setup();
    const { onSave } = renderEditor();
    const editor = screen.getByTestId('post-editor-surface');

    await user.click(editor);
    await user.type(editor, '앞본문뒤');
    await user.keyboard('{ArrowLeft}{ArrowLeft}');
    fireEvent.focus(editor);
    fireEvent.paste(editor, { clipboardData: { getData: () => '**삽입 본문**' } });
    await user.click(screen.getByRole('button', { name: '임시저장' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const serialized = JSON.stringify(
      (onSave.mock.calls[0][0] as { contentJson: Record<string, unknown> }).contentJson,
    );
    expect(serialized).toContain('앞본문');
    expect(serialized).toContain('삽입 본문');
    expect(serialized).toContain('뒤');
    expect(serialized.indexOf('앞본문')).toBeLessThan(serialized.indexOf('뒤'));
  });

  it('마크다운 toolbar 버튼도 기존 문서를 교체하지 않고 현재 선택 위치에 삽입한다', async () => {
    const user = userEvent.setup();
    Object.defineProperty(window.navigator, 'clipboard', {
      configurable: true,
      writable: true,
      value: { readText: vi.fn().mockResolvedValue('**버튼 삽입**') },
    });
    const { onSave } = renderEditor();
    const editor = screen.getByTestId('post-editor-surface');

    await user.click(editor);
    await user.type(editor, '버튼앞뒤');
    await user.keyboard('{ArrowLeft}{ArrowLeft}');
    await user.click(screen.getByRole('button', { name: '마크다운 붙여넣기' }));
    await user.click(screen.getByRole('button', { name: '임시저장' }));

    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const serialized = JSON.stringify(
      (onSave.mock.calls[0][0] as { contentJson: Record<string, unknown> }).contentJson,
    );
    expect(serialized).toContain('버튼앞');
    expect(serialized).toContain('버튼 삽입');
    expect(serialized).toContain('뒤');
  });

  it('편집 저장은 orderedList와 table 병합 attrs를 JSON snapshot에 round-trip한다', async () => {
    const user = userEvent.setup();
    const structuredContent = {
      type: 'doc',
      content: [
        {
          type: 'orderedList',
          attrs: { start: 5, type: 'A' },
          content: [
            { type: 'listItem', content: [{ type: 'paragraph', content: [{ type: 'text', text: '다섯째' }] }] },
          ],
        },
        {
          type: 'table',
          content: [
            {
              type: 'tableRow',
              content: [
                { type: 'tableCell', attrs: { colspan: 2 }, content: [{ type: 'paragraph', content: [{ type: 'text', text: '가로' }] }] },
                { type: 'tableCell', attrs: { rowspan: 2 }, content: [{ type: 'paragraph', content: [{ type: 'text', text: '세로' }] }] },
              ],
            },
            {
              type: 'tableRow',
              content: [
                { type: 'tableCell', attrs: { colspan: 2 }, content: [{ type: 'paragraph', content: [{ type: 'text', text: '둘째 행' }] }] },
              ],
            },
          ],
        },
      ],
    };
    const initialPost: PostDetail = {
      ...draft,
      contentJson: structuredContent,
      contentHtml: '<ol start="5"><li>다섯째</li></ol><table><tr><td colspan="2">가로</td><td rowspan="2">세로</td></tr></table>',
      images: [],
      previous: null,
      next: null,
    };
    const { onSave } = renderEditor({ mode: 'edit', postId: draft.id, initialPost });

    await user.click(screen.getByRole('button', { name: '임시저장' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const saved = (onSave.mock.calls[0][0] as { contentJson: typeof structuredContent }).contentJson;
    expect(saved.content?.[0]).toMatchObject({ type: 'orderedList', attrs: { start: 5, type: 'A' } });
    expect(saved.content?.[1]).toMatchObject({ type: 'table' });
    const rows = saved.content?.[1]?.content as Array<{ content?: Array<{ attrs?: Record<string, unknown> }> }>;
    expect(rows[0].content?.[0].attrs).toMatchObject({ colspan: 2 });
    expect(rows[0].content?.[1].attrs).toMatchObject({ rowspan: 2 });
  });

  it('draft picker와 이미지 업로드를 연결하고 canonical URL만 snapshot에 넣는다', async () => {
    const user = userEvent.setup();
    const uploadImage = vi.fn().mockResolvedValue({
      id: 'image-1',
      imageUrl: '/api/v1/uploads/image-1/content',
      contentType: 'image/png',
      size: 4,
      purpose: 'POST_IMAGE',
    });
    const { onDraftSelect, onSave } = renderEditor({ uploadImage });

    await user.click(screen.getByRole('button', { name: '임시저장 가져오기' }));
    await user.click(await screen.findByRole('button', { name: '저장된 초안' }));
    expect(onDraftSelect).toHaveBeenCalledWith(draft);

    const input = screen.getByLabelText('본문 이미지 업로드');
    const file = new File(['png'], 'body.png', { type: 'image/png' });
    fireEvent.change(input, { target: { files: [file] } });
    await waitFor(() => expect(uploadImage).toHaveBeenCalledWith(file, 'POST_IMAGE'));

    await user.click(screen.getByRole('button', { name: '임시저장' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    const request = onSave.mock.calls[0][0] as { images: Array<{ imageUrl: string }>; contentJson: Record<string, unknown> };
    expect(request.images).toEqual([
      { imageUrl: '/api/v1/uploads/image-1/content', altText: null, displayOrder: 0 },
    ]);
    expect(JSON.stringify(request.contentJson)).toContain('/api/v1/uploads/image-1/content');
    expect(JSON.stringify(request.contentJson)).not.toContain('blob:');
  });

  it('draft picker는 20개를 넘는 초안으로 페이지를 넘기고 선택을 전달한다', async () => {
    const user = userEvent.setup();
    const twentyFirst = { ...draft, id: 28, title: '21번째 초안' };
    const listDrafts = vi.fn(async ({ page = 0 }: { page?: number; size?: number } = {}) => ({
      items: page === 0 ? Array.from({ length: 20 }, (_, index) => ({ ...draft, id: index + 1, title: `${index + 1}번째 초안` })) : [twentyFirst],
      page,
      size: 20,
      totalElements: 21,
      totalPages: 2,
      hasNext: page === 0,
      hasPrevious: page > 0,
    }));
    const { onDraftSelect } = renderEditor({ listDrafts });

    await user.click(screen.getByRole('button', { name: '임시저장 가져오기' }));
    expect(await screen.findByRole('button', { name: '다음 초안 페이지' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '다음 초안 페이지' }));
    expect(await screen.findByRole('button', { name: '21번째 초안' })).toBeInTheDocument();
    expect(listDrafts).toHaveBeenCalledWith({ page: 1, size: 20 });

    await user.click(screen.getByRole('button', { name: '21번째 초안' }));
    expect(onDraftSelect).toHaveBeenCalledWith(twentyFirst);
  });

  it('저장 실패에도 입력을 보존하고 중복 클릭을 막는다', async () => {
    const user = userEvent.setup();
    let release: (() => void) | null = null;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });
    const onSave = vi.fn().mockReturnValueOnce(pending).mockRejectedValueOnce(new Error('저장 실패'));
    renderEditor({ onSave });
    const title = screen.getByRole('textbox', { name: '제목' });
    await user.type(title, '사라지면 안 되는 입력');
    const publish = screen.getByRole('button', { name: '발행하기 ✦' });
    await user.click(publish);
    await user.click(publish);
    expect(onSave).toHaveBeenCalledTimes(1);
    expect(publish).toBeDisabled();
    release!();
    await waitFor(() => expect(publish).toBeEnabled());
    expect(title).toHaveValue('사라지면 안 되는 입력');
    await user.click(publish);
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('저장 실패'));
    expect(title).toHaveValue('사라지면 안 되는 입력');
  });

  it('성공한 저장은 서버 updatedAt만 저장 시각으로 표시한다', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue({ updatedAt: '2026-10-03T02:00:00Z' } as PostDetail);
    renderEditor({ onSave });

    await user.click(screen.getByRole('button', { name: '임시저장' }));
    await waitFor(() => expect(screen.getByText(/서버 저장/)).toBeInTheDocument());
  });
});
