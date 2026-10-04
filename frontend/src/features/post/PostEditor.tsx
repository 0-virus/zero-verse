import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import { EditorContent, NodeViewWrapper, ReactNodeViewRenderer, useEditor, type NodeViewProps } from '@tiptap/react';
import type { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { TableKit } from '@tiptap/extension-table';
import { Markdown } from '@tiptap/markdown';
import { ManagedImage } from '../upload/ManagedImage';
import { uploadImage as defaultUploadImage, type UploadResponse } from '../upload/uploadApi';
import { listDrafts as defaultListDrafts } from './postApi';
import type {
  CreatePostRequest,
  PageResponse,
  PostDetail,
  PostImageInput,
  PostSummary,
  PostSnapshot,
  UpdatePostRequest,
} from './types';
import { DraftPicker } from './DraftPicker';
import { EditorToolbar } from './EditorToolbar';

const EMPTY_DOC = { type: 'doc', content: [{ type: 'paragraph' }] };

type CategoryOption = { id: number; name: string; type?: string };
type EditorSaveRequest = CreatePostRequest | UpdatePostRequest;
type DraftListParams = { page?: number; size?: number };
const DRAFT_PAGE_SIZE = 20;

export interface PostEditorProps {
  mode: 'create' | 'edit';
  blogId: number;
  postId?: number;
  initialPost?: PostDetail | null;
  categories: CategoryOption[];
  onSave: (request: EditorSaveRequest) => Promise<PostDetail | void>;
  onDraftSelect?: (draft: PostSummary) => void;
  listDrafts?: (params?: DraftListParams) => Promise<PageResponse<PostSummary>>;
  uploadImage?: (file: File, purpose: 'POST_IMAGE' | 'POST_THUMBNAIL') => Promise<UploadResponse>;
}

function flattenText(value: string): string[] {
  const normalized = value
    .split(/[\s,]+/)
    .map((tag) => tag.trim().replace(/^#+/, '').toLowerCase())
    .filter(Boolean)
    .map((tag) => tag.slice(0, 100));
  return [...new Set(normalized)].slice(0, 10);
}

function collectImageInputs(editor: Editor, current: PostImageInput[]): PostImageInput[] {
  const urls: string[] = [];
  editor.state.doc.descendants((node) => {
    if (node.type.name === 'image' && typeof node.attrs.src === 'string') urls.push(node.attrs.src);
  });
  const byUrl = new Map(current.map((image) => [image.imageUrl, image]));
  return [...new Set(urls)].map((imageUrl, displayOrder) => ({
    imageUrl,
    altText: byUrl.get(imageUrl)?.altText ?? null,
    displayOrder,
  }));
}

function AuthenticatedImageNodeView({ node }: NodeViewProps) {
  return (
    <NodeViewWrapper className="my-3 block" data-image-src={node.attrs.src as string}>
      <ManagedImage
        src={node.attrs.src as string}
        alt={(node.attrs.alt as string | null) || '본문 이미지'}
        className="max-h-[520px] max-w-full border-2 border-ink object-contain"
      />
    </NodeViewWrapper>
  );
}

const AuthenticatedImage = Image.extend({
  addNodeView() {
    return ReactNodeViewRenderer(AuthenticatedImageNodeView);
  },
});

function formatSavedAt(value: string | null | undefined): string {
  if (!value) return '아직 저장하지 않음';
  return `서버 저장 ${new Date(value).toLocaleString('ko-KR', { dateStyle: 'short', timeStyle: 'short' })}`;
}

function looksLikeMarkdown(value: string): boolean {
  return /^\s*(#{1,6}\s|[-*+]\s|>\s|```|\*\*[^\n]+\*\*|__[^\n]+__|\[[^\]]+\]\([^\n]+\))/m.test(value);
}

export function PostEditor({
  mode,
  blogId,
  postId,
  initialPost,
  categories,
  onSave,
  onDraftSelect,
  listDrafts = defaultListDrafts,
  uploadImage = defaultUploadImage,
}: PostEditorProps) {
  const defaultCategoryId = categories.find((category) => category.type === 'DEFAULT')?.id ?? null;
  const [title, setTitle] = useState(initialPost?.title ?? '');
  const [categoryId, setCategoryId] = useState<number | null>(initialPost?.category.id ?? defaultCategoryId);
  const [visibility, setVisibility] = useState(initialPost?.visibility ?? 'PUBLIC');
  const [tags, setTags] = useState(initialPost?.tags.join(' ') ?? '');
  const [thumbnailUrl, setThumbnailUrl] = useState<string | null>(initialPost?.thumbnailUrl ?? null);
  const [images, setImages] = useState<PostImageInput[]>(initialPost?.images ?? []);
  const [isSaving, setIsSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [draftOpen, setDraftOpen] = useState(false);
  const [drafts, setDrafts] = useState<PostSummary[]>([]);
  const [draftPage, setDraftPage] = useState(0);
  const [draftTotalPages, setDraftTotalPages] = useState(1);
  const [draftHasNext, setDraftHasNext] = useState(false);
  const [draftHasPrevious, setDraftHasPrevious] = useState(false);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [draftLoading, setDraftLoading] = useState(false);
  const [markdownError, setMarkdownError] = useState<string | null>(null);
  const [, refreshEditorToolbar] = useState(0);
  const [savedAt, setSavedAt] = useState(initialPost?.updatedAt ?? null);
  const imageInputRef = useRef<HTMLInputElement | null>(null);

  const extensions = useMemo(
    () => [
      StarterKit.configure({
        link: { openOnClick: false },
      }),
      Markdown,
      AuthenticatedImage.configure({ allowBase64: false }),
      TableKit,
    ],
    [],
  );

  const editor = useEditor({
    extensions,
    content: initialPost?.contentJson ?? EMPTY_DOC,
    editorProps: {
      attributes: {
        'data-testid': 'post-editor-surface',
        class: 'min-h-[340px] px-[26px] py-[22px] text-[15px] leading-[1.85] text-text-prose outline-0',
      },
      handlePaste: (_view, event) => {
        const text = event.clipboardData?.getData('text/plain');
        if (!text || !looksLikeMarkdown(text)) return false;
        event.preventDefault();
        try {
          editor?.chain().focus().insertContent(text, { contentType: 'markdown' }).run();
        } catch {
          setMarkdownError('마크다운을 본문으로 붙여넣지 못했습니다.');
        }
        return true;
      },
    },
  });

  useEffect(() => {
    if (!editor) return;
    const refresh = () => refreshEditorToolbar((version) => version + 1);
    editor.on('transaction', refresh);
    editor.on('selectionUpdate', refresh);
    return () => {
      editor.off('transaction', refresh);
      editor.off('selectionUpdate', refresh);
    };
  }, [editor]);

  useEffect(() => {
    if (!editor || !initialPost) return;
    editor.commands.setContent(initialPost.contentJson);
  }, [editor, initialPost]);

  const loadDraftPage = useCallback(async (pageNumber: number) => {
    setDraftLoading(true);
    setDraftError(null);
    try {
      const page = await listDrafts({ page: pageNumber, size: DRAFT_PAGE_SIZE });
      setDrafts(page.items);
      setDraftPage(page.page);
      setDraftTotalPages(page.totalPages);
      setDraftHasNext(page.hasNext);
      setDraftHasPrevious(page.hasPrevious);
    } catch {
      setDrafts([]);
      setDraftError('임시저장 글을 불러오지 못했습니다.');
    } finally {
      setDraftLoading(false);
    }
  }, [listDrafts]);

  const openDraftPicker = useCallback(async () => {
    setDraftOpen(true);
    await loadDraftPage(0);
  }, [loadDraftPage]);

  const handleImageFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !editor) return;
    setSaveError(null);
    setIsUploading(true);
    try {
      const uploaded = await uploadImage(file, 'POST_IMAGE');
      editor.chain().focus().setImage({ src: uploaded.imageUrl, alt: '' }).run();
      setImages((current) => [
        ...current.filter((image) => image.imageUrl !== uploaded.imageUrl),
        { imageUrl: uploaded.imageUrl, altText: null, displayOrder: current.length },
      ]);
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : '이미지 업로드에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleThumbnailFile = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setIsUploading(true);
    setSaveError(null);
    try {
      const uploaded = await uploadImage(file, 'POST_THUMBNAIL');
      setThumbnailUrl(uploaded.imageUrl);
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : '대표 이미지 업로드에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleMarkdownButton = async () => {
    if (!editor) return;
    try {
      const markdown = await navigator.clipboard?.readText();
      if (!markdown) return;
      editor.chain().focus().insertContent(markdown, { contentType: 'markdown' }).run();
    } catch {
      setMarkdownError('클립보드의 마크다운을 읽지 못했습니다. 본문에 직접 붙여넣어 주세요.');
    }
  };

  const save = async (publish: boolean) => {
    if (!editor || isSaving || isUploading) return;
    setSaveError(null);
    setIsSaving(true);
    const snapshot: PostSnapshot = {
      title,
      contentJson: editor.getJSON() as Record<string, unknown>,
      contentHtml: editor.getHTML(),
      categoryId,
      visibility,
      publish,
      thumbnailUrl,
      tagNames: flattenText(tags),
      images: collectImageInputs(editor, images),
    };
    try {
      const saved = mode === 'create'
        ? await onSave({ ...snapshot, blogId })
        : postId != null
          ? await onSave(snapshot)
          : null;
      if (saved?.updatedAt) setSavedAt(saved.updatedAt);
      if (mode === 'edit' && postId == null) throw new Error('수정할 글 ID가 없습니다.');
      setImages(snapshot.images);
    } catch (error) {
      setSaveError(error instanceof Error ? error.message : '글을 저장하지 못했습니다.');
    } finally {
      setIsSaving(false);
    }
  };

  const hints = {
    PUBLIC: '유니버스의 모든 별에게 공개됩니다.',
    UNIVERSE: '유니버스로 연결된 친구에게만 보입니다.',
    PRIVATE: '나만 볼 수 있습니다. 언제든 공개로 바꿀 수 있어요.',
  } as const;

  return (
    <div className="mx-auto max-w-[1060px] px-10 py-7 pb-[70px]">
      <div className="mb-[18px] flex items-center gap-3">
        <span className="font-pixel text-[11px] text-ink">{mode === 'create' ? 'NEW LOG_' : 'EDIT LOG'}</span>
        <div className="flex-1" />
        <span className="text-xs text-text-muted">{formatSavedAt(savedAt)}</span>
        <button
          type="button"
          className="border-[3px] border-ink bg-surface px-4 py-2 text-[13px] font-semibold shadow-btn hover:bg-surface-raise disabled:cursor-not-allowed disabled:opacity-60"
          onClick={() => void save(false)}
          disabled={isSaving || isUploading || !editor}
        >
          {isSaving ? '저장 중...' : '임시저장'}
        </button>
        <button
          type="button"
          className="border-[3px] border-ink bg-accent px-[18px] py-2 text-[13px] font-bold text-white shadow-btn hover:brightness-108 disabled:cursor-not-allowed disabled:opacity-60"
          onClick={() => void save(true)}
          disabled={isSaving || isUploading || !editor}
        >
          발행하기 ✦
        </button>
      </div>

      <section className="border-[3px] border-ink bg-surface shadow-panel">
        <input
          aria-label="제목"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="제목을 입력하세요"
          className="w-full border-0 border-b-[3px] border-ink bg-transparent px-[26px] py-5 text-2xl font-bold text-ink outline-0"
        />
        {editor && (
          <EditorToolbar
            editor={editor}
            onImageFile={handleImageFile}
            onMarkdownPaste={() => void handleMarkdownButton()}
            isUploading={isUploading}
          />
        )}
        <EditorContent editor={editor} />
      </section>

      {(saveError || markdownError) && (
        <div role="alert" className="mt-3 border-2 border-danger bg-danger-bg px-4 py-3 text-sm text-danger">
          {saveError ?? markdownError}
        </div>
      )}

      <div className="mt-[18px] grid grid-cols-2 gap-[18px]">
        <section className="border-[3px] border-ink bg-surface p-4 shadow-card">
          <h2 className="mb-2.5 text-xs font-bold tracking-[3px]">■ 발행 설정</h2>
          <div className="flex flex-col gap-2.5 text-[13px]">
            <label className="flex items-center gap-2.5">
              <span className="w-[70px] text-text-muted">카테고리</span>
              <select
                aria-label="카테고리"
                value={categoryId ?? ''}
                onChange={(event) => setCategoryId(event.target.value ? Number(event.target.value) : null)}
                className="flex-1 border-2 border-ink bg-surface-warm px-2 py-1.5"
              >
                {!categories.some((category) => category.type === 'DEFAULT') && <option value="">미분류</option>}
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex items-center gap-2.5">
              <span className="w-[70px] text-text-muted">태그</span>
              <input
                aria-label="태그"
                value={tags}
                onChange={(event) => setTags(event.target.value)}
                placeholder="#Spring #QueryDSL (최대 10개)"
                className="flex-1 border-2 border-ink bg-surface-warm px-2.5 py-1.5 outline-0"
              />
            </label>
            <div className="flex items-center gap-2.5">
              <span className="w-[70px] text-text-muted">대표 이미지</span>
              <label className="cursor-pointer border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold hover:bg-surface-raise">
                {thumbnailUrl ? '대표 이미지 교체' : '대표 이미지 업로드'}
                <input
                  type="file"
                  aria-label="대표 이미지 업로드"
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  className="sr-only"
                  onChange={handleThumbnailFile}
                  disabled={isUploading}
                />
              </label>
              {thumbnailUrl && (
                <>
                  <ManagedImage
                    src={thumbnailUrl}
                    alt="대표 이미지 미리보기"
                    className="h-12 w-20 border-2 border-ink object-cover"
                  />
                  <button type="button" className="text-xs text-danger" onClick={() => setThumbnailUrl(null)}>
                    제거
                  </button>
                </>
              )}
            </div>
            <button
              type="button"
              className="self-start border-2 border-shadow bg-surface px-3 py-1.5 text-xs text-text-body hover:border-ink"
              onClick={() => void openDraftPicker()}
            >
              임시저장 가져오기
            </button>
            <p className="text-[11px] text-text-muted">이미지는 JPEG/PNG/GIF/WebP, 최대 5MB까지 사용할 수 있습니다.</p>
          </div>
        </section>

        <section className="border-[3px] border-ink bg-surface p-4 shadow-card">
          <h2 className="mb-2.5 text-xs font-bold tracking-[3px]">■ 공개 범위</h2>
          <div className="flex gap-2">
            {(['PUBLIC', 'UNIVERSE', 'PRIVATE'] as const).map((value) => (
              <button
                type="button"
                key={value}
                onClick={() => setVisibility(value)}
                aria-pressed={visibility === value}
                className={`flex-1 border-[3px] px-1 py-2 text-xs font-bold ${
                  visibility === value
                    ? 'border-ink bg-surface-raise text-ink'
                    : 'border-shadow bg-surface text-text-muted'
                }`}
              >
                {value === 'UNIVERSE' ? '친구 공개' : value === 'PUBLIC' ? '전체 공개' : '비공개'}
              </button>
            ))}
          </div>
          <p className="mt-2 text-[11.5px] text-text-muted">{hints[visibility]}</p>
        </section>
      </div>

      {draftOpen && (
        <DraftPicker
          drafts={drafts}
          isLoading={draftLoading}
          error={draftError}
          page={draftPage}
          totalPages={draftTotalPages}
          hasNext={draftHasNext}
          hasPrevious={draftHasPrevious}
          onPageChange={(page) => void loadDraftPage(page)}
          onClose={() => setDraftOpen(false)}
          onSelect={(draft) => {
            setDraftOpen(false);
            onDraftSelect?.(draft);
          }}
        />
      )}
      <input ref={imageInputRef} type="file" className="sr-only" tabIndex={-1} aria-hidden="true" />
    </div>
  );
}
