import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type DragEvent,
  type FormEvent,
  type KeyboardEvent,
} from 'react';
import { Button } from '../components/ui/Button';
import { Panel } from '../components/ui/Panel';
import {
  createCategory,
  deleteCategory,
  getAllCategories,
  reorderCategories,
  updateCategory,
  type Category,
  type CategoryType,
  type EditableCategoryType,
} from '../features/category/categoryApi';
import { ApiRequestError } from '../lib/apiClient';
import { useAuth } from '../lib/authContext';

function sortCategories(categories: Category[]): Category[] {
  return [...categories]
    .sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id)
    .map((category) => ({
      ...category,
      children: [...category.children].sort(
        (a, b) => a.displayOrder - b.displayOrder || a.id - b.id,
      ),
    }));
}

function errorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiRequestError)) return fallback;
  switch (error.code) {
    case 'CAT_001':
      return '카테고리를 찾을 수 없습니다. 최신 목록을 확인해 주세요.';
    case 'CAT_002':
      return '카테고리 깊이는 루트와 하위 1단계까지만 허용됩니다.';
    case 'CAT_003':
      return '기본 카테고리는 삭제할 수 없습니다.';
    case 'CAT_004':
      return '이 카테고리를 관리할 권한이 없습니다.';
    case 'CAT_005':
      return '같은 위치에 같은 이름의 카테고리가 있습니다.';
    case 'CAT_006':
      return '잠금 카테고리는 이름·타입·순서를 되돌릴 수 없습니다.';
    case 'CAT_007':
      return '순서가 바뀌었습니다. 최신 목록을 확인한 뒤 다시 시도해 주세요.';
    default:
      return error.message || fallback;
  }
}

function sameLockedSlots(before: Category[], after: Category[]): boolean {
  return before.every(
    (category, index) => category.type !== 'LOCKED' || after[index]?.id === category.id,
  );
}

function replaceSiblingOrder(
  categories: Category[],
  parentId: number | null,
  orderedIds: number[],
): Category[] {
  if (parentId === null) {
    const byId = new Map(categories.map((category) => [category.id, category]));
    return orderedIds.map((id) => byId.get(id)).filter((category): category is Category => !!category);
  }

  return categories.map((category) => {
    if (category.id !== parentId) return category;
    const byId = new Map(category.children.map((child) => [child.id, child]));
    return {
      ...category,
      children: orderedIds
        .map((id) => byId.get(id))
        .filter((child): child is Category => !!child),
    };
  });
}

