# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-07 최종 QA 인수 시점 KST

## 현재 단계

- M5 A-v2 FE 구현·디자인 대조·독립 QA를 PASS로 인수했고 root의 Git 통합을 기다리는 단계다.
- 소유 경계는 `frontend/**` 및 `.claude/team/frontend/**`이며, backend·QA·root 문서·Git은 수정하지 않았다.
- `docs/design/DESIGN-SYSTEM.md`와 `ZeroVerse Pages.dc.html` 원본을 직접 대조했으며, QA final(`qa/M5-review.md`)의 1440px 실제 API·시각·키보드 PASS와 V001/V002 CLOSED를 접수했다.

## 이번 wave 구현

- `frontend/src/features/universe/types.ts`에 최소 개인정보 사용자 요약, 방향별 관계 snapshot, 목록 DTO/Page 타입을 추가했다.
- `frontend/src/features/universe/universeApi.ts`에 FR-UNI-01~12 경로·method·page/size/nickname query와 mutation API를 `apiClient` 기반으로 추가했다.
- `UniverseListCard`를 추가해 받은/보낸 신청·연결·following/followers·차단 목록의 검색, 독립 로딩/오류/빈 상태, 페이지네이션, 액션을 렌더한다.
- `SettingsUniversePage`를 scaffold에서 실제 여섯 카드 화면으로 교체했다. 목록별 request generation/account generation guard, 수락·거절·취소·연결 끊기·차단·해제와 mutation 후 canonical 재조회/409 재조회 안내를 구현했다.
- `BlogPage`는 공개 blog load와 보호 관계 snapshot load를 분리한다. guest signin 유도, 본인 관리 액션, A-v2 snapshot 우선순위(차단 해제/비활성/수락/취소/연결 끊기/신청), 늦은 응답 guard와 충돌 재조회가 연결됐다.
- Blog hero NONE와 guest signin CTA를 디자인 정본 카피 `✦ 유니버스 신청`으로 통일했다. guest는 동일 카피의 `/signin` 링크이며 보호 snapshot 요청은 하지 않는다.
- 디자인 대조 wave에서 `UniverseListCard` 헤더를 정본 20px/12px·13px/2px tracking으로 맞추고, 받은 신청 카운트 `CountBadge`(11px·1px/8px), 신청/관계별 38px·34px 아바타, 인라인 `accentSm`/`inert` 액션, 날짜 관계 subtitle을 적용했다. SettingsUniversePage는 원본 설정 main과 같이 여섯 카드를 단일 열 `gap-5`로 이어 붙이고 시각 전용 eyebrow/소개를 제거하되 접근성 h1은 `sr-only`로 유지했다. Blog hero CTA는 정본 3px·16/8px·4px rgba shadow와 관계 신청 neutral variant를 사용한다.
- `frontend/src/features/universe/AGENTS.md`와 API/카드/행동 테스트를 추가했다.

## 검증

- RED: 구현 전 `universeApi.test.ts`·`UniverseListCard.test.tsx`가 import resolution 실패로 종료했다.
- GREEN: M5 targeted (`universeApi`, `UniverseListCard`, router/post behavior)와 확장 `universeBehavior.test.tsx`에서 관계 snapshot 우선순위·stale owner/account·6개 카드 mutation·409 reload·독립 검색/페이지를 검증했다.
- RED mutation: `universeApi.ts`의 size clamp를 일시 제거했을 때 `universeApi.test.ts` 8개 중 1개가 `size=120` 수신으로 실패했고, `Math.min(...,100)` 원복 후 통과했다.
- 디자인 RED: 기존 Panel 헤더/38px 일괄 아바타·2열·hero primary/설정 액션 variant를 회귀 테스트로 고정한 뒤 5개 실패를 확인하고 최소 구현했다. 관련 2 files/18 tests GREEN.
- 카피 RED: 기존 suffix형 hero label과 guest CTA expectation을 prefix형으로 갱신한 뒤 4개 기존 상태 테스트 및 guest CTA 1개가 실패했고, `BlogPage` 카피를 수정해 17개 behavior tests GREEN을 확인했다.
- 전체: 최종 `npm test -- --run --maxWorkers=1` → 31 files/338 tests passed, failure/error/skip 0.
- `npm run lint` → exit 0.
- `npm run build` → exit 0 (`tsc -b`, Vite 137 modules); 500 kB chunk warning만 남았다.
- `git diff --check -- frontend .claude/team/frontend` → 오류 없음.

## 다음 작업 / 차단 요인

- QA가 1440px 실제 API 6개 카드·hero 7상태·refresh·계정 전환·guest·keyboard를 PASS했고 V001/V002를 닫았다. root가 같은 338/lint/build 결과와 screenshot 대조를 직접 확인했다.
- 남은 단계는 root의 Git 통합·M5 최종 기록뿐이다. 실제 Git merge 전이므로 frontend 역할이 마일스톤 완료를 단정하지 않는다.
- npm lockfile 기준 기존 audit 2 moderate/6 high는 임의 fix/upgrade하지 않았다.
