import { createContext, useContext, useState, type ReactNode } from 'react';
import type { PublicBlogResponse } from '../features/settings/types';

/**
 * 블로그 페이지 히어로 콘텐츠 Context.
 *
 * `/blog/:slug` 페이지에서 동적 데이터를 로드한 후 히어로에 전달한다.
 */
export interface HeroBlogContextValue {
  blog: PublicBlogResponse | null;
  setBlog: (blog: PublicBlogResponse | null) => void;
  actions: ReactNode | null;
  setActions: (actions: ReactNode | null) => void;
}

const HeroBlogContext = createContext<HeroBlogContextValue | null>(null);

// Public pages are also rendered in isolation by route tests and lightweight embeds. Keep the
// fallback identity stable: returning fresh no-op callbacks on every render would retrigger any
// effect that depends on `setBlog`/`setActions` indefinitely when no provider is mounted.
const FALLBACK_HERO_BLOG_CONTEXT: HeroBlogContextValue = {
  blog: null,
  setBlog: () => {},
  actions: null,
  setActions: () => {},
};

export function HeroBlogProvider({ children }: { children: ReactNode }) {
  const [blog, setBlog] = useState<PublicBlogResponse | null>(null);
  const [actions, setActions] = useState<ReactNode | null>(null);

  return (
    <HeroBlogContext.Provider value={{ blog, setBlog, actions, setActions }}>
      {children}
    </HeroBlogContext.Provider>
  );
}

export function useHeroBlog(): HeroBlogContextValue {
  const context = useContext(HeroBlogContext);
  if (!context) {
    // Fallback: Provider 없을 때(테스트 등)는 안정적인 더미 컨텍스트를 반환한다.
    return FALLBACK_HERO_BLOG_CONTEXT;
  }
  return context;
}
