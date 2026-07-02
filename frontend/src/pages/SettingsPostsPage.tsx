import { useEffect, useState } from 'react'
import AppShell from '../components/layout/AppShell'
import { useBlogSettings } from '../hooks/useBlogSettings'
import { useCategories } from '../hooks/useCategories'
import CategoryTree from '../components/category/CategoryTree'
import CategoryDetailPanel from '../components/category/CategoryDetailPanel'
import CategoryOrderControls from '../components/category/CategoryOrderControls'
import Button from '../components/ui/Button'
import type { CategoryTreeNode, CategoryType, CreateCategoryRequest } from '../types/category'

export default function SettingsPostsPage() {
  const { blog, getBlogMe, isLoading: blogLoading } = useBlogSettings()
  const {
    categories,
    isLoading: categoriesLoading,
    error,
    getCategories,
    createCategory,
    updateCategory,
    deleteCategory,
    reorderCategories,
  } = useCategories()

  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null)
  const [showNewCategoryForm, setShowNewCategoryForm] = useState(false)
  const [newCategoryName, setNewCategoryName] = useState('')
  const [newCategoryType, setNewCategoryType] = useState<CategoryType>('GENERAL')
  const [newCategoryParentId, setNewCategoryParentId] = useState<number | null>(null)

  // Load blog and categories on mount
  useEffect(() => {
    const loadBlog = async () => {
      const blogResult = await getBlogMe()
      if (blogResult) {
        await getCategories(blogResult.blogId)
      }
    }
    loadBlog()
  }, [getBlogMe, getCategories])

  const findCategory = (cats: CategoryTreeNode[], id: number): CategoryTreeNode | null => {
    for (const cat of cats) {
      if (cat.categoryId === id) return cat
      if (cat.children) {
        const found = findCategory(cat.children, id)
        if (found) return found
      }
    }
    return null
  }

  const selectedCategory = selectedCategoryId ? findCategory(categories, selectedCategoryId) : null

  const handleCreateCategory = async () => {
    if (!blog || !newCategoryName.trim()) return

    // Find the next display order for the parent
    const parentChildren = selectedCategory
      ? (selectedCategory.children || [])
      : categories.filter((c) => c.parentId === null)

    const maxOrder = parentChildren.length > 0
      ? Math.max(...parentChildren.map((c) => c.displayOrder))
      : -1
    const nextOrder = maxOrder + 1

    const request: CreateCategoryRequest = {
      parentId: newCategoryParentId,
      name: newCategoryName.trim(),
      type: newCategoryType,
      displayOrder: nextOrder,
    }

    const result = await createCategory(blog.blogId, request)
    if (result) {
      // Reload categories
      await getCategories(blog.blogId)
      setNewCategoryName('')
      setNewCategoryType('GENERAL')
      setNewCategoryParentId(null)
      setShowNewCategoryForm(false)
    }
  }

  const handleUpdateCategory = async (data: {
    parentId: number | null
    name: string
    type: CategoryType
    displayOrder: number
  }) => {
    if (!blog || !selectedCategory) return

    const result = await updateCategory(blog.blogId, selectedCategory.categoryId, data)
    if (result) {
      await getCategories(blog.blogId)
    }
  }

  const handleDeleteCategory = async () => {
    if (!blog || !selectedCategory) return

    const result = await deleteCategory(blog.blogId, selectedCategory.categoryId)
    if (result) {
      await getCategories(blog.blogId)
      setSelectedCategoryId(null)
    }
  }

  const handleReorderCategories = async (parentId: number | null, orderedIds: number[]) => {
    if (!blog) return

    const result = await reorderCategories(blog.blogId, {
      parentId,
      orderedCategoryIds: orderedIds,
    })
    if (result) {
      await getCategories(blog.blogId)
    }
  }

  const isLoading = blogLoading || categoriesLoading

  if (isLoading) {
    return (
      <AppShell showSidebar={true}>
        <div className="p-6 flex items-center justify-center min-h-96">
          <p className="text-text-muted">로딩 중...</p>
        </div>
      </AppShell>
    )
  }

  return (
    <AppShell showSidebar={true}>
      <div className="p-6">
        <h1 className="font-display text-3xl text-text-primary mb-6">Category Management</h1>

        <div className="grid grid-cols-3 gap-6">
          {/* Categories Tree - Left */}
          <div className="bg-bg-panel border-2 border-border-purple-dark p-4 shadow-card">
            <h3 className="font-display text-text-primary mb-4">Categories</h3>

            {error && (
              <div className="mb-4 p-3 bg-[#3b0718] border-2 border-border-danger rounded text-text-primary text-sm">
                <p className="font-semibold">{error.code}</p>
                <p>{error.message}</p>
              </div>
            )}

            <div className="mb-4">
              <CategoryTree
                categories={categories}
                selectedCategoryId={selectedCategoryId ?? undefined}
                onSelectCategory={(cat) => setSelectedCategoryId(cat.categoryId)}
              />
            </div>

            <Button
              onClick={() => setShowNewCategoryForm(!showNewCategoryForm)}
              variant="primary"
              className="w-full"
            >
              {showNewCategoryForm ? 'Cancel' : 'Add Category'}
            </Button>

            {showNewCategoryForm && (
              <div className="mt-4 space-y-3 p-3 bg-[#0f1029] border-2 border-border-cyan-dark rounded">
                <input
                  type="text"
                  placeholder="Category name"
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  className="w-full px-2 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary text-sm"
                />
                <select
                  value={newCategoryType}
                  onChange={(e) => setNewCategoryType(e.target.value as CategoryType)}
                  className="w-full px-2 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary text-sm"
                >
                  <option value="GENERAL">General</option>
                  <option value="LOCKED">Locked</option>
                </select>
                <select
                  value={newCategoryParentId ?? ''}
                  onChange={(e) => setNewCategoryParentId(e.target.value ? Number(e.target.value) : null)}
                  className="w-full px-2 py-2 bg-[#030712] border-2 border-border-purple-dark text-text-primary text-sm"
                >
                  <option value="">Root</option>
                  {categories
                    .filter((c) => c.parentId === null)
                    .map((cat) => (
                      <option key={cat.categoryId} value={cat.categoryId}>
                        {cat.name}
                      </option>
                    ))}
                </select>
                <Button onClick={handleCreateCategory} variant="primary" className="w-full">
                  Create
                </Button>
              </div>
            )}
          </div>

          {/* Category Details & Order - Right */}
          <div className="col-span-2 space-y-6">
            <div className="bg-bg-panel border-2 border-border-cyan-dark p-4 shadow-card">
              <h3 className="font-display text-text-primary mb-4">Category Details</h3>
              <CategoryDetailPanel
                category={selectedCategory || null}
                allCategories={categories}
                onSave={handleUpdateCategory}
                onDelete={handleDeleteCategory}
                error={error}
              />
            </div>

            {selectedCategory && (
              <div className="bg-bg-panel border-2 border-border-admin p-4 shadow-card">
                <h3 className="font-display text-text-primary mb-4">Order</h3>
                <CategoryOrderControls
                  categories={categories}
                  selectedCategoryId={selectedCategoryId ?? undefined}
                  onReorder={handleReorderCategories}
                  error={error}
                />
              </div>
            )}
          </div>
        </div>
      </div>
    </AppShell>
  )
}
