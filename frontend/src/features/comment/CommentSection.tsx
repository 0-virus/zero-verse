import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { AuthUser } from '../../types/auth';
import { ApiRequestError } from '../../lib/apiClient';
import { Button } from '../../components/ui/Button';
import { createComment, deleteComment, listComments, updateComment } from './commentApi';
import { CommentRow } from './CommentRow';
import type { CommentItem } from './types';
import type { InteractionState } from '../like/types';
import type { PageResponse } from '../post/types';

const PAGE_SIZE = 20;
const CANONICAL_RELOAD_NOTICE = '변경사항은 저장됐지만 최신 댓글을 불러오지 못했습니다.';

export interface CommentSectionProps {
  postId: number;
  viewer: AuthUser | null;
  interaction: InteractionState | null;
  interactionLoading: boolean;
  onRefreshInteractions: () => Promise<void>;
  onInteractionAccessDenied?: (error: unknown) => void;
}

type ReplyPageMap = Record<number, PageResponse<CommentItem>>;
type ReplyLoadingMap = Record<number, boolean>;
type ReplyErrorMap = Record<number, string | null>;
type LoadOptions = { reportError?: boolean; throwOnAccessDenied?: boolean };
type CanonicalReloadTarget = { rootId?: number; replyPageNumber?: number };

function isReadAccessError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError
    && (error.code === 'AUTH_004' || error.code === 'POST_001' || error.code === 'POST_002');
}

function readAccessErrorMessage(error: unknown): string {
  if (error instanceof ApiRequestError) {
    if (error.code === 'AUTH_004') return '로그인 후 이 글의 댓글을 볼 수 있습니다.';
    if (error.code === 'POST_001') return '글을 찾을 수 없습니다.';
    if (error.code === 'POST_002') return '이 글의 댓글을 볼 권한이 없습니다.';
  }
  return '이 글의 댓글을 불러오지 못했습니다.';
}

function emptyPage(): PageResponse<CommentItem> {
  return {
    items: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
    hasPrevious: false,
  };
}

function displayError(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    if (error.status === 401) return '로그인 후 댓글을 남길 수 있습니다.';
    if (error.code === 'COM_003' || error.code === 'POST_002') {
      return '현재 상태에서는 댓글을 남길 수 없습니다.';
    }
    if (error.code === 'COM_002') return '답글은 한 단계까지만 남길 수 있습니다.';
    if (error.code === 'COM_001') return '댓글을 찾을 수 없습니다.';
    if (error.code === 'VALIDATION_001') return error.message || '댓글 내용을 확인해 주세요.';
    return error.message || fallback;
  }
  return fallback;
}

function pageWithItem(
  page: PageResponse<CommentItem>,
  id: number,
  update: (item: CommentItem) => CommentItem,
): PageResponse<CommentItem> {
  return { ...page, items: page.items.map((item) => item.id === id ? update(item) : item) };
}

function addItem(page: PageResponse<CommentItem>, item: CommentItem): PageResponse<CommentItem> {
  return { ...page, items: [...page.items, item], totalElements: page.totalElements + 1 };
}

function CommentPagination({
  label,
  page,
  onChange,
}: {
  label: string;
  page: PageResponse<CommentItem>;
  onChange: (nextPage: number) => void;
}) {
  if (page.totalPages <= 1) return null;
  return (
    <nav aria-label={`${label} 페이지`} className="flex justify-end gap-1.5 px-5 py-3">
      <button
        type="button"
        aria-label={`${label} 이전 페이지`}
        disabled={!page.hasPrevious}
        onClick={() => onChange(page.page - 1)}
        className="h-8 w-8 border-2 border-shadow bg-surface text-xs disabled:cursor-not-allowed disabled:opacity-50 enabled:cursor-pointer enabled:hover:border-ink"
      >
        ‹
      </button>
      <span className="flex h-8 min-w-8 items-center justify-center border-2 border-ink bg-ink px-2 text-xs text-text-on-ink">
        {page.page + 1}
      </span>
      <button
        type="button"
        aria-label={`${label} 다음 페이지`}
        disabled={!page.hasNext}
        onClick={() => onChange(page.page + 1)}
        className="h-8 w-8 border-2 border-shadow bg-surface text-xs disabled:cursor-not-allowed disabled:opacity-50 enabled:cursor-pointer enabled:hover:border-ink"
      >
        ›
      </button>
    </nav>
  );
}

