<!-- Generated: 2026-07-01 | Updated: 2026-09-06 -->

# zeroverse-server

## 최우선: 에이전트 팀 헌법

모든 Claude·Codex 에이전트는 작업 전에 다음을 순서대로 읽는다.

1. `.claude/CONSTITUTION.md` — 사용자만 바꿀 수 있는 팀 헌법
2. 이 파일과 루트 `CLAUDE.md` — 프로젝트 정본·공통 규칙
3. 팀원이라면 `.claude/team/{역할}/CLAUDE.md` → `STATE.md`

역할은 `backend`, `frontend`, `qa`, `pm` 네 가지다. Claude 정의는 `.claude/agents/`, Codex 정의는 `.codex/agents/`에 있지만, 역할 지침·현재 상태·이력은 `.claude/team/{역할}/`을 공동 정본으로 쓴다. 전체 판단 이력은 `.claude/team/JOURNAL.md`, 운영 방법은 `.claude/team/README.md`가 정본이다. 헌법과 하위 지침이 충돌하면 헌법이 우선한다.

둘 이상의 역할 소유 영역에 걸친 팀 작업은 역할별 에이전트에 분할하고, 독립 작업만 병렬화한다. 한 파일을 둘 이상에게 동시에 맡기지 않는다. 주 에이전트는 모든 결과를 기다린 뒤 실제 파일과 검증 출력을 직접 확인한다. 세션 시작·재개·다음 작업 선정에는 `$brief`를 사용한다.

## Purpose

ZeroVerse Blog MVP 저장소. 개인 블로그 플랫폼으로, 사용자는 자신의 블로그를 운영하고 "유니버스"라 부르는 단방향 신청-수락 관계로 서로의 글을 발견/공유한다. Spring Boot 백엔드와 React 프론트엔드가 구현 중이며, 현재 위치는 Git·`docs/worklog/`·역할별 `STATE.md`를 직접 대조해 판정한다.

## Key Files & Docs

핵심 명세는 모두 `docs/`에 있다. 스펙 원본은 gitignore 대상이고, 에이전트 지침·운영 가이드·`docs/worklog/`·`docs/governance/`는 추적한다. 코드 작성 전 반드시 참조한다.

| Path | Description |
|------|--------------|
| `README.md` | 로컬 테스트 서버·프론트 실행, 자동 테스트, 종료·문제 해결 안내. 리더 소유이며 실제 설정과 대조해 유지한다. |
| `docs/REQUIREMENTS.md` | MVP 전체 요구사항 명세(v2.1). 도메인 모델, API 규칙, FR-*/NFR-*, 프론트 라우트, 엔드포인트 목록, 에픽 초안. **요구사항 정본**(단, `docs/PRD.md` §9 사용자 결정이 override하는 부분 제외). |
| `docs/PRD.md` | 요구사항 + 디자인 정본 + 사용자 결정을 통합한 **구현 실행 명세**(v2.0). 아키텍처/패키지 구조, 화면-API 매핑, 디자인 시스템 토큰(§6), 화면 명세(§7), 결정 로그(§9), 마일스톤 순서(§10), 테스트 전략, DoD. 실제 구현의 로드맵. |
| `docs/design/` | **시각 디자인 정본**(2026-07-24 도입). Claude Design 프로젝트에서 가져온 `.dc.html` 원본 + `DESIGN-SYSTEM.md`(토큰·컴포넌트·13화면 스펙). `docs/design/AGENTS.md` 참고. |
| `docs/governance/` | Codex 기획 심의팀 운영 규칙, 회의록 템플릿, ADR, 결정·위험 레지스터. **Git 추적 대상**. |
| `docs/PM-*.md` | PM의 마일스톤 준비·정합성 분석. 미확정 권고를 포함하는 로컬 산출물이며 승인 정본은 governance/PRD에서 확인한다. |
| `.claude/CONSTITUTION.md` | Claude·Codex 공통 팀 헌법. 에이전트 편집 금지. |
| `.claude/team/` | 네 역할의 지침·상태·append-only 작업 기록과 통합 저널. |
| `docs/log.md` | 개발 학습/작업 로그(과거 JPA·Security·JWT 메모). 현재 소스와 동기화 보장 안 됨(참고용). |
| `AGENTS.md` | (이 파일, 루트) 저장소 최상위 AI 에이전트 안내. git 추적 대상. |
| `.gitignore` | 표준 Spring/Gradle/IDE ignore + `docs/*` 무시(단 `docs/worklog/`, `docs/governance/`는 추적) + `application-local.yml` 제외. |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `docs/` | 요구사항·PRD·디자인·과거 로그. 스펙 문서(REQUIREMENTS/PRD/log/design)는 gitignore. 구현 전 반드시 읽는다. `docs/AGENTS.md` 참고. |
| `docs/design/` | 시각 디자인 정본(`.dc.html` 원본 + `DESIGN-SYSTEM.md`). 프론트엔드 작업 전 반드시 읽는다. `docs/design/AGENTS.md` 참고. |
| `docs/worklog/` | 마일스톤별 개발 로그(`M{n}-<slug>.md`). **git 추적됨**(`!docs/worklog/`). "개발 프로세스" 섹션 참조. |
| `docs/governance/` | 중요 결정과 대형 마일스톤을 심의하는 Codex 기획 심의팀의 정책·회의록·ADR·레지스터. **git 추적됨**. |
| `qa/` | 독립 QA 검토·재현 기록. `qa/AGENTS.md`를 따르며 제품 소스와 구현 테스트는 각 소유 역할에 요청한다. |
| `.claude/` | 공통 팀 헌법, Claude 역할 정의·스킬, 역할별 상태·기록. 앱 코드 아님. |
| `.codex/` | Codex custom agent 설정. 상태 파일은 두지 않고 `.claude/team/`을 공유. |
| `.agents/` | Codex 저장소 스킬. `$brief` 포함. |
| `.omc/` | oh-my-claudecode 런타임 상태. 무시 대상 운영 아티팩트. |

