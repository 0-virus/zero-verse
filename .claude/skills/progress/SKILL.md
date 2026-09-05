---
name: progress
description: "zeroverse-server의 커밋·브랜치·PR·워크로그·PRD를 대조해 마일스톤 진행상황과 다음 할 일을 보고한다. Use when the user asks where the project stands, what's done, what's next, which milestone is in progress, or wants to resume work after a break."
trigger: /progress
---

# /progress — 진행상황 · 다음 할 일 확인

역할 팀 전체의 짧은 재개 브리핑은 `/brief`가 기본이다. 이 스킬은 PRD §10의 마일스톤 표와 과거 브랜치 자산까지 자세히 볼 때 사용한다.

커밋 기록을 1차 근거로 삼되, PR 상태 · 워크로그 · `docs/PRD.md` §10 마일스톤 정의와 **교차 검증**해서 "지금 어디까지 왔고 다음에 뭘 해야 하는지"를 한 번에 보고한다.

## 사용법

```
/progress          # 전체 마일스톤 현황 + 다음 할 일 + 이상 징후
/progress M5       # 특정 마일스톤 초점 (범위·선행 조건·블로커 상세)
```

## 전제 — 현재 워킹트리가 최신이다

> **현재 체크아웃된 브랜치의 워킹트리가 최신 상태이며, 진행상황 판정의 기준(source of truth)이다.**

따라서:

