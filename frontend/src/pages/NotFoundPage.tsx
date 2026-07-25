import { PageScaffold } from './PageScaffold';

export function NotFoundPage() {
  return (
    <PageScaffold
      eyebrow="404"
      title="페이지를 찾을 수 없습니다"
      description="요청한 주소에 해당하는 화면이 없습니다."
    />
  );
}
