import { Badge, type BadgeKind } from './Badge';
import { TagChip } from './TagChip';
import { ManagedImage } from '../../features/upload/ManagedImage';

/** 디자인 정본 §7.6. 전달받은 값만 렌더한다 — 샘플 데이터를 내부에서 만들지 않는다. */
export interface PostCardProps {
  title: string;
  /** `블로그/카테고리 · 날짜` */
  meta: string;
  excerpt?: string;
  visibility?: BadgeKind;
  tags?: string[];
  /** M6 댓글/좋아요 API가 연결되기 전에는 숫자 자체를 렌더하지 않는다. */
  commentCount?: number;
  likeCount?: number;
  thumbnailUrl?: string | null;
  onClick?: () => void;
}

export function PostCard({
  title,
  meta,
  excerpt,
  visibility,
  tags = [],
  commentCount,
  likeCount,
  thumbnailUrl,
  onClick,
}: PostCardProps) {
  return (
    <article
      onClick={onClick}
      className="cursor-pointer border-[3px] border-ink bg-surface px-5 py-[18px] shadow-card hover:bg-surface-hover hover:-translate-x-0.5 hover:-translate-y-0.5"
    >
      <div className="mb-2 flex items-center gap-2">
        {visibility && <Badge kind={visibility} />}
        <span className="text-xs text-text-muted">{meta}</span>
      </div>
      <div className={thumbnailUrl ? 'grid grid-cols-[1fr_132px] gap-4' : undefined}>
        <div>
          <h2 className="text-[18px] font-bold">{title}</h2>
          {excerpt && <p className="mt-2 text-[13.5px] leading-[1.65] text-text-body">{excerpt}</p>}
        </div>
        {thumbnailUrl && (
          <ManagedImage
            src={thumbnailUrl}
            alt={`${title} 대표 이미지`}
            className="h-[88px] w-[132px] border-2 border-ink object-cover"
          />
        )}
      </div>
      <div className="mt-3 flex items-center justify-between">
        <div className="flex gap-1.5">
          {tags.map((tag) => (
            <TagChip key={tag} name={tag} />
          ))}
        </div>
        {commentCount !== undefined && likeCount !== undefined && (
          <span className="text-xs text-text-muted">댓글 {commentCount} · 좋아요 {likeCount}</span>
        )}
      </div>
    </article>
  );
}
