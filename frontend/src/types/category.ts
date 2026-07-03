export type CategoryType = 'DEFAULT' | 'GENERAL' | 'LOCKED'

export interface CategoryTreeNode {
  categoryId: number
  blogId: number
  parentId: number | null
  name: string
  type: CategoryType
  displayOrder: number
  postCount: number
  draftPostCount: number
  children: CategoryTreeNode[]
  createdAt?: string
  updatedAt?: string
}

export interface CategoryResponse {
  categoryId: number
  blogId: number
  parentId: number | null
  name: string
  type: CategoryType
  displayOrder: number
  postCount: number
  draftPostCount: number
  createdAt?: string
  updatedAt?: string
}

export interface CreateCategoryRequest {
  parentId: number | null
  name: string
  type: CategoryType
  displayOrder: number
}

export interface UpdateCategoryRequest {
  parentId: number | null
  name: string
  type: CategoryType
  displayOrder: number
}

export interface ReorderCategoriesRequest {
  parentId: number | null
  orderedCategoryIds: number[]
}

export interface DeleteCategoryResponse {
  deletedCategoryIds: number[]
  reassignedToCategoryId: number
  reassignedPostCount: number
}
