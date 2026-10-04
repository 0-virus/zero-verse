import type { Editor } from '@tiptap/core';
import type { ChangeEvent, MouseEvent, PointerEvent } from 'react';

export interface EditorToolbarProps {
  editor: Editor;
  onImageFile: (event: ChangeEvent<HTMLInputElement>) => void;
  onMarkdownPaste: () => void;
  isUploading: boolean;
}

interface ToolbarButtonProps {
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

function ToolbarButton({ label, active = false, disabled = false, onClick }: ToolbarButtonProps) {
  const preserveEditorSelection = (event: PointerEvent<HTMLButtonElement> | MouseEvent<HTMLButtonElement>) => {
    event.preventDefault();
  };

  return (
    <button
      type="button"
      aria-label={label}
      aria-pressed={active}
      disabled={disabled}
      onPointerDown={preserveEditorSelection}
      onMouseDown={preserveEditorSelection}
      onClick={onClick}
      className="min-w-[34px] border-2 border-ink bg-surface px-2 py-1 text-xs font-bold text-ink hover:bg-surface-raise disabled:cursor-not-allowed disabled:opacity-50"
    >
      {label}
    </button>
  );
}

export function EditorToolbar({
  editor,
  onImageFile,
  onMarkdownPaste,
  isUploading,
}: EditorToolbarProps) {
  const isActive = (name: string, attrs?: Record<string, unknown>) =>
    editor.isActive(name, attrs);

  return (
    <div className="flex flex-wrap gap-1 border-b-[3px] border-ink bg-surface-warm px-3.5 py-2.5">
      <ToolbarButton
        label="B"
        active={isActive('bold')}
        onClick={() => editor.chain().focus().toggleBold().run()}
      />
      <ToolbarButton
        label="I"
        active={isActive('italic')}
        onClick={() => editor.chain().focus().toggleItalic().run()}
      />
      <ToolbarButton
        label="U"
        active={isActive('underline')}
        onClick={() => editor.chain().focus().toggleUnderline().run()}
      />
      <ToolbarButton
        label="S"
        active={isActive('strike')}
        onClick={() => editor.chain().focus().toggleStrike().run()}
      />
      <ToolbarButton
        label="H1"
        active={isActive('heading', { level: 1 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
      />
      <ToolbarButton
        label="H2"
        active={isActive('heading', { level: 2 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
      />
      <ToolbarButton
        label="H3"
        active={isActive('heading', { level: 3 })}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
      />
      <ToolbarButton
        label="인용"
        active={isActive('blockquote')}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}
      />
      <ToolbarButton
        label="</>"
        active={isActive('codeBlock')}
        onClick={() => editor.chain().focus().toggleCodeBlock().run()}
      />
      <ToolbarButton label="—" onClick={() => editor.chain().focus().setHorizontalRule().run()} />
      <ToolbarButton
        label="— 목록"
        active={isActive('bulletList')}
        onClick={() => editor.chain().focus().toggleBulletList().run()}
      />
      <ToolbarButton
        label="1. 목록"
        active={isActive('orderedList')}
        onClick={() => editor.chain().focus().toggleOrderedList().run()}
      />
      <ToolbarButton
        label="🔗"
        active={isActive('link')}
        onClick={() => {
          const current = editor.getAttributes('link').href as string | undefined;
          const href = window.prompt('링크 주소', current ?? 'https://');
          if (href === null) return;
          if (!href.trim()) editor.chain().focus().unsetLink().run();
          else editor.chain().focus().setLink({ href: href.trim() }).run();
        }}
      />
      <label className="inline-flex min-w-[34px] cursor-pointer items-center justify-center border-2 border-ink bg-surface px-2 py-1 text-xs font-bold text-ink hover:bg-surface-raise">
        <span aria-hidden="true">🖼</span>
        <span className="sr-only">이미지 삽입</span>
        <input
          type="file"
          aria-label="본문 이미지 업로드"
          accept="image/jpeg,image/png,image/webp,image/gif"
          className="sr-only"
          onChange={onImageFile}
          disabled={isUploading}
        />
      </label>
      <ToolbarButton
        label="표"
        onClick={() =>
          editor.chain().focus().insertTable({ rows: 2, cols: 2, withHeaderRow: true }).run()
        }
      />
      <div className="min-w-2 flex-1" />
      <button
        type="button"
        className="h-[30px] border-2 border-ink bg-ink px-3 text-[11px] font-bold text-text-on-ink"
        onPointerDown={(event) => event.preventDefault()}
        onMouseDown={(event) => event.preventDefault()}
        onClick={onMarkdownPaste}
      >
        마크다운 붙여넣기
      </button>
    </div>
  );
}
