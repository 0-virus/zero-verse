# qa 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 심의·마일스톤 이력은 `docs/governance/**`와 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-03 13:43 KST (M3/M4 QA 산출물 commit-review 독립 대조)

## 현재 단계

- M3는 PR #9 merge commit `59badfe42d539a33091a387b9ee6119d838190b8`로 `dev`에 병합됐고 기존 QA acceptance는 APPROVE/PASS다.
- M4 전체·제품 U1·실제 AWS는 별도 gate이며 현재 승인/완료로 보지 않는다.
- 무료 대체 SeaweedFS 4.47 실측은 **U0-ALT 부분 통과, U0 전체 BLOCKED**다. 최종 QA 판정은 **BLOCKED (U0 PASS 아님)**, 확신도 99/100, 위험 HIGH/기밀성 CRITICAL이다.

## 진행 중

- 회의 §17·§19와 현재 `gradle/u0/**`, `frontend/u0/**`를 읽기 전용 대조했다. SeaweedFS 4.47은 PAB route는 등록하지만 공식 handler가 `ErrNotImplemented`를 반환해 setup/read가 `HTTP501`이며, PAB VERIFY는 false/분모 28/exit 1로 유지된다. expected status·negative 기준은 낮아지지 않았다.
- 공식 native SeaweedFS 4.47 ZIP SHA-256 `8809359079e62fcd60574ff661449160899622c52072f3f569d346669079efe9`, Java SHA-256 `EA8D0D21161159638683D5CD7871F391578641F743AD9D12F45572DE98D75F6D`, HTML SHA-256 `B98C583B82C49F87EB08C30550624EADADF58DDE1F8F6CB86DF07EA014442EDE`를 root 산출물과 대조했다.
- 후속 XHR 비교에서 사용한 일회성 진단 HTML SHA-256은 `4E25BBC4E140F2F705899985896A0FDCE4D50825AE283CB879D7B58889AEB7C9`로 root 증거에 기록됐고, 진단 후 현재 `frontend/u0/index.html`에는 XHR/diagnostic 코드가 남아 있지 않다. browser 본시험 증거의 HTML hash와 일회성 진단 hash를 혼합하지 않는다.
- Java core는 `19/19 CASE PASS`, `27/28 VERIFY PASS`, exit 1이다. SeaweedFS identity가 실제 anonymous private GET `403`, signed GET `200/403/403`, OwnershipControls, CORS, checksum/HEAD/range/Tika/object absence를 통과시켰다.
- root fresh browser 관측은 `19/19 cases · 18 PASS · verify 27/28 PASS`다. unsigned private GET은 실제 `403`이지만 `over-5mb-signed-at-max`는 `fetch TypeError/CORS`로 HTTP status가 노출되지 않아 FAIL 유지다. `/verify` 유일 실패는 PAB `UNSUPPORTED HTTP501`이다.
- fresh harness `81971`에서 동일 manifest/body를 native XHR로 한 번 대조했으며 `status 0 · event error · headers 미수신`이었다. fetch→XHR 교체로도 response status가 readable하지 않았고, 이는 진단 증거일 뿐 U0 PASS 승격이나 fetch fallback 근거가 아니다. JS의 ACAO/Connection 미관측은 CORS header exposure 제한 때문에 CORS failure를 뜻하지 않는다.
- 서버·harness는 종료됐고 SeaweedFS data는 보존됐다. 모든 listener loopback 확인, synthetic identity는 anonymous 없이 broad `Admin/Read/Write/List/Tagging`이며 least-privilege 증거가 아니다. STS signing-key/SSE/filer gRPC 경고는 운영 범위 밖 잔여 위험으로 남겼다.

## 다음 작업

1. `qa/M4-u0-review.md`의 SeaweedFS acceptance matrix와 BLOCKED 판정을 유지하고, SeaweedFS 결과를 AWS acceptance 또는 U0 완료로 승격하지 않는다.
2. browser oversized PUT은 fetch와 native XHR 모두 readable status가 아니므로 FAIL을 유지한다. raw HTTP403·object absence·XHR status0을 browser status의 대체 증거로 쓰지 않는다.
3. PAB 4 flags·least-privilege/deny precedence·AWS CORS/SigV4/checksum/conditional/size semantics는 실제 AWS 또는 정식 지원 IAM/PAB 환경의 별도 gate로 남긴다. U1은 실제 AWS가 아닌 제품 도메인/FE vertical slice다.

## 차단 요인

- SeaweedFS 4.47은 PAB route만 있고 handler가 stub이라 `HTTP501`; 회의 §17상 조용한 skip/full PASS가 금지돼 U0 전체를 승인할 수 없다.
- browser `over-5mb-signed-at-max`가 fetch와 XHR 모두 response status를 읽지 못한다(`fetch TypeError/CORS`, `XHR status 0/error`). 조기 close/reset 가설은 강해졌지만 exact browser TCP 원인은 미확정이며 통과로 바꾸지 않는다.
- SeaweedFS의 broad synthetic identity·loopback data-plane은 AWS IAM policy semantics, public-access block, TLS/내구성/수명주기/STS/SSE/control-plane hardening을 증명하지 않는다.

## 주요 산출물

- `qa/M4-u0-review.md` — SeaweedFS U0-ALT 독립 판정·acceptance matrix·잔여 AWS/U1 gate
- `build/u0-seaweedfs-diagnostic-evidence.md` — root의 raw HTTP·TCP·native XHR 후속 관측(실행 근거, QA 직접 실행 아님)
- `.claude/team/qa/WORKLOG.md` — 2026-09-23 최종 독립 QA append
- 제품·BE/FE 원본·governance/worklog 원본은 QA가 변경하지 않았다.
