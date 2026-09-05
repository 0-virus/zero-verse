# ZeroVerse 팀 통합 저널

> 리더 소유, append-only. 역할을 가로지르는 결정·전달·검증만 기록한다. 마일스톤 상세 기록은 `docs/worklog/**`, 심의 기록은 `docs/governance/**`가 정본이다.

## 2026-09-06 — 역할 기반 팀 운영 도입

- 비교 기준: `C:/Users/PC/Desktop/ZeroWiki-SaaS`의 `AGENTS.md`, `CLAUDE.md`, `.claude/CONSTITUTION.md`, `.claude/team/**`, `.claude/agents/**`, `.codex/**`, `.agents/skills/brief/**`, 에이전트 팀 운영 가이드.
- 도입: backend/frontend/qa/pm 역할, 파일 소유권, 역할별 STATE·append-only WORKLOG, 통합 JOURNAL, Claude·Codex 공동 상태, `/brief`·`$brief`, 완료 증거와 구조 변경 동기화 규약.
- 보존: 기존 `docs/worklog/M0~M2`, `docs/governance/**`, `/progress` 스킬 및 작업 중인 M2 변경은 이동·초기화·덮어쓰기하지 않았다.
- 프로젝트별 차이: ZeroVerse의 정본 우선순위, 기획 심의, `main`/`dev`/`feature/M{n}` 파이프라인, 저자-검토자 분리를 헌법에 연결했다.
- 검증: 역할 파일·설정 파일 존재, TOML 구문, 참조 경로, gitignore 재포함 여부를 리더가 확인한다.

## 2026-09-06 04:41 KST — M2 종료 검증과 연속 마일스톤 진행 착수

- 사용자 지시: 검증 역할은 검증, 준비 역할은 다음 마일스톤 준비를 시작한다. 모두 완료하면 M3를 시작하고 이후에도 마일스톤 완료마다 다음 단계로 계속 진행한다.
- 기준: `feature/M2-settings` / `ed8f2c1`, 작업 트리 clean. PR #8은 OPEN이고 원격 HEAD는 `403eb31`이다. 로컬 후속 커밋의 최종 검증을 먼저 수행한다.
- 배정: backend는 M2 전체 BE 테스트·빌드, frontend는 FE 테스트·lint·build, qa는 독립 계약·회귀 리뷰, pm은 확정 결정의 PRD 동기화와 M3 준비를 맡는다. 제품 코드 변경은 검증 결과와 저자 배정을 확정한 뒤 시작한다.
- 단일 작성자: 리더가 M2/M3 마일스톤 worklog, governance, 이 JOURNAL 및 Git 단계 전환을 소유한다. 역할은 자기 STATE/WORKLOG만 기록하고 아직 stage/commit/branch 변경을 하지 않는다. `qa/M2-review.md`와 `docs/PM-M3-readiness.md`의 신규 소유자는 각각 qa와 pm이다.
- 지속 실행: 현재 작업에 시간별 heartbeat `zeroverse`를 등록했다. 실행 중인 일을 중복 실행하지 않고, 유효한 다음 단계가 있으면 재개하며, 동일 승인 대기를 반복 요청하지 않는다.
- 확인: Claude Code `auth status`가 `loggedIn: false`다. 기존 AGENTS.md의 Claude 구현/Codex 리뷰 절차를 Codex 역할 구현+독립 검토로 바꿀지 사용자에게 비동기 확인을 요청했다. 검증과 계획 준비는 계속 진행한다.
- 다음: 현재 HEAD의 실제 검증 결과와 M3 안건을 취합하고, 완료 증거가 충족된 M2부터 기록·PR·머지 절차를 이어간다. 새 정책 결정은 구체적인 안건으로 구분한다.

## 2026-09-06 — Codex 구현·독립 검토 전환 명시 승인

- 사용자 응답: "Codex 역할 구현 + 독립 검토로 진행 (권고)". Claude 인증 없이 Codex backend/frontend가 구현하며 독립 QA·리더가 최종 검토한다.
- 변경: AGENTS.md의 Claude 전용 구현·수정·Git 담당과 사용할 수 없는 고정 실행기 지정을 현재 역할 파이프라인으로 대체했다. CLAUDE.md, 팀 README와 Codex 운영 가이드에도 승인과 연속 진행 규칙을 연결했다. 과거 worklog·회의록과 불변 헌법은 변경하지 않았다.
- 이유: 사용자가 요청한 연속 개발을 실행 가능한 역할 체계로 수행하고 저자/검토자 분리를 유지하기 위함이다. 역할 STATE/WORKLOG·JOURNAL을 실행기 공통 작업 추적 수단으로 명시했다.

## 2026-09-06 05:10 KST — M2 독립 검토와 M3 심의 병행

- 리더 직접 검증: BE XML 53개/347 tests/실패·오류·skip 0, 별도 MySQL/JAR/Vite의 실제 가입→초기 설정→프로필·블로그 독립 저장→새로고침→slug 변경·이전404·공개 hero, 1440px 디자인 및 서버 중단→일반 오류→다시시도 복구를 확인했다. 자세한 실행 증거는 M2 worklog에 기록했다.
- 잔여 M2 수정 소유자: backend는 OpenAPI scheme/public security/error response와 자동검증, frontend는 디자인·이미지 변경 동작·비404 오류 복구·stale 응답 회귀 실효성, qa는 독립 재검토다. 마지막 전체 검증과 실제 새 JAR 확인 전 승인하지 않는다.
- 구조 동기화: QA 소유 `qa/AGENTS.md`·`qa/M2-review.md`가 생성됐다. root AGENTS 및 팀 README에 진입점을 연결했다. PM 소유 신규 준비 보고는 `docs/PM-M3-readiness.md`, `docs/PM-M4-readiness.md`이며 기존 `docs/*` ignore 정책을 유지한다. 최종 승인 근거는 추적되는 governance/worklog에 기록한다.
- M3 심의는 동일 REVIEWING 안건을 PM(Product), backend(Architecture), qa(Delivery & Risk)의 독립 컨텍스트에 전달했다. 상호 의견 공유를 금지했고 리더만 취합한다. 세 검토 결과와 필요한 구체 사용자 승인을 받기 전 M3 구현은 하지 않는다.
- PM은 자신의 Product 검토 완료 후 M4 선행 의존·사용자 제공값을 준비한다. M4 구현이나 새 범위 추가는 배정하지 않았다.

