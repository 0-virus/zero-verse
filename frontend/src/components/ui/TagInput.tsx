import { useState } from 'react'

interface TagInputProps {
  tags: string[]
  onTagsChange: (tags: string[]) => void
  placeholder?: string
}

export default function TagInput({
  tags,
  onTagsChange,
  placeholder = 'Add tag and press Enter',
}: TagInputProps) {
  const [input, setInput] = useState('')

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' && input.trim()) {
      e.preventDefault()
      if (!tags.includes(input.trim())) {
        onTagsChange([...tags, input.trim()])
      }
      setInput('')
    }
  }

  const removeTag = (tag: string) => {
    onTagsChange(tags.filter((t) => t !== tag))
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-2 p-2 bg-bg-input border-2 border-border-cyan-dark min-h-10">
        {tags.map((tag) => (
          <span
            key={tag}
            className="px-2 py-1 bg-bg-button-primary border border-border-cyan-dark text-text-primary text-sm flex items-center gap-2"
          >
            {tag}
            <button
              type="button"
              onClick={() => removeTag(tag)}
              className="text-text-muted hover:text-text-primary"
            >
              ×
            </button>
          </span>
        ))}
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="flex-1 bg-transparent text-text-primary placeholder-text-muted outline-none min-w-fit"
        />
      </div>
    </div>
  )
}
