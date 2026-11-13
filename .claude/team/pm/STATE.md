# pm 현재 상태

> 덮어쓰기 스냅샷. 시간순 이력은 `WORKLOG.md`, 마일스톤 이력은 `docs/worklog/**`와 `docs/governance/**`를 본다.

마지막 갱신: 2026-11-13 KST

## 현재 단계

- M10 `테스트 & 마감` **LOCAL MVP CLOSEOUT COMPLETE / DEV INTEGRATED**다. 이 완료·범위는 유지하며 배포 후속은 별도 `X-deployment` 실행이다.
- 현재 checkout은 `main`, HEAD `8c297537c4b2997accaa2793c723169c5fee4b9c`이며 배포 후속 문서·프론트 LOW 변경이 작업 트리에 있다. M10의 `dev` merge 기록은 역사로 보존한다.
- M9는 PR #15 merge SHA `c0df2d33d0dce5ac71f1b358672606f717e739ae`로 `dev`에 통합된 과거 완료 마일스톤이다. M9 historical acceptance는 M10 fresh 결과에 합산하지 않는다.

## 진행 결과

- 계획·독립 심의·승인된 Task2/Task3 구현과 review가 완료됐다. Backend는 고정 Clock test와 표준 Gradle 8.10 wrapper를 적용했고, frontend는 publish 경계 검증을 적용했다. 공개 API·DB schema·security policy·dependency는 변경하지 않았다.
- Backend 강제 fresh gate는 정확한 JDK21/Colima Docker/temp 조건에서 `test check bootJar --rerun-tasks --no-daemon --max-workers=1` exit 0이다. XML 직접 집계는 `109 suites / 637 tests / failures 0 / errors 0 / skipped 0`이다. 이번 재패키징 JAR SHA는 `9dedd8ffd07f3a82c4b3915dbd252ebd38cb8301f4f9628ee0d03f1c5369fdaa`이며, 실제 HTTP/Chrome 인수가 실행한 고정 JAR `1ade616199bd0da11daa5856dcd5d88fe8fae4042c0ee8ab66725baa01188b21`과 별도 provenance다.
- Frontend fresh gate는 `51 files / 472 tests`, lint, build exit 0이다. bundle `958.38 kB` warning은 기능 실패가 아닌 비차단 잔여 항목으로 유지한다.
- 실제 격리 runtime은 MySQL `127.0.0.1:13320`, API `18090`, FE `5180`을 사용했다. `build/m10-evidence/m10-20261112053726-e37fa5d0/acceptance-20261112T063315284522Z-e23298bb/evidence.json`은 `82/82 PASS`, failures 0, 13개 고유 `1440x1000` PNG를 기록한다. source/JAR/FE/harness/runtime/attestation provenance와 before/after가 일치한다.
- Whole-branch 독립 review의 I-1~I-4는 단일 수정 wave 후 scoped re-review에서 모두 `ADDRESSED`, Critical 0 / Important 0, `Ready to merge Yes`다. 실제 Chrome 범위는 bounded keyboard observation과 기존 unit/API/source coverage를 구분하며 모든 mutation을 Chrome에서 실행했다고 주장하지 않는다.
- QA 최종 판정은 `APPROVED / READY TO MERGE`였고, PR #16 등록 CI/check/review thread는 0개였다. `dev` 병합으로 PM은 M10을 `COMPLETE / DEV INTEGRATED`로 확정했다.
- M10 runtime은 정상 종료됐다. API/FE/MySQL 포트 `18090/5180/13320`과 프로세스는 모두 내려갔고 합성 DB·업로드·evidence는 보존됐다.
- 2026-11-13 사용자가 배포 후속 심의 [X-20261113-deployment §10](../../docs/governance/meetings/X-20261113-deployment.md#10-a-v2--사용자-승인-대상-보완본)의 **A-v2 전체 승인**을 명시했다. 신규 Aiven DB 준비, 기존 local 데이터 보존, private S3 direct presigned PUT→server complete verification→canonical 권한 proxy read, V7 additive intent/provider metadata, prod fail-fast/same-origin FE, 배포 산출물 및 T0~T5 독립 검증이 승인됐다.
- 승인 이후 PM이 `docs/PRD.md` §9.13, §10 X-deployment, §13.1 현행 상태를 동기화했다. backend/frontend 구현과 독립 QA는 진행 중이며, 실제 AWS/browser gate·운영 자원·실사용 DB·도메인/TLS는 아직 PASS가 아니다.

## 다음 작업

- X-deployment T0 정본 동기화는 PRD 반영까지 완료했다. T1/T2 backend·S3 API/V7, T3 FE direct upload, T4 운영 설정·배포/rollback 산출물, T5 독립 QA·격리 검증의 실제 상태를 구현 역할·QA 결과와 대조한다.
- 코드 준비 PASS, 개발 AWS/browser PASS, 실제 운영 인수 PASS를 분리해 기록한다. 실제 Lightsail·Aiven·domain/TLS·실사용 DB migration·기존 local 파일 이관은 이번 승인 범위 밖의 별도 gate다.

## 차단 요인

- M10 local MVP closeout은 완료됐고 차단되지 않았다. X-deployment의 제품 구현·자동검증·독립 QA·개발 AWS/browser gate는 아직 진행 중이다.
- 실제 운영 대상의 Lightsail/domain, Aiven endpoint·CA, 운영 S3 identity/prefix, 기존 DB 연결·이미지 이관 여부가 제공되지 않아 T6 운영 인수는 차단된 상태다. 사용자가 승인한 신규 Aiven DB·기존 local 데이터 보존 전제로 코드 준비를 진행한다.
- `RISK-0005/0006/0007/0010/0012/0021` 등 배포·S3/U0·refresh global revoke·slug redirect·역사 DATETIME provenance·실사용 DB·운영 bootstrap 위험은 M10 local PASS로 닫지 않는다.
- 실제 Chrome에서 실행하지 않은 DraftPicker 선택→edit, TopBar profile/search 전 경로, category create/delete/reorder, admin select/confirm/paging 및 focus-trap/restore는 새 hard contract로 승격하지 않고 범위 경계로 기록한다.

## 주요 산출물

- 정본: `docs/PRD.md` §9.13, §10 X-deployment, §13.1 현행 상태; `docs/governance/meetings/X-20261113-deployment.md` §10; `docs/REQUIREMENTS.md` §6.12/NFR-09; `docs/worklog/M10-closeout.md`.
- 실제 acceptance: `build/m10-evidence/m10-20261112053726-e37fa5d0/acceptance-20261112T063315284522Z-e23298bb/evidence.json`.
- Runtime evidence: `build/m10-evidence/m10-20261112053726-e37fa5d0/runtime-20261112T063307134422Z-ef26b501/runtime-evidence.json`.
- Provenance: commit `75ac4d8bcbfe777e1c1a71dca6283be2344704a3`, tree `90e7657a6ccd6e101abcfa46ffee22c375c6e9c8`, source manifest `a188b0dcc6278010b9298300d15474be98450035ca846948e5a44382ea717c27`, acceptance JAR `1ade616...`, FE dist `992d0e1dc72c094afb13754473d07dd3944c1209f3cb0b3331addd83ffb802ef`, harness `9864b13181cab9e3563f5effc1f951546f9e30bb5a8597363d4989e4c5af9ecb`.
- M10 범위 밖: 배포·S3/AWS·U0 해소·사용자 DB `13307`·기존 업로드·모바일·브라우저 파일 선택·운영 bootstrap.
- 통합 확인: M10 historical `dev` merge `288f29cffef0671c20a365921f087fe02d4dfcdb` 및 tree `23ce12bc28c1613c04537587692b89ed9f1922e8`는 역할 기록과 대조했다. 현재 checkout은 `main` HEAD `8c297537c4b2997accaa2793c723169c5fee4b9c`다.