## Architecture

확정 스택(REQUIREMENTS §2 / PRD §2):

- **Backend**: Spring Boot 3.x, Java 21, Gradle, **base package `com.zeroverse`**, Spring Security + JWT, JPA/Hibernate, QueryDSL, MySQL 8.x, Flyway, SpringDoc(`/swagger-ui.html`). 메인 클래스 `com.zeroverse.ZeroverseServerApplication`(`@EnableJpaAuditing`).
- **Frontend**: React 19 + TypeScript, Vite, React Router(SPA, Next.js 미사용), TipTap, TailwindCSS v4, 커스텀 JWT AuthContext. **MVP 데스크톱 전용**.
- **Image Storage**: S3(pre-signed URL 업로드), DB엔 URL만. 로컬은 **LocalStack**, 접근 URL은 **S3 직접 URL**(CloudFront 미사용, PRD §13.1). 실제 버킷·리전은 M4 시 제공.

### 도메인 모델 (요약)

`User 1:N Blog 1:N Post N:0..1 Category`, `Post 1:N PostImage`, `Post N:N Tag`(PostTag), `User N:N User`(Universe), `Post 1:N Comment/Like`, `User 1:N Notification/RefreshToken`. 상세 필드는 `docs/REQUIREMENTS.md` §4 + PRD §3(카테고리 타입 갱신).

### 핵심 정책 (자주 참조됨)

- 사용자 1명당 기본 블로그 1개 자동 생성, 단 `User 1:N Blog` 구조 유지(다중 블로그 확장 대비).
- 임시저장 = `published_at IS NULL`.
- soft delete는 `deleted_at`(Universe/Like/PostTag 등 재생성 가능 관계는 hard delete).
- Access Token=프론트 메모리, Refresh Token=HttpOnly Secure SameSite 쿠키 + rotation 필수.
- 공통 응답(success/data/error/timestamp) + `Pageable` offset 페이징을 모든 API가 따른다.
- 에러코드 도메인 prefix(AUTH_/USER_/BLOG_/POST_/CAT_/UNI_/COM_/LIKE_/NOT_/ADMIN_/UPLOAD_) — REQUIREMENTS NFR-04.

## 개발 프로세스 (마일스톤 파이프라인)

이 파이프라인은 프로젝트 고유 릴리스 절차이고, 역할 팀은 각 단계의 실행 단위다. 파일 소유권·상태 기록·완료 증거는 `.claude/CONSTITUTION.md`와 `.claude/team/README.md`를 함께 적용한다. 기존 `docs/worklog/`·`docs/governance/` 기록은 그대로 유지하며 역할별 기록으로 대체하지 않는다.

