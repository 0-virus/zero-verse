import type { ReactNode } from 'react';
import { ManagedImage } from '../../features/upload/ManagedImage';

/** 디자인 정본 §7.10. 게시글 본문 영역. */
export function Prose({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-[18px] px-[34px] py-[30px] text-[15px] leading-[1.85] text-text-prose">
      {children}
    </div>
  );
}

/** 인용·콜아웃. 접두 ★. */
export function Callout({ children }: { children: ReactNode }) {
  return (
    <blockquote className="border-l-[6px] border-accent bg-surface-soft px-[18px] py-3.5 text-sm text-text-body">
      ★ {children}
    </blockquote>
  );
}

/** 코드 블록. */
export function CodeBlock({ code }: { code: string }) {
  return (
    <pre className="border-2 border-ink bg-ink px-[18px] py-4 font-mono text-[13px] leading-[1.7] text-text-on-ink shadow-btn">
      <code>{code}</code>
    </pre>
  );
}

interface ContentNode {
  type?: unknown;
  text?: unknown;
  attrs?: unknown;
  marks?: unknown;
  content?: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asAttrs(value: unknown): Record<string, unknown> {
  return isRecord(value) ? value : {};
}

function childNodes(node: ContentNode): ContentNode[] {
  if (!Array.isArray(node.content)) return [];
  return node.content.filter(isRecord) as ContentNode[];
}

function positiveInteger(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : undefined;
}

type OrderedListType = '1' | 'a' | 'A' | 'i' | 'I';

function orderedListStyle(type: OrderedListType | undefined): string {
  switch (type) {
    case 'a':
      return 'lower-alpha';
    case 'A':
      return 'upper-alpha';
    case 'i':
      return 'lower-roman';
    case 'I':
      return 'upper-roman';
    default:
      return 'decimal';
  }
}

function safeHref(value: unknown): string | null {
  if (typeof value !== 'string') return null;
  if ((value.startsWith('/') && !value.startsWith('//')) || value.startsWith('#')) return value;
  try {
    const url = new URL(value);
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null;
  } catch {
    return null;
  }
}

function markedText(node: ContentNode, key: string): ReactNode {
  let rendered: ReactNode = typeof node.text === 'string' ? node.text : '';
  const marks = Array.isArray(node.marks) ? node.marks.filter(isRecord) : [];
  for (const mark of marks) {
    const markAttrs = asAttrs(mark.attrs);
    switch (mark.type) {
      case 'bold':
        rendered = <strong key={`${key}-bold`}>{rendered}</strong>;
        break;
      case 'italic':
        rendered = <em key={`${key}-italic`}>{rendered}</em>;
        break;
      case 'underline':
        rendered = <u key={`${key}-underline`}>{rendered}</u>;
        break;
      case 'strike':
        rendered = <s key={`${key}-strike`}>{rendered}</s>;
        break;
      case 'code':
        rendered = <code key={`${key}-code`}>{rendered}</code>;
        break;
      case 'link': {
        const href = safeHref(markAttrs.href);
        if (href) {
          rendered = (
            <a key={`${key}-link`} href={href} target="_blank" rel="noreferrer">
              {rendered}
            </a>
          );
        }
        break;
      }
      default:
        break;
    }
  }
  return <span key={key}>{rendered}</span>;
}

function renderNode(node: ContentNode, key: string): ReactNode {
  const children = childNodes(node).map((child, index) => renderNode(child, `${key}-${index}`));
  switch (node.type) {
    case 'doc':
      return <div key={key} className="contents">{children}</div>;
    case 'text':
      return markedText(node, key);
    case 'paragraph':
      return <p key={key}>{children.length > 0 ? children : '\u00a0'}</p>;
    case 'heading': {
      const level = Number(asAttrs(node.attrs).level);
      if (level === 1) return <h1 key={key}>{children}</h1>;
      if (level === 3) return <h3 key={key}>{children}</h3>;
      return <h2 key={key}>{children}</h2>;
    }
    case 'blockquote':
      return <Callout key={key}>{children}</Callout>;
    case 'codeBlock':
      return <CodeBlock key={key} code={childNodes(node).map((child) => typeof child.text === 'string' ? child.text : '').join('')} />;
    case 'bulletList':
      return <ul key={key} className="list-disc pl-6">{children}</ul>;
    case 'orderedList': {
      const attrs = asAttrs(node.attrs);
      const start = positiveInteger(attrs.start);
      const type = attrs.type === '1' || attrs.type === 'a' || attrs.type === 'A' || attrs.type === 'i' || attrs.type === 'I'
        ? attrs.type as OrderedListType
        : undefined;
      return <ol key={key} className="pl-6" style={{ listStyleType: orderedListStyle(type) }} start={start} type={type}>{children}</ol>;
    }
    case 'listItem':
      return <li key={key}>{children}</li>;
    case 'horizontalRule':
      return <hr key={key} className="border-0 border-t-2 border-shadow" />;
    case 'hardBreak':
      return <br key={key} />;
    case 'image': {
      const attrs = asAttrs(node.attrs);
      const src = typeof attrs.src === 'string' ? attrs.src : null;
      if (!src) return null;
      return (
        <ManagedImage
          key={key}
          src={src}
          alt={typeof attrs.alt === 'string' && attrs.alt ? attrs.alt : '본문 이미지'}
          className="my-3 max-h-[520px] max-w-full border-2 border-ink object-contain"
        />
      );
    }
    case 'table':
      return <table key={key} className="w-full border-collapse border-2 border-ink"><tbody>{children}</tbody></table>;
    case 'tableRow':
      return <tr key={key}>{children}</tr>;
    case 'tableHeader': {
      const attrs = asAttrs(node.attrs);
      return <th key={key} colSpan={positiveInteger(attrs.colspan)} rowSpan={positiveInteger(attrs.rowspan)} className="border border-ink bg-surface-raise px-2 py-1 text-left">{children}</th>;
    }
    case 'tableCell': {
      const attrs = asAttrs(node.attrs);
      return <td key={key} colSpan={positiveInteger(attrs.colspan)} rowSpan={positiveInteger(attrs.rowspan)} className="border border-ink px-2 py-1">{children}</td>;
    }
    default:
      return children.length > 0 ? <span key={key}>{children}</span> : null;
  }
}

/** 서버가 제공한 TipTap JSON을 허용된 노드/마크만 React 요소로 렌더링한다. */
export function PostContent({ content }: { content: Record<string, unknown> }) {
  return <>{isRecord(content) ? renderNode(content as ContentNode, 'post-content') : null}</>;
}
