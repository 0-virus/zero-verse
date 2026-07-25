/**
 * `/blog/:blogSlug` 블로그 화면의 본문 열.
 *
 * 페이지 제목은 190px 히어로(`AppShell`)가 담당하므로 여기서 h1을 다시 두지 않는다
 * (`ZeroVerse Pages.dc.html` SCREEN: BLOG 구조).
 *
 * 카테고리·통계는 좌측 `ScreenPanel`이 담당한다. 실제 글 목록은 M4다.
 */
export function BlogPage() {
  return (
    <div className="border-[3px] border-ink bg-surface px-6 py-16 text-center shadow-card">
      <p className="text-[13px] text-text-muted">아직 발행된 글이 없습니다.</p>
    </div>
  );
}