모든 구현은 `docs/PRD.md`의 마일스톤(M0~M10) 단위로 아래 사이클을 반복한다. **2026-09-06 사용자 승인으로 Codex backend/frontend 역할이 구현하고 독립 QA·리더가 검토한다.** 구현자와 최종 검토자는 서로 다른 컨텍스트를 사용한다. 기존 Claude 개발 이력은 그대로 보존한다.

사용자는 M2 검증 후 M3를 시작하고 이후에도 마일스톤마다 다음 단계로 연속 진행하도록 지시했다. 마일스톤 완료는 세션의 종료 조건이 아니다. 리더는 검증·기록·PR·머지와 다음 마일스톤 준비를 이어가며, 새로운 개별 승인 안건은 구체적인 근거를 갖춰 요청하고 독립 작업을 계속한다. 승인 대기 중인 안건 자체는 준비·독립 검토만 진행하며 구현하지 않는다.

### 브랜치 전략
- `main` — 릴리스(안정) 브랜치.
- `dev` — 통합 브랜치. 모든 마일스톤 PR의 base.
- `feature/M{n}-<slug>` — 마일스톤마다 `dev`에서 분기(예: `feature/M1-auth`). 완료 후 `dev`로 머지.
- **사전 1회**: baseline를 `main`에 커밋 → `dev` 분기 → `codex:setup`으로 Codex CLI 확인 → `gh auth status`.

### 사이클 (단계 · 담당 · 도구)
| 단계 | 담당 | 도구/스킬 |
|------|------|-----------|
| 0. 분기 | 리더 | `git` — 완료된 선행 마일스톤의 `dev`에서 분기 |
| 1. 계획 | **Codex 리더·PM** | `docs/PRD.md` + `docs/worklog/*` + 역할 STATE를 대조하고 세부 계획 작성 → 리더가 워크로그 `[계획]` 기록 |
| 1a. 조건부 기획 심의 | **Codex 진행자 + Codex 독립 에이전트 3명** | `docs/governance/README.md`의 소집 조건 충족 시 제품성·기술 실현성·전달/위험을 독립 검토. Claude는 심의에 참여하지 않음. `LOW`만 자동 승인, 나머지는 사용자 승인 후 진행 |
| 2. 개발 | **Codex backend·frontend** | 역할별 소유 경로에서 PRD+계획 기반 구현 + 테스트(PRD §11, TDD) |
| 점검 | 구현 역할 | 빌드·테스트·동작 점검 — 자체 통과 확인이며 최종 승인은 아님 |
| 3a. 커밋·PR | 리더 또는 명시 배정된 역할 | 명시한 파일만 원자적 커밋 → `gh pr create --base dev`. 실제 저자만 기록 |
| 3b. 리뷰 | **독립 Codex QA·리더** | diff(`dev...feature/M{n}`) + 정본·워크로그·실측 출력 대조 → 리더가 워크로그 `[리뷰]` 기록 |
| 수정 루프 | 원 구현 역할 → 독립 검토자 | 리뷰 반영 → 재리뷰, 완료 조건 충족까지 반복 |
| 4. 머지 | 리더 또는 명시 배정된 역할 | `gh pr merge` — `dev`로 머지 → 워크로그 `[머지]` 기록 → 다음 마일스톤 |

- **저자와 최종 검토자는 항상 분리** — Codex 역할끼리도 같은 컨텍스트에서 자기 승인 금지.
- 기획 심의가 소집되면 독립 에이전트끼리 메시지나 중간 결과를 공유하지 않는다. Codex 진행자만 결과를 취합하며, 상세 운영 규칙은 `docs/governance/README.md`를 따른다.
- 사이클 동안 역할별 STATE·WORKLOG와 리더 JOURNAL로 배정·완료·의존을 추적한다. 실행기가 제공하면 `TaskCreate`/`TaskUpdate`도 사용한다.

### 워크로그 규약
- 위치: `docs/worklog/M{n}-<slug>.md` — **마일스톤당 Markdown 1파일**. `docs/`는 gitignore이나 `docs/worklog/`와 `docs/governance/`는 예외로 추적됨(PR diff에 로그와 결정 문서 포함).
- 기록: 파일 내부에 timestamp 항목을 **append**(새 파일 남발 금지).
- 고정 섹션: `[계획]` → `[개발 기록]`(실제 구현자와 시각 명시) → `[이슈·결정]` → `[리뷰]`(독립 검토자) → `[머지]`. 리더가 단계별 단일 작성자를 배정한다.
- 목적: 3b 리뷰의 "로그 ↔ 실제 작업 일치" 검증 및 이후 마일스톤 참고.