- **진행상황은 현재 브랜치의 커밋·파일로만 집계한다.** 다른 브랜치가 더 앞서 있는지 탐색하지 않는다.
- 2026-07-25 재시작 이전 이력(PR #1~#5, `archive/dev-legacy`와 관련 구 브랜치)은 현재 진행으로 세지 않는다. 삭제하지 말고 참고 자산으로만 표시한다.
- 현재 `dev`·마일스톤 브랜치 상태는 매번 Git에서 다시 읽는다. 과거 재시작 당시 상태를 현재 사실로 재사용하지 않는다.

## 원칙

- **읽기 전용이다.** `checkout` / `switch` / `merge` / `commit` / `push` / 파일 생성·수정 전부 금지. 다른 브랜치의 파일이 필요하면 `git show <ref>:<path>`로 읽는다.
- **커밋 메시지의 자기신고를 믿지 않는다.** `M3: 카테고리 ...` 같은 제목은 "작업했다"는 주장일 뿐이다. 실제 소스 파일 존재와 워크로그 `[머지]` 본문으로 교차 확인한다.
- 근거를 붙일 수 없는 항목은 **"확인 불가"** 로 표기한다. 추정으로 채우지 않는다.

## 실행 절차

### Step 0. 기준 문서 로드

- `docs/PRD.md` §10 "구현 계획 (에픽 · 마일스톤 · 순서)" — M0~M10 정의와 끝의 **의존성 요약**을 읽는다. 이게 리포트 표의 기준 축이다.
  - `grep -n "^### M" docs/PRD.md` 로 위치를 잡고 해당 구간을 읽는다. (`docs/`는 gitignore라 로컬 파일로만 존재한다)
- `AGENTS.md` "개발 프로세스" 섹션 — 브랜치 전략(`main` / `dev` / `feature/M{n}-<slug>`)과 워크로그 규약이 판정 기준이다.

### Step 1. 기준 ref 확정 = 현재 브랜치

```bash
git branch --show-current
git status --short
git log --oneline -20
git log -1 --format='%h %ad %s' --date=short
```

- 여기서 나온 **현재 브랜치가 기준 ref**다. 이후 모든 조회는 워킹트리와 이 브랜치 기준으로 한다.
- 커밋되지 않은 워킹트리 변경(`git status`)도 **진행 중인 작업으로 집계**한다.
- 실제 구현 산출물이 있는지 파일로 확인한다(커밋 제목 아닌 실물 기준):

```bash
ls build.gradle settings.gradle frontend 2>/dev/null
git ls-files 'src/main/java/com/zeroverse/**' | head -20
```

### Step 2. 초기화 이전 이력 수집 (참고용, 진행 집계 제외)

재사용 가능한 자산이 어디 있는지만 파악한다.

```bash
git branch -a
git for-each-ref --sort=-committerdate --format='%(refname:short) %(committerdate:short)' refs/heads refs/remotes
git rev-list --count <기준ref>..<구브랜치>
```

⚠️ **구 PR은 squash 머지였다.** feature 브랜치의 개별 커밋은 머지 후에도 후속 브랜치의 조상이 **되지 않는다**(`git merge-base --is-ancestor origin/feature/M0-scaffold origin/feature/M4a-post-backend` → false). 구 이력에서 "어느 브랜치에 뭐가 들어 있는지" 볼 때는 squash 커밋 기준으로 본다:

```bash
git log --oneline main..<구브랜치> | grep -E '\(#[0-9]+\)$'   # (#N) = 머지된 PR 번호
```

### Step 3. PR 상태 대조

```bash
gh pr list --state all --limit 30 --json number,title,headRefName,baseRefName,state,mergedAt
```

- **기준 ref를 head로 하는 PR만 현재 진행상황에 반영한다.** OPEN이면 🔵, MERGED면 ✅ 근거로 쓴다.
- 초기화 이전 PR(#1~#5)은 `참고` 섹션으로 보낸다. CLOSED·미머지여도 경고하지 않는다.
- `gh` 인증 실패·오프라인이면 중단하지 말고 "PR 정보 없음"으로 표시한 뒤 git 근거만으로 판정한다.

### Step 4. 워크로그 · 거버넌스 대조

**기준 ref의 `docs/worklog/`** 를 본다. 워킹트리에 있으면 그대로 읽고, 없으면 존재하지 않는 것으로 판정한다(구 브랜치의 워크로그를 끌어와 진행으로 세지 않는다).

```bash
ls docs/worklog/
# 초기화 이전 워크로그를 참고 자산으로만 볼 때:
git show origin/feature/M4a-post-backend:docs/worklog/M4a-post-backend.md
```

각 워크로그에서 확인할 것:

- 고정 섹션 `[계획]` → `[개발 기록]` → `[이슈·결정]` → `[리뷰]` → `[머지]` 중 **어디까지 채워졌는지** = 사이클 진행 단계.
  - ⚠️ **섹션 헤더 존재만으로 판정하지 않는다.** 템플릿상 헤더는 미리 들어가 있고 본문이 `아직 머지 기록 없음` 같은 placeholder일 수 있다(실제 M4a가 그렇다). `grep -A6 "^## \[머지\]"`로 **본문에 timestamp 항목이 실제로 append됐는지** 확인한다.
- 헤더의 **`- **제외**:`** 항목과 `[이슈·결정]`의 **후속 마일스톤 이월 항목** — 이게 "다음 할 일"의 1차 소스다. (예: M4a 워크로그의 "S3 presigned URL은 M4b, 프론트 TipTap 연동은 M4c")
- `docs/governance/DECISION-REGISTER.md` · `RISK-REGISTER.md`의 미해결 항목.

### Step 5. 판정 후 리포트 출력

## 마일스톤 상태 판정 규칙

**모든 판정은 기준 ref(= 현재 브랜치 + 워킹트리) 기준이다.**

| 상태 | 판정 조건 |
|------|-----------|
| ✅ 완료 | 기준 ref에 구현 커밋 포함 **AND** 워크로그 `[머지]`에 실제 기록 있음 (PR을 썼다면 MERGED) |
| ⚠️ 완료(주의) | 커밋은 있으나 워크로그 `[머지]`가 placeholder이거나 소스 실물이 확인되지 않음 — **어떤 근거가 어긋나는지 명시**한다 |
| 🔵 진행 중 | 커밋 또는 uncommitted 워킹트리 변경 존재, 혹은 워크로그가 `[리뷰]`까지만 채워짐 |
| 📝 계획만 | 워크로그 `[계획]`만 있고 구현 커밋 없음 |
| ⬜ 미착수 | 기준 ref에 커밋·워크로그·소스 모두 없음 (구 브랜치에만 있는 것은 **미착수 + 참고 자산 있음**) |

## 출력 포맷

```markdown
## 현재 위치 (기준: <브랜치> 워킹트리 = 최신)
- 커밋: N개 · 마지막 <최신 커밋 요약> (<날짜>)
- 미커밋 변경: <git status 요약 또는 "없음">
- 구현 산출물: <build.gradle/frontend/src 존재 여부>

## 마일스톤 현황 (PRD §10)
| M | 범위 | 상태 | 근거 |
|---|------|------|------|
| M0 | 스캐폴딩 | 🔵 | <기준 ref의 커밋/파일 근거> |
| ... |

## 다음 할 일
1. <가장 앞선 미완 마일스톤> — 근거: <PRD §10 의존성 / 기준 ref 상태>
2. <블로커·확인 필요 항목> — 출처: <문서 위치>
...

## 참고: 초기화 이전 이력 (진행 집계 제외 · 재사용 가능 자산)
- origin/feature/M4a-post-backend — M0~M4a 백엔드/프론트 구현 + 워크로그 5개. `git show <ref>:<path>`로 참조 가능
- PR #1~#5, archive/dev-legacy — 구 이력

## 이상 징후
- <없으면 "없음">
```

## 이상 징후 체크리스트 (매번 확인)

- [ ] 기준 ref의 커밋 주장과 실제 소스 파일이 불일치(커밋만 있고 구현 없음)
- [ ] 커밋은 있는데 워크로그가 없는 마일스톤 / 워크로그만 있고 커밋이 없는 마일스톤
- [ ] 워크로그 `[이슈·결정]`의 미해결 이월 항목
- [ ] `docs/PRD.md` §9.3 "사용자 확인 필요(미확정)" 항목이 다음 마일스톤을 블로킹
- [ ] `docs/PRD.md` §13 잔여 항목(S3 실제 버킷·리전 등)이 다음 마일스톤을 블로킹
- [ ] `docs/governance/README.md` §2 회의 소집 조건에 걸리는데 심의 기록이 없는 안건

**과거 이력 자체는 이상 징후가 아니다**:
- PR #1~#5와 `archive/dev-legacy`의 상태는 2026-07-25 재시작 이전 참고 기록이다.
- 구 feature 브랜치 tip이 후속 브랜치의 조상이 아님 → squash 머지의 정상 동작일 수 있으므로 Step 2의 실제 로그로 확인한다.

## 금지 사항

- 리포트 출력까지만 한다. 다음 액션은 **제안**하고 멈춘다 — 자동으로 구현·머지·브랜치 정리에 착수하지 않는다.
- 진행상황을 추정으로 메우지 않는다. 근거 없는 칸은 "확인 불가".
