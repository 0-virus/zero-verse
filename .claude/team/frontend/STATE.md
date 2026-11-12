# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-11-12 10:09:00 KST

## 현재 단계

- M9 final-review Minor M1(frontend 관리자 keyword 내부 공백 축약) 수정과 자동 검증이 완료됐다. 독립 scoped 재검토와 root의 Task5 기록·dev 통합은 아직 대기 중이며 M9 통합 완료는 선언하지 않는다.
- 이번 수정은 `frontend/src/features/admin/adminApi.ts`, `frontend/src/test/adminApi.test.ts`, 이 상태 파일과 frontend `WORKLOG.md`, 인계 보고서만 대상으로 했다. 기존 dirty QA/PM 5개 파일은 보존했고 Git 조작은 하지 않았다.
- 사용자 승인 A-v2, REQUIREMENTS §6.13-A, M9 회의 §5·§10/10.1·§11, ADR-0008 및 final-review Minor M1을 따른다. M10·배포·사용자 DB 적용은 제외한다.

## 진행 중

- `normalizeAdminKeyword`는 양끝만 `trim`하고 내부 whitespace를 그대로 보존한다. `adminApi.list`의 수동 literal 회귀가 `Kim  Lee`를 `keyword=Kim%20%20Lee`로 확인하며, 기존 UTF-16 100자·page/size/role/status 검증은 유지한다. `AdminPage`의 URL 입력·검색 제출도 같은 helper를 소비하므로 별도 소비 경로 테스트 파일은 추가하지 않았다.
- TDD RED: `npm test -- --run src/test/adminApi.test.ts` → exit 1, 1 failed/3 passed. 기존 요청은 `keyword=Kim+Lee`로 수신되어 내부 두 공백을 축약했다.
- GREEN/전체 검증: focused `adminApi.test.ts` → 1 file/4 tests PASS; `npm test -- --run --maxWorkers=1` → 51 files/465 tests PASS; `npm run lint` → exit 0; `npm run build` → exit 0, Vite 149 modules.
- build의 JS 958.14 kB 및 500 kB 초과 chunk warning은 기존 비차단 경고이며 이번 수정과 무관하다. 시각 변경이 없는 API/query 수정이므로 1440px 화면 검증은 재실행하지 않았다. 기존 396 PASS 실제 인수·source hash는 수정 전 증거로만 유지하고, 수정 후 독립 scoped 재확인이 필요하다.

## 다음 작업

- root의 Minor M1 독립 scoped 재검토 결과와 Task5 기록·PR/dev 통합을 기다린다. 재검토에서 추가 frontend 결함이 확인될 때만 별도 배정에 따라 수정한다.
- 현재 변경의 최종 범위·명령·출력·남은 gate는 `.superpowers/sdd/M9-admin/task-4-final-m1-report.md`에 기록한다.

## 차단 요인

- frontend focused/full test, lint, build에 남은 실패는 없다. 독립 scoped 재검토, 수정 후 source/hash 대조, root PR/dev 통합은 아직 완료되지 않았다.
- 기존 bundle chunk warning은 비차단 이력으로 유지한다. 사용자 파일·기존 DB·업로드와 타 역할 dirty 변경은 보존한다.