## For AI Agents

### 소스 오브 트루스 우선순위 (반드시 준수)

구현 판단이 갈릴 때 아래 순서로 따른다:

0. **사용자의 명시적 결정** — `docs/PRD.md` **§9 결정 로그**에 기록된 확정 사항은 아래 문서를 **override**한다. (예: 디자인 소스 교체 §9.0, 카테고리 타입 재정의 §9-H, 공개범위 화면 라벨 §9-B, `나를 발견한` 피드 탭 제거 §9-G, 데스크톱 전용 §9-I.)
1. **`docs/design/` — Claude Design 정본** (프로젝트 `f1af0ea2-421d-44aa-9871-4273726f53fe` "우주 블로그 웹사이트 레이아웃"). **시각 디자인·레이아웃·화면 구성·UI 카피의 최종 권위.** 색·그림자·타이포·간격·패널 배치·버튼 라벨이 다른 문서와 충돌하면 **무조건 디자인이 이긴다**(사용자 결정, 2026-07-24). 요약은 `docs/design/DESIGN-SYSTEM.md`, 정확한 값은 `.dc.html` 원본의 인라인 `style`.
2. **`docs/REQUIREMENTS.md`** — 데이터 모델·API 계약·비즈니스 규칙·접근제어·에러코드의 최종 권위(§9로 override되지 않은 범위).
3. **`docs/PRD.md` 본문** — 위를 통합한 실행 계획.

> **1과 2의 경계**: 디자인은 *어떻게 보이는가*, 요구사항은 *무엇을 저장하고 누구에게 허용하는가*를 정한다. 디자인에 어떤 필드·상태가 그려져 있지 않다는 사실만으로 요구사항의 필드를 삭제하지 않는다(예: `birth_date`, 카테고리 `DEFAULT/LOCKED` 잠금 — PRD §9.3에 확인 대기 항목으로 정리됨). 반대로 디자인이 명시한 레이아웃·라벨은 요구사항 문구보다 우선한다.
>
> **폐기**: Figma 와이어프레임(`GdWn01gB35uT1mebKXGIx6`, page `94:7`)과 거기서 파생된 다크 네온 토큰(`#02020b`/`#22d3ee`/`#a855f7`/골드 `#fde047`)은 더 이상 참조하지 않는다.
>
> 새 충돌 사례를 발견하면 임의 진행하지 말고 PRD §9 표에 추가한 뒤 사용자에게 확인한다. **S3/이미지 업로드는 로컬 LocalStack + S3 직접 URL로 확정(PRD §13.1)** — 실제 버킷·리전 값만 M4 착수 시 제공 필요.

### Working In This Directory

