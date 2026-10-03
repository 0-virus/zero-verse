# frontend 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`를 본다.

마지막 갱신: 2026-10-03 (frontend commit-review: U0 정적 self-check·lint/build/Vitest 재검증 및 기록 갱신)

## 현재 단계

- M3 제품 FE는 완료·독립 QA 승인·리더 승인 및 PR #9 `dev` merge(`59badfe42d539a33091a387b9ee6119d838190b8`)까지 끝났다.
- 현재 배정은 제품 M4가 아니라 사용자 승인 범위인 M4 회의록 §12.3 U0 로컬 업로드 안전성 검증과, 부모가 전달한 SeaweedFS U0-ALT 설치·재시험 승인 범위뿐이다.
- 현재 checkout은 `feature/M4-posts`, HEAD `59badfe42d539a33091a387b9ee6119d838190b8`이다. Git 조작은 리더 소유이며 이 역할은 수행하지 않는다.

## 진행 중

- `frontend/u0/index.html` 단일 정적 페이지를 유지한다. `http://127.0.0.1:14567/`에서 BE manifest를 읽고 정확히 허용된 `http://127.0.0.1:14566` 또는 `http://127.0.0.1:14568` 로컬 S3-compatible endpoint에 실제 브라우저 PUT/GET/HEAD 검증을 수행하는 실험 전용 산출물이다. 14566 LocalStack의 기존 결과와 14568 SeaweedFS-ALT 결과는 별도 증거로 구분한다. 기존 19-case Run 경로는 불변이며, 비교용 native XHR 진단은 실측 후 제거했다.
- manifest `{cases:[{id,method,url,headers,bodyBase64,contentType,expectedStatuses,waitMs?}]}`를 메모리 closure 안에서만 소비하고, case마다 `id/status/code/pass`를 렌더한 뒤 다음 case로 계속 진행한다. 마지막에 `/verify`의 `{checks:[{id,passed,detail}]}`를 fetch해 checks summary를 표시한다.
- PUT은 base64 → `Uint8Array` → `new File([bytes], "u0-synthetic.bin", {type: contentType})` → raw `fetch` body 순서다. `File.size`를 확인하고 JS에서 `Content-Length`·`Host`를 설정하지 않는다. 허용 method는 `GET/HEAD/PUT/OPTIONS`, URL origin은 정확히 `http://127.0.0.1:14566` 또는 `http://127.0.0.1:14568`이다. 기존 Content-Length 서명 계약과 기본 기대값은 FE에서 완화하지 않는다.
- `waitMs`는 정수 0~10,000ms로 검증하며 요청별 `AbortController` 30초 timeout, Run 중복 잠금, case별 HTTP/TypeError·CORS/timeout·client validation 분류를 적용했다. negative case는 기대 HTTP status 일치로만 PASS하며 network error는 PASS가 될 수 없다.
- `safeId`는 `[A-Za-z0-9._-]{1,80}`만 표시하고 그 외는 고정 fallback으로 바꾼다. signed URL·credentials·body·headers·verify detail 및 실행 결과는 DOM/console/storage/window 전역에 기록하지 않는다. self-check는 native `atob`, local-origin allowlist, 실제 5MiB `File.size` 경계를 확인한다.
- root의 fresh Codex In-app 브라우저(`127.0.0.1:14567`)에서 SeaweedFS `14568`을 실측했다. self-check PASS, 19 cases 중 18 PASS였고, 정상 5MiB PUT 및 type/checksum/expiry/replay/signed GET/unsigned GET403 negative는 HTTP PASS였다. `over-5mb-signed-at-max`는 fetch TypeError/CORS로 관측되어 HTTP code가 노출되지 않았고 FAIL로 유지했다. `/verify`는 27/28 PASS이며 PAB UNSUPPORTED HTTP 501만 실패했다. 로그는 `build/u0-seaweedfs-browser-v2-server.log`다.
- root가 동일 live manifest에 대해 PowerShell/원시 TCP로 `Content-Length=5,242,881`을 보낸 대조에서는 즉시 HTTP 403 `SignatureDoesNotMatch`, 정확한 `Access-Control-Allow-Origin`, `Connection: close`를 관측했다. 이 결과는 CORS 누락을 확정하지 않으며, SeaweedFS의 body 미소비 조기 서명 거절 뒤 Go `net/http` close/reset 경로를 강한 원인 후보로 만든다. Chromium의 실제 응답 수신 여부는 native XHR 진단 버튼 실행 전까지 미확정이다.
- root는 fresh same-manifest에서 비교용 native XHR을 실행해 `status 0 · event error · HEADERS_RECEIVED 미수신`을 관측했다. ACAO/Connection은 JS에서 읽히지 않았으며, XHR 교체로 HTTP status가 확보되거나 U0 판정이 개선되지 않았다. 진단 버튼·함수·추가 CSS는 실측 후 제거했다.

