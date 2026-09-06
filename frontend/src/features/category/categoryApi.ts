import { apiClient } from '../../lib/apiClient';

export type CategoryType = 'DEFAULT' | 'GENERAL' | 'LOCKED';
export type EditableCategoryType = Exclude<CategoryType, 'DEFAULT'>;

export interface Category {
  id: number;
  parentId: number | null;
  name: string;
  type: CategoryType;
  displayOrder: number;
  postCount: number;
  children: Category[];
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface CreateCategoryRequest {
  name: string;
  parentId: number | null;
  type: EditableCategoryType;
  displayOrder: number;
}

export interface UpdateCategoryRequest {
  name: string;
  type: CategoryType;
  displayOrder: number;
}

const categoryPath = (blogId: number) => `/api/v1/blogs/${blogId}/categories`;

export async function getCategories(
  blogId: number,
  options: { page?: number; size?: number; includeDrafts?: boolean } = {},
): Promise<PageResponse<Category>> {
  const page = options.page ?? 0;
  // API 기본값은 20이며, 전체 루트 로더만 최대 허용값 100을 명시한다.
  const size = options.size ?? 20;
  const includeDrafts = options.includeDrafts ?? false;
  const response = await apiClient.get<PageResponse<Category>>(
    `${categoryPath(blogId)}?page=${page}&size=${size}&includeDrafts=${includeDrafts}`,
  );
  if (!response) {
    throw new Error('카테고리 조회 응답이 비어 있습니다.');
  }
  return response;
}

/** 루트 page를 모두 읽어야 reorder/create/delete를 안전하게 허용할 수 있다. */
export async function getAllCategories(
  blogId: number,
  includeDrafts = false,
): Promise<Category[]> {
  const first = await getCategories(blogId, { page: 0, size: 100, includeDrafts });
  const pages = [first.items];
  for (let page = 1; page < first.totalPages; page += 1) {
    const next = await getCategories(blogId, { page, size: 100, includeDrafts });
    pages.push(next.items);
  }
  return pages.flat();
}

export async function createCategory(
  blogId: number,
  request: CreateCategoryRequest,
): Promise<Category> {
  const response = await apiClient.post<Category>(categoryPath(blogId), request);
  if (!response) {
    throw new Error('카테고리 생성 응답이 비어 있습니다.');
  }
  return response;
}

export async function updateCategory(
  blogId: number,
  categoryId: number,
  request: UpdateCategoryRequest,
): Promise<Category> {
  const response = await apiClient.put<Category>(
    `${categoryPath(blogId)}/${categoryId}`,
    request,
  );
  if (!response) {
    throw new Error('카테고리 수정 응답이 비어 있습니다.');
  }
  return response;
}

export async function deleteCategory(blogId: number, categoryId: number): Promise<null> {
  return apiClient.delete<null>(`${categoryPath(blogId)}/${categoryId}`);
}

export async function reorderCategories(blogId: number, categoryIds: number[]): Promise<null> {
  return apiClient.put<null>(`${categoryPath(blogId)}/order`, categoryIds);
}

export function flattenCategories(categories: Category[]): Category[] {
  return categories.flatMap((category) => [category, ...category.children]);
}