- 어떤 기능이든 코드 작성 전에 **`docs/REQUIREMENTS.md`의 FR/NFR 번호와 `docs/PRD.md`의 대응 섹션(§5 백엔드 / §7 화면)** 을 먼저 확인한다. 프론트엔드라면 **`docs/design/DESIGN-SYSTEM.md`를 함께** 읽는다. 스펙을 임의로 바꾸지 않는다.
- Spring Boot 프로젝트와 React 프로젝트는 구현 중이다. 현재 완료·진행 상태는 커밋 제목으로 추정하지 말고 Git, `docs/worklog/**`, 역할별 `STATE.md`를 대조한다. 구현 순서는 **PRD §10 마일스톤(M0 스캐폴딩 → M1 인증 → … → M10 마감)** 을 따른다.
- 백엔드 패키지는 PRD §2.2(base `com.zeroverse`, 도메인 패키지 + 레이어드), 프론트는 §2.3. DB 컬럼 snake_case / Java 필드 camelCase(NFR-06), JPA 필드는 래퍼 타입.
- **카테고리 타입은 `DEFAULT/GENERAL/LOCKED`** (SERIES 제거, §9-H). 미분류=DEFAULT(변경·삭제 불가), LOCKED=잠금(변경·삭제 불가). **공개범위 enum은 `UNIVERSE`이나 화면 표기는 "친구"**(§9-B).
- M3 카테고리의 상세 예외·계약은 사용자 승인 [ADR-0005](docs/governance/decisions/ADR-0005-categories-contract.md)와 REQUIREMENTS §6.4를 따른다. DEFAULT 순서는 변경 가능하고 LOCKED 숫자 순서는 불변이다. 활성 unique, 공개 count, 초기 설정 후속 카테고리 저장 및 M4 blog lock 인계도 이 계약을 따른다.
- **프론트엔드는 PRD §6 = `docs/design/DESIGN-SYSTEM.md` 토큰을 반드시 적용**(레트로 픽셀 × 크림 페이퍼 × 황혼의 우주):
  - 배경 `#f6ead8`(paper) · 잉크/보더 `#2b1b3d` · 강조 `#e85d75`(accent) · 하드 오프셋 그림자 `#d8c7b0`.
  - 표면 `#fff` / `#fff8ec` / `#ffe9c9` / `#fff3dd`, 텍스트 `#3d2f52`·`#5c4a72`·`#9b8aa8`, 잉크 위 텍스트 `#ffd9a0`.
  - 상태색: PUBLIC/ACTIVE `#2e7d4f`+`#d7f5dd` · 친구(UNIVERSE) `#c86bb1`+`#f9e3f2` · PRIVATE `#9b8aa8`+`#f1ece2` · danger `#c73a55`+`#ffe3ea` · warning `#b8860b`+`#fdf3d7` · info `#2a6f97`+`#ddedf5`.
  - **`border-radius: 0` 예외 없음**, 보더 `3px solid #2b1b3d`(주)/`2px`(보조), 그림자 `6px/7px/8px 오프셋 0 blur #d8c7b0`(버튼/카드/대형 컨테이너).
  - 폰트 `Press Start 2P`(로고 + 짧은 영문 대문자 라벨 **전용, 한글 금지**) + `IBM Plex Sans KR`(그 외 전부).
  - 우주 그라디언트·픽셀 별(`steps(2)` 반짝임)·픽셀 로켓은 **다크 영역(히어로·온보딩)에만**.
  - 화면 레이아웃은 PRD §7 + `docs/design/*.dc.html`에 시각적으로 일치. **데스크톱 전용 `min-width:1440px`.**
  - 폐기: 다크 네온 팔레트(`#02020b`/`#22d3ee`/`#a855f7`/`#fb7185`/`#fde047`), 골드 관리자 강조, 상단바 관리자 버튼, 사이드바 Admin 항목.
- 공통 규약(응답 래퍼·페이징·에러코드·JWT/rotation·slug·sanitize)은 PRD §4를 전 도메인에 일관 적용.
- 완료 판정은 PRD **§12 DoD**: 규칙 구현 + 테스트 통과(placeholder/skip/stub 금지) + Swagger 문서화 + 디자인 일치 + 보안 + 저자와 다른 패스의 검증.
- 구현 시작 후 하위 디렉터리(`src/main/java/com/zeroverse/...`, `frontend/src/...`)별로 AGENTS.md를 추가 생성한다.

### Testing Requirements

`docs/REQUIREMENTS.md` §NFR-09(정본)와 이를 마일스톤별로 정리한 `docs/PRD.md` §11을 따른다: BE 단위(검증/토큰/접근제어/카테고리·soft delete 정책), Repository/JPA(unique·auditing·soft delete 제외), Controller(Validation/401/403/공통 응답), 통합 시나리오(가입→로그인→블로그 설정→글 작성→공개 조회 등), FE(AuthContext, apiClient 401 자동갱신, 라우터 보호). 마일스톤마다 해당 테스트를 함께 작성(TDD 권장).

### Common Patterns

현재 패턴은 실제 `src/**`와 `frontend/src/**`에서 확인한다. `docs/log.md`의 과거 메모(`@EntityListeners(AuditingEntityListener.class)`, `OncePerRequestFilter` JWT 필터, DTO record `@Valid`)는 참고용이며 현재 소스와 대조 없이 그대로 신뢰하지 말 것.

## Dependencies

### Internal

없음 (단일 저장소, 하위 모듈 없음).

### External

`docs/REQUIREMENTS.md` §2 스택 외 추가 패키지 결정 시 이 문서와 PRD를 갱신. 프론트 TipTap 설치: `@tiptap/react @tiptap/pm @tiptap/starter-kit @tiptap/extension-link @tiptap/extension-image`(REQUIREMENTS §8.6).

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
