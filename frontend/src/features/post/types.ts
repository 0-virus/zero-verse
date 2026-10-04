export type Visibility = 'PUBLIC' | 'UNIVERSE' | 'PRIVATE';

export interface PostImageInput {
  imageUrl: string;
  altText: string | null;
  displayOrder: number;
}

export interface PostAuthor {
  id: number;
  nickname: string;
  profileImageUrl: string | null;
}

export interface PostCategory {
  id: number;
  name: string;
}

export interface AdjacentPost {
  id: number;
  title: string;
  blogSlug: string;
}

export interface PostDetail {
  id: number;
  blogId: number;
  blogSlug: string;
  blogTitle: string;
  author: PostAuthor;
  category: PostCategory;
  title: string;
  contentJson: Record<string, unknown>;
  contentHtml: string;
  thumbnailUrl: string | null;
  visibility: Visibility;
  viewCount: number;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
  tags: string[];
  images: PostImageInput[];
  previous: AdjacentPost | null;
  next: AdjacentPost | null;
}

export type PostSummary = Omit<
  PostDetail,
  'contentJson' | 'contentHtml' | 'images' | 'previous' | 'next'
> & {
  excerpt: string;
};

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PostSnapshot {
  title: string;
  contentJson: Record<string, unknown>;
  contentHtml: string;
  categoryId: number | null;
  visibility: Visibility;
  publish: boolean;
  thumbnailUrl: string | null;
  tagNames: string[];
  images: PostImageInput[];
}

export interface CreatePostRequest extends PostSnapshot {
  blogId: number;
}

export type UpdatePostRequest = PostSnapshot & { blogId?: never };

export interface PostListParams {
  page?: number;
  size?: number;
  sort?: 'latest' | 'popular';
  categoryId?: number;
  tag?: string;
  visibility?: Visibility;
  publish?: boolean;
}

export interface UpdatePostImagesRequest {
  images: PostImageInput[];
}