## 검증 결과

- U0 inline JS 문법: `new Function` 검사 `U0_INLINE_JS_SYNTAX=PASS`, Node exit 0.
- SeaweedFS U0-ALT FE 변경 관련 inline JS syntax → `U0_INLINE_JS_SYNTAX=PASS`, 두 origin self-check → `U0_SELF_CHECK_TWO_ORIGINS=PASS`, 관련 정적 확인 → `U0_RELATED_STATIC_CHECK=PASS`를 각각 exit 0으로 확인했다. 이는 서버 runtime·CORS·서명·브라우저 결과를 대신하지 않는다.
- SeaweedFS U0-ALT fresh-browser 실측: self-check PASS; 19 cases 중 18 PASS. `over-5mb-signed-at-max`는 fetch TypeError/CORS/HTTP code 미노출 FAIL이며 원인은 CORS 정책인지 연결 종료인지 미확정이다. 정상 5MiB 200, 다른 type/checksum/expiry/replay/signed GET/unsigned GET403, over-size object 부재는 root 브라우저에서 확인됐다.
- 일회성 XHR 진단 추가 당시 inline JS syntax와 관련 정적 invariant는 각각 `PASS`였고, 제거 후에도 inline JS syntax는 `PASS`다. root의 실제 비교 결과는 `status 0 / error / headers 미수신`이며, XHR은 보조 진단에 그치고 기존 fetch 기대값·판정을 대체하지 않는다.
- 진단 경로 제거 후 `frontend/u0/index.html`에 XHR/diagnostic 마커와 임시 header wrapper/CSS가 없음을 정적 확인했다. 현재 파일 SHA-256은 `FEBE910EA47F526AF901C86502EE774E51874E417FC51D914EC8353E23691DDE`이며, 기존 기록의 기준 해시 `B98C583B82C49F87EB08C30550624EADADF58DDE1F8F6CB86DF07EA014442EDE`와는 불일치한다. 단순 LF/CRLF 변환 후보도 일치하지 않아 추가 추정 복원은 중단하고 부모에게 차이를 보고한다.
- SeaweedFS `/verify`: 27/28 PASS. 유일한 실패는 PAB UNSUPPORTED HTTP 501이며, 미지원 기능을 조용히 skip하거나 PASS로 바꾸지 않는다.
- 기존 U0 diff whitespace 확인은 과거 기록이며, 이번 작업에서는 사용자 지시대로 Git 명령을 실행하지 않았다.
- 정적 보안/구조 확인: Run U0 버튼·결과 열·`new File`·`atob`·`AbortController`·allowlist 존재, `POST/DELETE`·`window`·`console`·Web Storage 사용 없음.
- 14566 기존 root 실제 브라우저 결과: 19/19 cases, 18 PASS, verify 27/28 PASS. fetch TypeError/CORS는 없었고 모든 case가 HTTP 결과로 분류됐다. 유일한 실패는 `unsigned-private-get`이 HTTP 200을 반환해 expected 403 assertion에 실패한 것이다. 14568 fresh-browser 결과는 위 SeaweedFS 실측 bullet에 별도로 기록한다.
- 기존 저장소 FE `npm.cmd run lint`: exit 0.
- 기존 저장소 FE `npm.cmd run build`: exit 0(Vite 64 modules).
- 기존 저장소 FE `npm.cmd test -- --run`: 23 files / 273 tests 중 270 passed, 3 failed로 종료. 실패 위치는 `categoryBehavior` 1건, `authForms` timeout 1건, `settingsBehavior` 1건이다. 이 실행만으로 U0 변경과의 인과 또는 기존 제품 결함을 판단하지 않으며, 제품 소스·테스트는 수정하지 않았다.