### 2026-09-06 05:08 KST · 시각 표기 정정

- 직전 항목 제목의 05:10은 잘못 기입했다. 현재 시계 확인값은 UTC 2026-09-05 20:08:32이며 위 배정·동기화는 KST 05:08 이전에 수행됐다. 내용과 검증 판정은 변경하지 않는다.

## 2026-09-06 05:31 KST — FE 독립 최종 검증 및 운영 안내 정합화

- 리더 직접 실행: FE 전체 21파일/250테스트, lint, build가 모두 exit 0이다. stale 응답 방어만 메모리 변환으로 제거한 별도 실행에서 관련 두 테스트가 모두 실패했고 원본 소스 SHA256은 동일했다. 상세 증거는 M2 worklog와 `build/m2-fe-final-*.log`, `build/m2-stale-mutation.log`에 남겼다.
- 현재 안내의 모순 정리: `docs/governance/README.md`에 남아 있던 Claude 전용 구현·분기 지정을 사용자 승인 Codex 역할 구현/독립 검토와 리더 Git 배정으로 대체했다. 이유는 기존 명시 승인과 실제 실행 담당을 일치시키기 위함이다. 세 독립 심의·LOW 초과 개별 승인·저자/검토자 분리는 유지하며 과거 회의·ADR·헌법은 수정하지 않았다.
- QA에는 FE 최종 증거와 운영 안내 변경의 독립 검토를 요청했다. BE 최종 전체 검증·새 JAR 확인과 M3 Architecture 심의는 진행 중이며, 이 항목은 M2 완료 또는 M3 승인 기록이 아니다.

## 2026-09-06 05:38 KST — M2 독립 승인과 M3 개별 결정 요청

- M2: 리더·QA가 최신 BE348/FE250 및 빌드, 새 JAR/OpenAPI·브라우저·stale mutation을 확인했다. 독립 QA APPROVE 96/100, source/evidence blocking 없음. 리더가 검증된 역할 경로만 명시 stage하여 원자 커밋·PR·dev 머지를 수행한다. 제품 코드는 동결했다.
- 운영 안내 보완: AGENTS/팀 README의 연속 진행 문장에 승인 대기 안건 자체는 준비·독립 검토만 가능함을 덧붙였다. QA가 보고한 오독 여지를 해소했으며 새 권한을 부여하지 않는다. M2 worklog는 과거 고정 섹션을 보존하고 종료 리뷰 continuation으로 최신 기준을 명시했다.
- M3: 세 독립 결과(Product/Architecture 조건부 찬성, Delivery/Risk 선행조건 보류)를 취합했다. 원안의 상태 혼재·활성 unique 구현 방식·동일 ID reorder의 last-write-wins 한계·M4 잠금 참여·DEFAULT 멱등 입력 모순을 미승인 회의록에서 명확히 했다. 원문 권고·가정·반론과 공개 count 이견을 요약 보존했다. 이유와 변경은 회의록 §10에 기록했다.
- 사용자에게 Q1~Q4를 개별 비동기 질문으로 제시했다. 자동/포괄 승인으로 처리하지 않으며 응답 전 M3 구현은 하지 않는다. 기다리는 동안 M2 Git 절차를 계속한다.

## 2026-09-06 — M2 dev 머지 완료·M3 계획 인계

- GitHub PR #8 MERGED, `mergedAt=2026-09-05T20:41:12Z`, merge `4c129e20f58a6ccb9c61246d103934702516c295`. 검증 HEAD `13d8deb`를 고정해 머지했고 로컬 dev를 fast-forward했다. 원격/로컬 제품 tree는 검증본과 같다.
- `$brief`로 현재 Git·네 역할·PRD §9/10·결정/위험·M2 로그를 재대조했다. source/evidence/PR 완료와 역할의 과거 미머지 스냅샷 차이를 확인해 각 소유자에게 마지막 상태 동기화를 배정했다.
- 리더 소유 `docs/worklog/M3-categories.md`를 기존 마일스톤당 1파일 규약으로 만들었다. 계획/심의 승인대기만 기록했고 제품 구현·승인·머지를 주장하지 않는다. 결정 레지스터에 USER_DECISION_REQUIRED 안건을 연결했다. root/docs 지침의 기존 worklog 경로 규약은 그대로 적용된다.
- 운영 문구 동기화: `docs/worklog/README.md`의 개발 기록 담당 `Claude`를 현재 사용자 승인 `구현 역할`로 교체했다. 이유는 AGENTS/헌법의 실제 배정과 일치시키기 위함이며 과거 worklog 저자 기록은 변경하지 않았다. 이 안내 파일은 리더가 소유한다.
- M2 마감 후에도 M3 준비를 이어갔다. 다음 제품 작업은 Q1~Q4 승인→정본/ADR 동기화→M3 분기/구현이다. 승인에 종속되지 않은 M4 준비도 이미 끝났으며 새로운 제품 범위를 만들지 않는다.