export function SettingsPostsPage() {
  const { user, isLoading: authLoading } = useAuth();
  const blogId = user?.defaultBlog.id;
  const currentBlogId = blogId ?? null;
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [loadedBlogId, setLoadedBlogId] = useState<number | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isMutating, setIsMutating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingName, setEditingName] = useState('');
  const [newName, setNewName] = useState('');
  const [newParentId, setNewParentId] = useState('');
  const [newType, setNewType] = useState<EditableCategoryType>('GENERAL');
  const [draggedId, setDraggedId] = useState<number | null>(null);
  const loadGeneration = useRef(0);
  const operationGeneration = useRef(0);
  const currentBlogIdRef = useRef<number | null>(currentBlogId);

  // blog 전환/null 전환은 진행 중인 API의 결과를 모두 폐기하는 경계다.
  // 렌더 시점에 ref를 먼저 갱신해 늦은 promise continuation도 새 화면을 덮지 못하게 한다.
  if (currentBlogIdRef.current !== currentBlogId) {
    currentBlogIdRef.current = currentBlogId;
    operationGeneration.current += 1;
    loadGeneration.current += 1;
  }

  const isCurrentOperation = (expectedBlogId: number | null, operation: number) =>
    currentBlogIdRef.current === expectedBlogId && operationGeneration.current === operation;

  // blog가 바뀌는 렌더와 effect 사이에도 이전 목록을 새 blog의 쓰기 대상으로
  // 취급하지 않는다. 실제로 성공한 GET의 blog ID가 현재 ID와 같아야만 조작을 허용한다.
  const isReadyForBlog = currentBlogId !== null && loadedBlogId === currentBlogId && isLoaded;

  const loadCategories = useCallback(async (): Promise<boolean> => {
    const requestBlogId = blogId ?? null;
    const operation = operationGeneration.current;
    if (!isCurrentOperation(requestBlogId, operation)) return false;

    const generation = ++loadGeneration.current;
    if (requestBlogId == null) {
      setCategories([]);
      setIsLoading(false);
      setIsLoaded(false);
      setLoadedBlogId(null);
      setError('블로그 정보를 확인한 뒤 카테고리를 불러올 수 있습니다.');
      setNotice(null);
      return false;
    }

    setIsLoading(true);
    setIsLoaded(false);
    setLoadedBlogId(null);
    setError(null);
    setNotice(null);
    try {
      const loaded = await getAllCategories(requestBlogId, true);
      if (!isCurrentOperation(requestBlogId, operation) || loadGeneration.current !== generation) {
        return false;
      }
      setCategories(sortCategories(loaded));
      setIsLoaded(true);
      setLoadedBlogId(requestBlogId);
      return true;
    } catch (loadError) {
      if (!isCurrentOperation(requestBlogId, operation) || loadGeneration.current !== generation) {
        return false;
      }
      setCategories([]);
      setIsLoaded(false);
      setLoadedBlogId(null);
      setError(errorMessage(loadError, '카테고리를 불러오지 못했습니다.'));
      return false;
    } finally {
      if (isCurrentOperation(requestBlogId, operation) && loadGeneration.current === generation) {
        setIsLoading(false);
      }
    }
  }, [blogId]);

  useEffect(() => {
    if (authLoading) return;
    setCategories([]);
    setIsLoaded(false);
    setLoadedBlogId(null);
    setIsMutating(false);
    setEditingId(null);
    setDraggedId(null);
    setError(null);
    setNotice(null);
    void loadCategories();
  }, [authLoading, loadCategories]);

  const reloadAfterFailure = () => loadCategories();

  const showMutationError = async (
    mutationError: unknown,
    fallback: string,
    expectedBlogId: number,
    operation: number,
  ) => {
    const reloaded = await reloadAfterFailure();
    if (!isCurrentOperation(expectedBlogId, operation)) return;
    setError(
      reloaded
        ? errorMessage(mutationError, fallback)
        : '변경 결과를 확인하지 못했습니다. 카테고리를 다시 불러와 주세요.',
    );
  };

  const siblings = (parentId: number | null): Category[] => {
    if (parentId === null) return categories;
    return categories.find((category) => category.id === parentId)?.children ?? [];
  };

  const persistOrder = async (parentId: number | null, next: Category[]) => {
    const requestBlogId = blogId;
    const operation = operationGeneration.current;
    if (
      !isReadyForBlog ||
      isMutating ||
      requestBlogId == null ||
      !isCurrentOperation(requestBlogId, operation)
    ) {
      return;
    }
    const before = siblings(parentId);
    const ids = next.map((category) => category.id);
    if (ids.length !== before.length || new Set(ids).size !== before.length) {
      setError('같은 위치의 카테고리를 모두 포함해야 순서를 저장할 수 있습니다.');
      return;
    }
    if (!sameLockedSlots(before, next)) {
      setError('잠금 카테고리의 숫자 순서는 변경할 수 없습니다.');
      return;
    }

    setError(null);
    setNotice(null);
    setIsMutating(true);
    setCategories((current) => replaceSiblingOrder(current, parentId, ids));
    try {
      await reorderCategories(requestBlogId, ids);
      if (!isCurrentOperation(requestBlogId, operation)) return;
      const reloaded = await loadCategories();
      if (!reloaded || !isCurrentOperation(requestBlogId, operation)) return;
      setNotice('카테고리 순서를 저장했습니다.');
    } catch (orderError) {
      if (!isCurrentOperation(requestBlogId, operation)) return;
      await showMutationError(
        orderError,
        '카테고리 순서를 저장하지 못했습니다.',
        requestBlogId,
        operation,
      );
    } finally {
      if (isCurrentOperation(requestBlogId, operation)) setIsMutating(false);
    }
  };

  const moveCategory = async (category: Category, direction: -1 | 1) => {
    if (!isReadyForBlog || isMutating) return;
    const current = siblings(category.parentId);
    const index = current.findIndex((item) => item.id === category.id);
    const target = index + direction;
    if (index < 0 || target < 0 || target >= current.length) return;
    const next = [...current];
    [next[index], next[target]] = [next[target], next[index]];
    await persistOrder(category.parentId, next);
  };

  const handleDrop = async (event: DragEvent<HTMLDivElement>, target: Category) => {
    event.preventDefault();
    if (!isReadyForBlog || isMutating) return;
    const sourceId = draggedId;
    setDraggedId(null);
    if (sourceId == null || sourceId === target.id) return;
    const current = siblings(target.parentId);
    const sourceIndex = current.findIndex((category) => category.id === sourceId);
    const targetIndex = current.findIndex((category) => category.id === target.id);
    if (sourceIndex < 0 || targetIndex < 0) return;
    const next = [...current];
    const [source] = next.splice(sourceIndex, 1);
    next.splice(targetIndex, 0, source);
    await persistOrder(target.parentId, next);
  };

  const beginRename = (category: Category) => {
    if (
      !isReadyForBlog ||
      isMutating ||
      category.type !== 'GENERAL' ||
      !isCurrentOperation(currentBlogId, operationGeneration.current)
    ) {
      return;
    }
    setError(null);
    setNotice(null);
    setEditingId(category.id);
    setEditingName(category.name);
  };

  const saveRename = async (category: Category) => {
    const requestBlogId = blogId;
    const operation = operationGeneration.current;
    if (
      requestBlogId == null ||
      !isReadyForBlog ||
      category.type !== 'GENERAL' ||
      isMutating ||
      !isCurrentOperation(requestBlogId, operation)
    ) {
      return;
    }
    const name = editingName.trim();
    if (!name) {
      setError('카테고리 이름을 입력해 주세요.');
      return;
    }
    setEditingId(null);
    if (name === category.name) return;
    setError(null);
    setNotice(null);
    setIsMutating(true);
    try {
      await updateCategory(requestBlogId, category.id, {
        name,
        type: category.type,
        displayOrder: category.displayOrder,
      });
      if (!isCurrentOperation(requestBlogId, operation)) return;
      const reloaded = await loadCategories();
      if (!reloaded || !isCurrentOperation(requestBlogId, operation)) return;
      setNotice('카테고리 이름을 저장했습니다.');
    } catch (renameError) {
      if (!isCurrentOperation(requestBlogId, operation)) return;
      await showMutationError(
        renameError,
        '카테고리 이름을 저장하지 못했습니다.',
        requestBlogId,
        operation,
      );
    } finally {
      if (isCurrentOperation(requestBlogId, operation)) setIsMutating(false);
    }
  };

  const changeType = async (category: Category, type: CategoryType) => {
    const requestBlogId = blogId;
    const operation = operationGeneration.current;
    if (
      requestBlogId == null ||
      !isReadyForBlog ||
      isMutating ||
      category.type === type ||
      category.type !== 'GENERAL' ||
      !isCurrentOperation(requestBlogId, operation)
    ) {
      return;
    }
    if (type !== 'LOCKED') return;
    const confirmed = window.confirm(
      '잠금 카테고리로 저장하면 이름·타입·순서를 되돌릴 수 없습니다. 저장할까요?',
    );
    if (!confirmed) return;

    setError(null);
    setNotice(null);
    setIsMutating(true);
    try {
      await updateCategory(requestBlogId, category.id, {
        name: category.name,
        type: 'LOCKED',
        displayOrder: category.displayOrder,
      });
      if (!isCurrentOperation(requestBlogId, operation)) return;
      const reloaded = await loadCategories();
      if (!reloaded || !isCurrentOperation(requestBlogId, operation)) return;
      setNotice('카테고리를 잠금 상태로 저장했습니다. 잠금은 되돌릴 수 없습니다.');
    } catch (typeError) {
      if (!isCurrentOperation(requestBlogId, operation)) return;
      await showMutationError(
        typeError,
        '카테고리 타입을 저장하지 못했습니다.',
        requestBlogId,
        operation,
      );
    } finally {
      if (isCurrentOperation(requestBlogId, operation)) setIsMutating(false);
    }
  };

  const handleDelete = async (category: Category) => {
    const requestBlogId = blogId;
    const operation = operationGeneration.current;
    if (
      requestBlogId == null ||
      !isReadyForBlog ||
      isMutating ||
      category.type !== 'GENERAL' ||
      !isCurrentOperation(requestBlogId, operation)
    ) {
      return;
    }
    const confirmed = window.confirm(
      '이 카테고리를 삭제하면 포함된 글은 미분류로 이동합니다. 삭제할까요?',
    );
    if (!confirmed) return;

    setError(null);
    setNotice(null);
    setIsMutating(true);
    try {
      await deleteCategory(requestBlogId, category.id);
      if (!isCurrentOperation(requestBlogId, operation)) return;
      const reloaded = await loadCategories();
      if (!reloaded || !isCurrentOperation(requestBlogId, operation)) return;
      setNotice('카테고리를 삭제했습니다. 글은 미분류로 이동했습니다.');
    } catch (deleteError) {
      if (!isCurrentOperation(requestBlogId, operation)) return;
      await showMutationError(
        deleteError,
        '카테고리를 삭제하지 못했습니다.',
        requestBlogId,
        operation,
      );
    } finally {
      if (isCurrentOperation(requestBlogId, operation)) setIsMutating(false);
    }
  };

  const handleCreate = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const requestBlogId = blogId;
    const operation = operationGeneration.current;
    if (
      requestBlogId == null ||
      !isReadyForBlog ||
      isMutating ||
      !isCurrentOperation(requestBlogId, operation)
    ) {
      return;
    }
    const name = newName.trim();
    if (!name) {
      setError('카테고리 이름을 입력해 주세요.');
      return;
    }
    if (
      newType === 'LOCKED' &&
      !window.confirm('잠금 카테고리는 생성 후 이름·타입·순서를 되돌릴 수 없습니다. 생성할까요?')
    ) {
      return;
    }
    const parentId = newParentId ? Number(newParentId) : null;
    const currentSiblings = siblings(parentId);
    setError(null);
    setNotice(null);
    setIsMutating(true);
    try {
      await createCategory(requestBlogId, {
        name,
        parentId,
        type: newType,
        displayOrder: Math.max(-1, ...currentSiblings.map((category) => category.displayOrder)) + 1,
      });
      if (!isCurrentOperation(requestBlogId, operation) || !isReadyForBlog) return;
      setNewName('');
      const reloaded = await loadCategories();
      if (!reloaded || !isCurrentOperation(requestBlogId, operation)) return;
      setNotice('카테고리를 추가했습니다.');
    } catch (createError) {
      if (!isCurrentOperation(requestBlogId, operation)) return;
      await showMutationError(
        createError,
        '카테고리를 추가하지 못했습니다.',
        requestBlogId,
        operation,
      );
    } finally {
      if (isCurrentOperation(requestBlogId, operation)) setIsMutating(false);
    }
  };

  const handleRenameKeyDown = async (
    event: KeyboardEvent<HTMLInputElement>,
    category: Category,
  ) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      await saveRename(category);
    } else if (event.key === 'Escape') {
      setEditingId(null);
    }
  };

  const renderCategory = (category: Category, depth: number) => {
    const canRename = category.type === 'GENERAL';
    const canReorder = category.type !== 'LOCKED';
    return (
      <div key={category.id} data-category-id={category.id}>
        <div
          draggable={isReadyForBlog && canReorder && !isMutating}
          onDragStart={(event) => {
            if (canReorder && isReadyForBlog && !isMutating) {
              setDraggedId(category.id);
              event.dataTransfer?.setData('text/plain', String(category.id));
              if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
            }
          }}
          onDragEnd={() => setDraggedId(null)}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => void handleDrop(event, category)}
          className={`flex items-center gap-3 border-b border-line px-5 py-[13px] ${
            depth > 0 ? 'bg-surface-soft' : 'bg-surface'
          }`}
          style={{ paddingLeft: depth > 0 ? 42 : 16 }}
        >
          <button
            type="button"
            aria-label={`${category.name} 순서 이동`}
            disabled={!isReadyForBlog || isMutating || !canReorder}
            onKeyDown={(event) => {
              if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
                event.preventDefault();
                void moveCategory(category, event.key === 'ArrowUp' ? -1 : 1);
              }
            }}
            className="w-6 shrink-0 border-2 border-transparent text-[15px] leading-none text-shadow enabled:cursor-grab enabled:hover:border-ink disabled:cursor-not-allowed disabled:opacity-50"
          >
            ⠿
          </button>
          {editingId === category.id ? (
            <input
              aria-label={`${category.name} 이름 편집`}
              autoFocus
              value={editingName}
              onChange={(event) => setEditingName(event.target.value)}
              onKeyDown={(event) => void handleRenameKeyDown(event, category)}
              onBlur={() => void saveRename(category)}
              className="min-w-0 flex-1 border-2 border-ink bg-surface-warm px-2 py-1 text-[13px] outline-0"
            />
          ) : (
            <span className="min-w-0 flex-1 truncate text-sm font-bold text-ink">
              {category.name}
            </span>
          )}
          <span className="shrink-0 text-xs text-text-muted">{category.postCount}개의 글</span>
          <span className="shrink-0 border border-shadow px-1.5 py-0.5 text-[10px] font-bold text-text-muted">
            {category.type}
          </span>
          {editingId !== category.id && (
            <Button
              variant="inert"
              size="sm"
              disabled={!isReadyForBlog || isMutating || !canRename}
              onClick={() => beginRename(category)}
              aria-label={`${category.name} 이름 변경`}
            >
              이름 변경
            </Button>
          )}
          {editingId === category.id && (
            <Button
              variant="neutral"
              size="sm"
              disabled={!isReadyForBlog || isMutating}
              onClick={() => void saveRename(category)}
            >
              저장
            </Button>
          )}
          <Button
            variant={canRename ? 'danger' : 'inert'}
            size="sm"
            disabled={!isReadyForBlog || isMutating || !canRename}
            onClick={() => void handleDelete(category)}
            aria-label={`${category.name} 삭제`}
          >
            삭제
          </Button>
          <select
            aria-label={`${category.name} 타입`}
            value={category.type}
            disabled={!isReadyForBlog || isMutating || !canRename}
            onChange={(event) => void changeType(category, event.target.value as CategoryType)}
            className="border-2 border-ink bg-surface-warm px-1 py-1 text-[10px] font-bold disabled:cursor-not-allowed disabled:opacity-60"
          >
            {category.type === 'DEFAULT' && <option value="DEFAULT">DEFAULT</option>}
            <option value="GENERAL">GENERAL</option>
            <option value="LOCKED">LOCKED</option>
          </select>
        </div>
        {category.children.map((child) => renderCategory(child, depth + 1))}
      </div>
    );
  };

  return (
    <div>
      <h1 className="sr-only">글·카테고리 관리</h1>
      <Panel title="카테고리 관리 — 드래그로 순서 변경" tone="primary">
        {isLoading && (
          <p className="px-5 py-10 text-center text-[13px] text-text-muted">카테고리를 불러오는 중...</p>
        )}
        {!isLoading && error && !isLoaded && (
          <div className="space-y-3 px-5 py-8 text-center">
            <p role="alert" className="text-[13px] text-danger">
              {error}
            </p>
            <Button variant="neutral" size="sm" onClick={() => void loadCategories()}>
              카테고리 다시 불러오기
            </Button>
          </div>
        )}
        {!isLoading && isLoaded && categories.length === 0 && (
          <p className="px-5 py-10 text-center text-[13px] text-text-muted">
            아직 카테고리가 없습니다. 아래에서 카테고리를 추가하세요.
          </p>
        )}
        {!isLoading && isLoaded && categories.map((category) => renderCategory(category, 0))}

        {isLoaded && error && (
          <p role="alert" className="border-t-2 border-danger bg-danger-bg px-5 py-3 text-[12px] text-danger">
            {error}
          </p>
        )}
        {isLoaded && notice && (
          <p className="border-t-2 border-success bg-success-bg px-5 py-3 text-[12px] text-success">
            {notice}
          </p>
        )}

        <form onSubmit={handleCreate} className="flex flex-wrap items-end gap-2.5 px-5 py-4">
          <label className="min-w-[220px] flex-1 text-[12px] font-bold text-text-muted">
            새 카테고리 이름
            <input
              aria-label="새 카테고리 이름"
              value={newName}
              onChange={(event) => {
                if (isReadyForBlog) setNewName(event.target.value);
              }}
              disabled={!isReadyForBlog || isMutating}
              className="mt-1 w-full border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] outline-0 disabled:cursor-not-allowed disabled:opacity-60"
              maxLength={100}
            />
          </label>
          <label className="min-w-[180px] text-[12px] font-bold text-text-muted">
            부모 카테고리
            <select
              aria-label="부모 카테고리"
              value={newParentId}
              onChange={(event) => setNewParentId(event.target.value)}
              disabled={!isReadyForBlog || isMutating}
              className="mt-1 w-full border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <option value="">루트 카테고리</option>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
          </label>
          <label className="min-w-[130px] text-[12px] font-bold text-text-muted">
            타입
            <select
              aria-label="새 카테고리 타입"
              value={newType}
              onChange={(event) => setNewType(event.target.value as EditableCategoryType)}
              disabled={!isReadyForBlog || isMutating}
              className="mt-1 w-full border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] disabled:cursor-not-allowed disabled:opacity-60"
            >
              <option value="GENERAL">GENERAL</option>
              <option value="LOCKED">LOCKED</option>
            </select>
          </label>
          <Button variant="primary" size="md" type="submit" disabled={!isReadyForBlog || isMutating}>
            {isMutating ? '저장 중...' : '＋ 추가'}
          </Button>
        </form>
      </Panel>
      <p className="mt-2 px-1 text-xs text-text-muted">
        카테고리를 삭제하면 글은 '미분류'로 이동합니다. 글이 있는 카테고리는 삭제 전 확인을
        거칩니다.
      </p>
    </div>
  );
}