## 다음 작업

1. XHR status 0/error 결과를 fetch fallback이나 기대값 변경으로 사용하지 않고, `over-5mb-signed-at-max`의 원인은 backend/root가 early reject·connection close 후보와 서버 로그로 분리한다.
2. PAB UNSUPPORTED HTTP 501의 SeaweedFS 기능 경계를 명시하고, AWS PAB 동등성으로 해석하거나 검증을 완화하지 않는다.
3. 1440px 시각 검증은 아직 수행하지 않았고, 이번 정적 페이지 및 browser U0는 제품 화면 인증을 주장하지 않는다. U0는 실패 1건과 verify 1건 때문에 BLOCKED이며 U1/제품 M4 FE 구현은 현재 미승인이다.

## 차단 요인

- 14566 기존 실제 브라우저 검증은 IAM enforcement가 활성화되지 않아 `unsigned-private-get`이 200으로 통과하지 못했다. SeaweedFS U0-ALT는 사용자가 설치·재시험을 승인했고 fresh-browser 실측까지 수행했지만 `over-5mb-signed-at-max` TypeError/CORS FAIL, native XHR status 0/error, PAB UNSUPPORTED HTTP 501 때문에 전체 U0가 BLOCKED다. 원시 HTTP에서 exact ACAO가 관측됐어도 Chromium fetch status를 대신하지 않으며, XHR 결과도 PASS 승격 근거가 아니다.
- 14566 기존 실행에서는 fetch TypeError/CORS 없이 `/cases`·`/verify`·S3 결과가 모두 HTTP로 관측됐고, 14568 fresh 실행에서는 `over-5mb-signed-at-max`만 fetch TypeError/CORS로 관측됐다. 과거의 Origin 강제 가능성은 두 실행에서 재현되지 않았다.
- 1440px 시각 검증은 미실행이며 제품 화면 인증 근거가 아니다. U1/제품 M4는 별도 승인 전 차단한다.
- FE는 `Content-Length` 서명 제거·기대값 약화·negative 정책 완화를 하지 않았다. 해당 계약의 provider 호환성은 backend/runtime 실측 경계다.
- 이번 사용자의 설치·재시험 승인은 실행으로 소진됐다. 계정 설정·가입·추가 승인 요청은 하지 않으며, 남은 것은 실패 원인 분리와 기능 미지원의 정직한 판정이다.
- 비공개 S3 read URL 세부 계약·제품 API/DB/FE 이미지 소비는 여전히 별도 승인·심의 범위이며 이번 U0 산출물에 포함하지 않는다.

## 소유권 및 변경 경계

- 이전 U0 준비 작업에서 변경한 구현 파일은 `frontend/u0/index.html`이며, 이번 원인 조사에서 추가했던 native XHR 진단 버튼·함수·CSS는 실측 후 제거했다. 제품 `frontend/src/**`, package/dependency, backend/API/DB/AWS/Git는 변경하지 않았다.
- FE 역할은 `frontend/src/**`, `frontend/package.json`, `frontend/package-lock.json`, `docs/design/**`, backend/API/DB/AWS/Git를 변경하지 않았다. 공유 checkout의 backend U0 harness 등 다른 역할 변경은 보존한다.

## 주요 산출물

- [frontend/u0/index.html](/C:/Users/PC/Desktop/zeroverse-server/frontend/u0/index.html)
- `.claude/team/frontend/STATE.md`
- `.claude/team/frontend/WORKLOG.md`
