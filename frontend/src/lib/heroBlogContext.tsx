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
}

const HeroBlogContext = createContext<HeroBlogContextValue | null>(null);

export function HeroBlogProvider({ children }: { children: ReactNode }) {
  const [blog, setBlog] = useState<PublicBlogResponse | null>(null);

  return (
    <HeroBlogContext.Provider value={{ blog, setBlog }}>
      {children}
    </HeroBlogContext.Provider>
  );
}

export function useHeroBlog(): HeroBlogContextValue {
  const context = useContext(HeroBlogContext);
  if (!context) {
    // Fallback: Provider 없을 때(테스트 등)는 null을 반환하는 더미 컨텍스트
    return { blog: null, setBlog: () => {} };
  }
  return context;
}