function ActionButton({
  children,
  onClick,
  disabled = false,
  label,
}: {
  children: ReactNode;
  onClick: () => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      disabled={disabled}
      className="cursor-pointer text-[11px] text-text-muted underline-offset-2 hover:text-ink hover:underline disabled:cursor-not-allowed disabled:opacity-50"
    >
      {children}
    </button>
  );
}

export function CommentSection({
  postId,
  viewer,
  interaction,
  interactionLoading,
  onRefreshInteractions,
  onInteractionAccessDenied,
}: CommentSectionProps) {
  const [rootPage, setRootPage] = useState<PageResponse<CommentItem> | null>(null);
  const [rootPageNumber, setRootPageNumber] = useState(0);
  const [rootLoading, setRootLoading] = useState(true);
  const [rootError, setRootError] = useState<string | null>(null);
  const [replyPages, setReplyPages] = useState<ReplyPageMap>({});
  const [replyLoading, setReplyLoading] = useState<ReplyLoadingMap>({});
  const [replyErrors, setReplyErrors] = useState<ReplyErrorMap>({});
  const [rootDraft, setRootDraft] = useState('');
  const [replyDrafts, setReplyDrafts] = useState<Record<number, string>>({});
  const [openReplyId, setOpenReplyId] = useState<number | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingDraft, setEditingDraft] = useState('');
  const [busy, setBusy] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [canonicalReloadTarget, setCanonicalReloadTarget] = useState<CanonicalReloadTarget | null>(null);
  const generationRef = useRef(0);
  const rootPageGenerationRef = useRef(0);
  const rootRequestRef = useRef(0);
  const replyRequestRef = useRef<Record<number, number>>({});
  const mountedRef = useRef(true);

  const clearReadState = useCallback((error: unknown) => {
    rootPageGenerationRef.current += 1;
    rootRequestRef.current += 1;
    replyRequestRef.current = {};
    setRootPage(null);
    setRootError(readAccessErrorMessage(error));
    setRootLoading(false);
    setReplyPages({});
    setReplyErrors({});
    setReplyLoading({});
    onInteractionAccessDenied?.(error);
  }, [onInteractionAccessDenied]);

  const isCurrent = useCallback((generation: number) => (
    mountedRef.current && generationRef.current === generation
  ), []);

  useEffect(() => {
    mountedRef.current = true;
    const generation = ++generationRef.current;
    rootPageGenerationRef.current += 1;
    setRootPage(null);
    setRootPageNumber(0);
    setRootError(null);
    setRootLoading(true);
    setReplyPages({});
    setReplyErrors({});
    setReplyLoading({});
    replyRequestRef.current = {};
    setBusy(null);
    setNotice(null);
    setCanonicalReloadTarget(null);
    setEditingId(null);
    setOpenReplyId(null);
    return () => {
      mountedRef.current = false;
      if (generationRef.current === generation) generationRef.current += 1;
    };
  }, [postId, viewer?.id]);

  const loadRootPage = useCallback(async (pageNumber: number, { reportError = true, throwOnAccessDenied = false }: LoadOptions = {}): Promise<PageResponse<CommentItem> | null> => {
    const generation = generationRef.current;
    const rootPageGeneration = ++rootPageGenerationRef.current;
    const requestId = ++rootRequestRef.current;
    replyRequestRef.current = {};
    setReplyLoading({});
    setReplyErrors({});
    try {
      const loaded = await listComments(postId, { page: pageNumber, size: PAGE_SIZE });
      if (!isCurrent(generation) || requestId !== rootRequestRef.current || rootPageGeneration !== rootPageGenerationRef.current) return null;
      setRootPage(loaded);
      setReplyPages(loaded.items.reduce<ReplyPageMap>((pages, item) => {
        if (item.replies) pages[item.id] = item.replies;
        return pages;
      }, {}));
      setRootError(null);
      return loaded;
    } catch (error) {
      if (isCurrent(generation) && requestId === rootRequestRef.current && rootPageGeneration === rootPageGenerationRef.current) {
        if (isReadAccessError(error)) {
          clearReadState(error);
          if (throwOnAccessDenied) throw error;
        } else if (reportError) {
          setRootError(displayError(error, '댓글을 불러오지 못했습니다.'));
        }
      }
      return null;
    } finally {
      if (isCurrent(generation) && requestId === rootRequestRef.current && rootPageGeneration === rootPageGenerationRef.current) setRootLoading(false);
    }
  }, [clearReadState, isCurrent, postId]);

  useEffect(() => {
    void loadRootPage(rootPageNumber);
  }, [loadRootPage, rootPageNumber, viewer?.id]);

  const loadReplyPage = useCallback(async (rootId: number, pageNumber: number, { reportError = true, throwOnAccessDenied = false }: LoadOptions = {}): Promise<PageResponse<CommentItem> | null> => {
    const generation = generationRef.current;
    const rootPageGeneration = rootPageGenerationRef.current;
    const requestId = (replyRequestRef.current[rootId] ?? 0) + 1;
    replyRequestRef.current[rootId] = requestId;
    setReplyLoading((current) => ({ ...current, [rootId]: true }));
    setReplyErrors((current) => ({ ...current, [rootId]: null }));
    try {
      const loaded = await listComments(postId, { parentId: rootId, page: pageNumber, size: PAGE_SIZE });
      if (!isCurrent(generation) || rootPageGeneration !== rootPageGenerationRef.current || replyRequestRef.current[rootId] !== requestId) return null;
      setReplyPages((current) => ({ ...current, [rootId]: loaded }));
      setRootPage((current) => current && {
        ...current,
        items: current.items.map((item) => item.id === rootId ? { ...item, replies: loaded } : item),
      });
      return loaded;
    } catch (error) {
      if (isCurrent(generation) && rootPageGeneration === rootPageGenerationRef.current && replyRequestRef.current[rootId] === requestId) {
        if (isReadAccessError(error)) {
          clearReadState(error);
          if (throwOnAccessDenied) throw error;
        } else if (reportError) {
          setReplyErrors((current) => ({ ...current, [rootId]: displayError(error, '답글을 불러오지 못했습니다.') }));
        }
      }
      return null;
    } finally {
      if (isCurrent(generation) && rootPageGeneration === rootPageGenerationRef.current && replyRequestRef.current[rootId] === requestId) {
        setReplyLoading((current) => ({ ...current, [rootId]: false }));
      }
    }
  }, [clearReadState, isCurrent, postId]);

  const refreshCanonical = useCallback(async (rootId?: number, replyPageNumber?: number) => {
    const generation = generationRef.current;
    const currentRootPage = rootPage?.page ?? rootPageNumber;
    const loadedRoot = await loadRootPage(currentRootPage, { reportError: false, throwOnAccessDenied: true });
    if (!isCurrent(generation)) return;
    if (!loadedRoot) throw new Error('댓글 canonical reload failed');
    if (rootId != null) {
      const loadedReplies = await loadReplyPage(rootId, replyPageNumber ?? replyPages[rootId]?.page ?? 0, { reportError: false, throwOnAccessDenied: true });
      if (!isCurrent(generation)) return;
      if (!loadedReplies) throw new Error('답글 canonical reload failed');
    }
    if (!isCurrent(generation)) return;
    try {
      await onRefreshInteractions();
    } catch (error) {
      if (!isCurrent(generation)) return;
      if (isReadAccessError(error)) clearReadState(error);
      throw error;
    }
    if (!isCurrent(generation)) return;
  }, [clearReadState, isCurrent, loadReplyPage, loadRootPage, onRefreshInteractions, replyPages, rootPage?.page, rootPageNumber]);

  const retryCanonical = async () => {
    if (!canonicalReloadTarget || busy) return;
    const generation = generationRef.current;
    setBusy('canonical-reload');
    try {
      await refreshCanonical(canonicalReloadTarget.rootId, canonicalReloadTarget.replyPageNumber);
      if (!isCurrent(generation)) return;
      setCanonicalReloadTarget(null);
      setNotice(null);
    } catch (error) {
      if (isCurrent(generation)) {
        if (isReadAccessError(error)) {
          setCanonicalReloadTarget(null);
          setNotice(readAccessErrorMessage(error));
        } else {
          setNotice(CANONICAL_RELOAD_NOTICE);
        }
      }
    } finally {
      if (isCurrent(generation)) setBusy(null);
    }
  };

  const handleCanonicalReloadFailure = useCallback((error: unknown, target: CanonicalReloadTarget) => {
    if (isReadAccessError(error)) {
      setCanonicalReloadTarget(null);
      setNotice(readAccessErrorMessage(error));
      return;
    }
    setCanonicalReloadTarget(target);
    setNotice(CANONICAL_RELOAD_NOTICE);
  }, []);

  const findRootId = useCallback((commentId: number): number | null => {
    if (!rootPage) return null;
    const root = rootPage.items.find((item) => item.id === commentId);
    if (root) return root.id;
    for (const item of rootPage.items) {
      const replies = replyPages[item.id] ?? item.replies;
      if (replies?.items.some((reply) => reply.id === commentId)) return item.id;
    }
    return null;
  }, [replyPages, rootPage]);

  const updateLocal = useCallback((commentId: number, update: (item: CommentItem) => CommentItem) => {
    setRootPage((current) => current ? pageWithItem(current, commentId, update) : current);
    setReplyPages((current) => Object.fromEntries(
      Object.entries(current).map(([rootId, page]) => [rootId, pageWithItem(page, commentId, update)]),
    ));
  }, []);

  const addLocal = useCallback((item: CommentItem) => {
    if (item.parentId == null) {
      setRootPage((current) => current ? addItem(current, item) : current);
      return;
    }
    setReplyPages((current) => {
      const existing = current[item.parentId!] ?? emptyPage();
      return { ...current, [item.parentId!]: addItem(existing, item) };
    });
  }, []);

  const optimisticComment = (parentId: number | null, content: string): CommentItem => ({
    id: -Date.now(),
    postId,
    parentId,
    author: viewer ? {
      id: viewer.id,
      nickname: viewer.nickname,
      profileImageUrl: viewer.profileImageUrl,
    } : null,
    content,
    deleted: false,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    canEdit: false,
    canDelete: false,
    canReply: false,
    replies: null,
  });

  const submitComment = async (event: FormEvent<HTMLFormElement>, parentId: number | null = null) => {
    event.preventDefault();
    if (!viewer || !interaction?.canComment || busy) return;
    const original = parentId == null ? rootDraft : (replyDrafts[parentId] ?? '');
    const content = original.trim();
    if (!content) {
      setNotice('댓글 내용을 입력해 주세요.');
      return;
    }
    if (content.length > 2000) {
      setNotice('댓글은 2000자 이내로 입력해 주세요.');
      return;
    }

    const generation = generationRef.current;
    const temp = optimisticComment(parentId, content);
    const beforeRoot = rootPage;
    const beforeReplies = replyPages;
    const busyKey = parentId == null ? 'create:root' : `create:${parentId}`;
    setBusy(busyKey);
    setNotice(null);
    setCanonicalReloadTarget(null);
    addLocal(temp);
    try {
      let created: CommentItem;
      try {
        created = await createComment(postId, parentId == null ? { content, parentId: null } : { content, parentId });
      } catch (error) {
        if (isCurrent(generation)) {
          if (isReadAccessError(error)) {
            clearReadState(error);
          } else {
            setRootPage(beforeRoot);
            setReplyPages(beforeReplies);
            setNotice(displayError(error, '댓글을 작성하지 못했습니다.'));
          }
        }
        return;
      }
      if (!isCurrent(generation)) return;
      // Keep the server's canonical DTO even if the follow-up list reload is slow or fails;
      // the temporary negative id must never survive a successful mutation.
      updateLocal(temp.id, () => created);
      if (parentId == null) setRootDraft('');
      else setReplyDrafts((current) => ({ ...current, [parentId]: '' }));
      setOpenReplyId(null);
      try {
        await refreshCanonical(parentId ?? undefined);
      } catch (error) {
        if (isCurrent(generation)) {
          handleCanonicalReloadFailure(error, parentId == null ? {} : { rootId: parentId });
        }
      }
    } finally {
      if (isCurrent(generation)) setBusy(null);
    }
  };

  const startEdit = (item: CommentItem) => {
    if (item.deleted || !item.canEdit) return;
    setEditingId(item.id);
    setEditingDraft(item.content ?? '');
    setNotice(null);
  };

  const saveEdit = async (item: CommentItem) => {
    if (!viewer || !item.canEdit || !editingDraft.trim() || busy) return;
    const content = editingDraft.trim();
    if (content.length > 2000) {
      setNotice('댓글은 2000자 이내로 입력해 주세요.');
      return;
    }
    const generation = generationRef.current;
    const beforeRoot = rootPage;
    const beforeReplies = replyPages;
    const rootId = findRootId(item.id);
    const busyKey = `edit:${item.id}`;
    setBusy(busyKey);
    setNotice(null);
    setCanonicalReloadTarget(null);
    updateLocal(item.id, (current) => ({ ...current, content, updatedAt: new Date().toISOString() }));
    try {
      try {
        await updateComment(item.id, { content });
      } catch (error) {
        if (isCurrent(generation)) {
          if (isReadAccessError(error)) {
            clearReadState(error);
          } else {
            setRootPage(beforeRoot);
            setReplyPages(beforeReplies);
            setNotice(displayError(error, '댓글을 수정하지 못했습니다.'));
          }
        }
        return;
      }
      if (!isCurrent(generation)) return;
      setEditingId(null);
      try {
        const canonicalRootId = rootId != null && rootId !== item.id ? rootId : undefined;
        await refreshCanonical(canonicalRootId);
      } catch (error) {
        if (isCurrent(generation)) {
          const canonicalRootId = rootId != null && rootId !== item.id ? rootId : undefined;
          handleCanonicalReloadFailure(error, canonicalRootId == null ? {} : { rootId: canonicalRootId });
        }
      }
    } finally {
      if (isCurrent(generation)) setBusy(null);
    }
  };

  const remove = async (item: CommentItem) => {
    if (!item.canDelete || item.deleted || busy || !window.confirm('이 댓글을 삭제할까요?')) return;
    const generation = generationRef.current;
    const beforeRoot = rootPage;
    const beforeReplies = replyPages;
    const rootId = findRootId(item.id);
    const busyKey = `delete:${item.id}`;
    setBusy(busyKey);
    setNotice(null);
    setCanonicalReloadTarget(null);
    updateLocal(item.id, (current) => ({
      ...current,
      content: null,
      deleted: true,
      canEdit: false,
      canDelete: false,
      canReply: false,
    }));
    try {
      try {
        await deleteComment(item.id);
      } catch (error) {
        if (isCurrent(generation)) {
          if (isReadAccessError(error)) {
            clearReadState(error);
          } else {
            setRootPage(beforeRoot);
            setReplyPages(beforeReplies);
            setNotice(displayError(error, '댓글을 삭제하지 못했습니다.'));
          }
        }
        return;
      }
      if (!isCurrent(generation)) return;
      try {
        const canonicalRootId = rootId != null && rootId !== item.id ? rootId : undefined;
        await refreshCanonical(canonicalRootId);
      } catch (error) {
        if (isCurrent(generation)) {
          const canonicalRootId = rootId != null && rootId !== item.id ? rootId : undefined;
          handleCanonicalReloadFailure(error, canonicalRootId == null ? {} : { rootId: canonicalRootId });
        }
      }
    } finally {
      if (isCurrent(generation)) setBusy(null);
    }
  };

  const canWrite = Boolean(viewer && interaction?.canComment);
  const count = interaction?.commentCount;

  const renderActions = (item: CommentItem) => {
    if (item.deleted || item.content === null) return null;
    return (
      <>
        {item.canReply && (
          <ActionButton
            label={`댓글 ${item.id} 답글`}
            onClick={() => setOpenReplyId((current) => current === item.id ? null : item.id)}
            disabled={busy !== null}
          >
            답글
          </ActionButton>
        )}
        {item.canEdit && (
          <ActionButton label={`댓글 ${item.id} 수정`} onClick={() => startEdit(item)} disabled={busy !== null}>
            수정
          </ActionButton>
        )}
        {item.canDelete && (
          <ActionButton label={`댓글 ${item.id} 삭제`} onClick={() => void remove(item)} disabled={busy !== null}>
            삭제
          </ActionButton>
        )}
      </>
    );
  };

  const renderBody = (item: CommentItem) => {
    if (editingId !== item.id) return undefined;
    return (
      <div className="space-y-2">
        <textarea
          value={editingDraft}
          maxLength={2000}
          aria-label={`댓글 ${item.id} 수정 내용`}
          onChange={(event) => setEditingDraft(event.target.value)}
          className="min-h-20 w-full border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0"
        />
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="accentSm"
            onClick={() => void saveEdit(item)}
            disabled={busy !== null}
          >
            저장
          </Button>
          <Button size="sm" variant="inert" onClick={() => setEditingId(null)} disabled={busy !== null}>
            취소
          </Button>
        </div>
      </div>
    );
  };

  const renderReplyForm = (root: CommentItem) => {
    if (openReplyId !== root.id || !canWrite || !root.canReply) return null;
    return (
      <form onSubmit={(event) => void submitComment(event, root.id)} className="ml-8 flex gap-2 border-b border-line px-5 py-3">
        <textarea
          value={replyDrafts[root.id] ?? ''}
          maxLength={2000}
          aria-label={`댓글 ${root.id} 답글 내용`}
          onChange={(event) => setReplyDrafts((current) => ({ ...current, [root.id]: event.target.value }))}
          placeholder="답글을 남겨주세요…"
          className="min-h-12 flex-1 border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0"
        />
        <Button size="sm" variant="accentSm" type="submit" disabled={busy !== null}>
          전송
        </Button>
      </form>
    );
  };

  const renderReplies = (root: CommentItem) => {
    const replies = replyPages[root.id] ?? root.replies;
    if (!replies) return null;
    const error = replyErrors[root.id];
    const loading = replyLoading[root.id] === true;
    return (
      <>
        {loading && <p role="status" className="ml-8 border-b border-line px-5 py-3 text-xs text-text-muted">답글을 불러오는 중...</p>}
        {error && !loading && (
          <div className="ml-8 flex items-center justify-between border-b border-line px-5 py-3 text-xs text-danger" role="alert">
            <span>{error}</span>
            <ActionButton onClick={() => void loadReplyPage(root.id, replies.page)}>다시 시도</ActionButton>
          </div>
        )}
        {!loading && !error && replies.items.map((reply) => (
          <CommentRow key={reply.id} item={reply} depth={1} body={renderBody(reply)} actions={renderActions(reply)} />
        ))}
        {!loading && !error && (
          <CommentPagination
            label={`댓글 ${root.id} 답글`}
            page={replies}
            onChange={(pageNumber) => void loadReplyPage(root.id, pageNumber)}
          />
        )}
      </>
    );
  };

  return (
    <section className="mt-[26px] border-[3px] border-ink bg-surface shadow-panel">
      <header className="border-b-[3px] border-ink bg-surface-raise px-5 py-3">
        <h2 className="text-[13px] font-bold tracking-[2px]">■ 댓글{count == null ? '' : ` ${count}`}</h2>
      </header>

      {rootLoading && <p role="status" className="px-5 py-6 text-center text-xs text-text-muted">댓글을 불러오는 중...</p>}
      {!rootLoading && rootError && (
        <div className="flex items-center justify-between border-b border-danger bg-danger-bg px-5 py-4 text-xs text-danger" role="alert">
          <span>{rootError}</span>
          <ActionButton onClick={() => void loadRootPage(rootPageNumber)}>다시 시도</ActionButton>
        </div>
      )}
      {!rootLoading && !rootError && rootPage?.items.length === 0 && (
        <p className="px-5 py-8 text-center text-xs text-text-muted">아직 댓글이 없습니다.</p>
      )}
      {!rootLoading && !rootError && rootPage && rootPage.items.length > 0 && (
        <div>
          {rootPage.items.map((item) => (
            <CommentRow key={item.id} item={item} body={renderBody(item)} actions={renderActions(item)}>
              {renderReplies(item)}
              {renderReplyForm(item)}
            </CommentRow>
          ))}
          <CommentPagination label="댓글" page={rootPage} onChange={setRootPageNumber} />
        </div>
      )}

      <div className="flex gap-2 border-t border-line px-5 py-4">
        {viewer == null ? (
          <p className="flex-1 self-center text-xs text-text-muted">
            <Link to="/signin" className="font-bold text-accent underline">로그인 후 댓글을 남겨주세요.</Link>
          </p>
        ) : interactionLoading ? (
          <p className="flex-1 self-center text-xs text-text-muted">댓글 작성 권한을 확인하는 중...</p>
        ) : !canWrite ? (
          <p className="flex-1 self-center text-xs text-text-muted">현재 상태에서는 댓글을 남길 수 없습니다.</p>
        ) : (
          <form onSubmit={(event) => void submitComment(event)} className="flex flex-1 gap-2">
            <textarea
              value={rootDraft}
              maxLength={2000}
              aria-label="댓글 입력"
              onChange={(event) => setRootDraft(event.target.value)}
              placeholder="따뜻한 신호를 남겨주세요…"
              className="min-h-10 flex-1 border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0"
            />
            <Button size="sm" variant="primary" type="submit" disabled={busy !== null}>
              전송
            </Button>
          </form>
        )}
      </div>
      {notice && (
        <div role="alert" className="flex items-center justify-between gap-3 border-t-2 border-danger bg-danger-bg px-5 py-3 text-xs text-danger">
          <span>{notice}</span>
          {canonicalReloadTarget && (
            <ActionButton onClick={() => void retryCanonical()} disabled={busy !== null}>
              다시 시도
            </ActionButton>
          )}
        </div>
      )}
    </section>
  );
}
