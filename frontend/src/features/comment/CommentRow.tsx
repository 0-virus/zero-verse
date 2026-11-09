import type { ReactNode } from 'react';
import { Avatar } from '../../components/ui/Avatar';
import type { CommentItem } from './types';

export interface CommentRowProps {
  item: CommentItem;
  depth?: 0 | 1;
  body?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(value),
  );
}

function renderPlainText(value: string): ReactNode {
  return value.split('\n').map((line, index, lines) => (
    <span key={`${index}-${line}`}>
      {line}
      {index < lines.length - 1 && <br />}
    </span>
  ));
}

function avatar(author: CommentItem['author']) {
  const label = author ? `${author.nickname}의 아바타` : '탈퇴한 사용자의 아바타';
  if (author?.profileImageUrl) {
    return (
      <img
        src={author.profileImageUrl}
        alt={label}
        className="h-[34px] w-[34px] shrink-0 border-2 border-ink object-cover"
      />
    );
  }
  return <Avatar emoji="🪐" size={34} label={label} />;
}

export function CommentRow({ item, depth = 0, body, actions, children }: CommentRowProps) {
  const deleted = item.deleted || item.content === null;
  const authorName = item.author?.nickname ?? '탈퇴한 사용자';

  return (
    <div
      data-comment-id={item.id}
      data-comment-depth={depth}
      className={depth === 1 ? 'ml-8 border-l-2 border-line' : undefined}
    >
      <div className="flex gap-3 border-b border-line px-5 py-4">
        {avatar(item.author)}
        <div className="min-w-0 flex-1">
          <div className="mb-1 text-[12.5px]">
            <strong>{authorName}</strong>
            <span className="text-text-muted"> · {formatDate(item.createdAt)}</span>
          </div>
          <div className="text-[13.5px] leading-[1.6] text-text-prose">
            {body ?? (deleted ? '삭제된 댓글' : renderPlainText(item.content ?? ''))}
          </div>
          {actions && <div className="mt-2 flex items-center gap-2">{actions}</div>}
        </div>
      </div>
      {children}
    </div>
  );
}
