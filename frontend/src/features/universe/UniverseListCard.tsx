import { useEffect, useState, type ReactNode, type FormEvent } from 'react';
import { Avatar } from '../../components/ui/Avatar';
import { Button } from '../../components/ui/Button';
import { CountBadge } from '../../components/ui/Badge';
import { ListRow } from '../../components/ui/ListRow';
import { Pagination } from '../../components/ui/Pagination';
import type {
  UniverseListKind,
  UniverseRelationItem,
  UniverseRequestItem,
  UniverseUserSummary,
} from './types';

type UniverseItem = UniverseRequestItem | UniverseRelationItem;

export interface UniverseListCardProps {
  title: string;
  kind: UniverseListKind;
  items: UniverseItem[];
  page: number;
  totalPages: number;
  hasPrevious: boolean;
  hasNext: boolean;
  search: string;
  onSearch: (value: string) => void;
  onPageChange: (page: number) => void;
  loading?: boolean;
  error?: string | null;
  count?: number;
  onAction?: (item: UniverseItem) => void;
  actionLabel?: string;
  actions?: (item: UniverseItem) => ReactNode;
  emptyMessage?: string;
}

function userOf(item: UniverseItem): UniverseUserSummary {
  return item.user;
}

function avatarFor(user: UniverseUserSummary, size: 34 | 38) {
  if (user.profileImageUrl) {
    return (
      <img
        src={user.profileImageUrl}
        alt={`${user.nickname}의 프로필 이미지`}
        className={size === 38
          ? 'h-[38px] w-[38px] shrink-0 border-2 border-ink object-cover'
          : 'h-[34px] w-[34px] shrink-0 border-2 border-ink object-cover'}
      />
    );
  }
  return <Avatar emoji="🪐" size={size} label={`${user.nickname}의 아바타`} />;
}

function relationSubtitle(kind: UniverseListKind, item: UniverseItem): string | undefined {
  const user = userOf(item);
  const blog = user.blogSlug ? `/blog/${user.blogSlug}` : undefined;
  const date = 'requestId' in item ? item.createdAt : item.updatedAt;
  if (!blog || !date) return blog;
  const month = date.slice(0, 7).replace('-', '.');
  return kind === 'friends' ? `${blog} · ${month}부터 연결` : `${blog} · ${month}`;
}

export function UniverseListCard({
  title,
  kind,
  items,
  page,
  totalPages,
  hasPrevious,
  hasNext,
  search,
  onSearch,
  onPageChange,
  loading = false,
  error = null,
  count,
  onAction,
  actionLabel,
  actions,
  emptyMessage = '아직 항목이 없습니다.',
}: UniverseListCardProps) {
  const [draftSearch, setDraftSearch] = useState(search);

  useEffect(() => {
    setDraftSearch(search);
  }, [search]);

  const submitSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    onSearch(draftSearch.trim());
  };

  const avatarSize: 34 | 38 = kind === 'received' || kind === 'sent' ? 38 : 34;
  const headingTitle = kind === 'friends' && count !== undefined ? `${title} ${count}` : title;

  return (
    <section
      data-universe-card={kind}
      className="overflow-hidden border-[3px] border-ink bg-surface shadow-card"
    >
      <header
        className={`flex items-center justify-between border-b-[3px] border-ink px-5 py-3 ${
          kind === 'received' ? 'bg-surface-raise' : ''
        }`}
      >
        <div className="flex min-w-0 items-center gap-2">
          <h2 className="text-[13px] font-bold tracking-[2px]">■ {headingTitle}</h2>
          {kind === 'received' && count !== undefined && <CountBadge count={count} />}
        </div>
        <form onSubmit={submitSearch} className="flex shrink-0 items-center gap-2">
          <label htmlFor={`universe-search-${kind}`} className="sr-only">
            {title} 검색
          </label>
          <input
            id={`universe-search-${kind}`}
            type="search"
            role="searchbox"
            aria-label={`${title} 검색`}
            value={draftSearch}
            onChange={(event) => setDraftSearch(event.target.value)}
            placeholder="닉네임 검색"
            className="w-[180px] border-2 border-ink bg-surface-warm px-3 py-2 text-[13px] font-normal"
          />
          <button type="submit" className="border-2 border-ink bg-surface px-3 py-1.5 text-xs font-bold">
            검색
          </button>
        </form>
      </header>
      {loading && (
        <p className="px-5 py-6 text-center text-xs text-text-muted" role="status">
          불러오는 중...
        </p>
      )}
      {!loading && error && (
        <p className="border-b-2 border-danger bg-danger-bg px-5 py-4 text-xs text-danger" role="alert">
          {error}
        </p>
      )}
      {!loading && !error && items.length === 0 && (
        <p className="px-5 py-8 text-center text-xs text-text-muted">{emptyMessage}</p>
      )}
      {!loading && !error && items.length > 0 && (
        <div>
          {items.map((item) => {
            const user = userOf(item);
            return (
              <ListRow
                key={`${kind}-${'requestId' in item ? item.requestId : user.id}`}
                avatar={avatarFor(user, avatarSize)}
                title={user.nickname}
                subtitle={relationSubtitle(kind, item)}
                actions={
                  actions
                    ? actions(item)
                    : onAction && actionLabel
                      ? (
                        <Button
                          size="sm"
                          variant="neutral"
                          aria-label={`${actionLabel}: ${user.nickname}`}
                          onClick={() => onAction(item)}
                        >
                          {actionLabel}
                        </Button>
                      )
                      : undefined
                }
              />
            );
          })}
        </div>
      )}
      {!loading && !error && (hasPrevious || hasNext) && (
        <div className="border-t-2 border-line px-5 py-4">
          <Pagination page={page} totalPages={totalPages} onChange={onPageChange} />
        </div>
      )}
    </section>
  );
}
