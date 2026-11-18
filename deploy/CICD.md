# CI/CD 운영 가이드 — 도입 예정

**현재는 계획·가이드 작성 단계다. 아래 workflow, 배포 버튼, pull agent는 아직 구현·설치되지 않았다.** GitHub 설정이나 운영 서버도 이번 작업에서 변경하지 않았다. 상세 계약과 구현 순서는 [CI/CD 계획](../docs/governance/meetings/X-20261117-cicd.md)에 있다. 현재 수동 배포는 [README](README.md)를 따른다.

## 1. 도입 후 달라지는 작업

CI는 코드를 올릴 때 테스트·빌드를 자동 실행하는 단계다. CD는 그 검증된 파일을 운영 서버에 적용하는 단계다. ZeroVerse는 **CI 자동 실행 + 운영자가 선택·승인하는 CD**로 구성한다.

```text
feature PR → dev 통합 → main PR/통합
                           ↓
                    테스트·빌드 성공
                           ↓
                 변경 불가능한 패키지 보관
                           ↓
              운영자가 배포 대상 선택 + 승인
                           ↓
          Lightsail이 요청과 패키지를 확인해 다운로드
                           ↓
          점검 화면 → 백업 → 버전 교체 → 상태 확인
                           ↓
                GitHub에 실제 성공/실패 보고
```

서버는 GitHub로 나가는 HTTPS 연결을 사용한다. 기존 SSH 접근 제한은 유지한다. 단일 Lightsail이므로 배포 중 짧은 점검 시간이 생긴다. 초기 버전은 DB 구조·마이그레이션 코드가 바뀌지 않는 릴리스만 처리한다.

## 2. 구현 순서

| 단계 | 작업 | 다음 단계로 넘어가는 증거 |
|---|---|---|
| T1 | PR·dev/main CI, 필수 `ci-gate` | 실제 PR에서 성공 및 실패 차단 확인 |
| T2 | 동일 빌드 패키징, SHA·migration 정보 기록 | 저장된 artifact와 테스트한 JAR/화면 파일 일치 |
| T3 | 수동 배포 요청과 production 승인 | 승인 전 요청0, 승인 후 요청/증거1 |
| T4 | 서버 pull·출처 검증·결과 기록 | 위조·중복·만료 요청 거부, dry-run 서비스 변경0 |
| T5 | 백업·교체·복구 및 공용 잠금 | 격리 Linux에서 실패 복구·중복 실행 방지 확인 |
| T6 | 독립 QA·비활성 설치 준비 | 테스트 출력과 설치할 파일/권한 검토 완료 |
| T7 | 실제 설정·최초 배포·자동 조회 활성화 | 운영 파일 해시·HTTPS·Deployment success 일치 |

T1~T2가 끝나면 CD를 기다리지 않고 CI를 사용할 수 있다. T7은 구현 결과를 검토하고 실제 운영 적용 지시를 받은 뒤 진행한다.

## 3. 처음 한 번 설정할 항목

아래는 **구현 후 적용할 체크리스트**다. 아직 없는 workflow를 필수 검사로 지정하면 PR이 막힐 수 있으므로 먼저 CI를 한 번 성공시킨다.

### GitHub 저장소

대상: 공개 저장소 `0-virus/zero-verse`, repository ID `1253062454`.

1. `Settings → Actions → General`에서 Actions 사용을 확인한다. 기본 workflow 권한은 읽기로 두고, 요청 job에만 필요한 쓰기 권한을 명시한다.
2. `Settings → Rules → Rulesets`에서 main/dev의 PR·성공한 `ci-gate`를 요구하고 force push·브랜치 삭제를 제한한다. 적용 전 현재 main/dev 차이를 확인한다.
3. `Settings → Environments`에 `production`을 만든다. 배포 branch는 main만, required reviewer는 `0-virus`, 관리자 우회는 비허용으로 설정한다.
4. 현재 1인 운영이므로 `Prevent self-review`는 끈다. 이는 본인의 배포 요청을 직접 확인하는 절차다. 독립된 다른 사람의 승인이라고 기록하지 않는다. PR required approvals도 혼자서는 충족할 수 없는 값으로 설정하지 않는다.

`production` gate를 쓰되 자동 Deployment 생성을 끄고 실제 서버 결과를 별도로 보고한다. 이 조합은 도입 때 실제 검증한다. [GitHub 공식 환경/배포 제어 안내](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/control-deployments)

### 서버용 GitHub App

구현 결과가 준비되면 GitHub의 `Settings → Developer settings → GitHub Apps`에서 전용 App을 만들고 **이 저장소만** 선택해 설치한다.

| Repository permission | 값 | 목적 |
|---|---|---|
| Metadata | Read | 저장소 식별 |
| Actions | Read | 성공한 실행·승인 증거·artifact 조회 |
| Contents | Read | 신뢰하는 workflow/helper 코드 확인 |
| Deployments | Read and write | 실제 운영 반영 결과 보고 |

그 외 권한은 추가하지 않는다. 이 설계는 polling이므로 webhook 이벤트 수신은 필요 없다. 서버에는 App ID, installation ID와 private key가 필요하다. private key는 `/etc/zeroverse-deploy/`의 root 전용 파일로 설치하며 채팅·Git·Actions secret에 넣지 않는다. DB 비밀번호·AWS 키·JWT 키·SSH 키도 CI로 옮기지 않는다.

App key를 가진 root broker가 저장소와 권한을 제한한 단기 token을 발급한다. 비특권 puller에는 읽기 token만, 결과 쓰기는 root journal을 읽는 reporter만 사용한다. [GitHub App 설치 인증 안내](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/authenticating-as-a-github-app-installation)

### 서버 초기화

- timer는 비활성, 설정은 `enabled=false`로 설치한다. 다운로드된 패키지 안의 운영 스크립트를 자동 실행하지 않는다.
- repo/workflow/environment/승인자 ID와 신뢰하는 workflow/helper의 blob SHA를 고정한다. 이 파일들이 바뀌면 운영자가 검토 후 허용값을 갱신한다.
- current release·JAR·SQL/Java migration·DB history를 baseline으로 기록한다. 현재 format1 운영 패키지는 보존하고 새로운 자동 배포 후보에만 format2를 요구한다.
- 자동 배포용 systemd drop-in은 앱의 Flyway 실행을 끄고 Hibernate schema validate를 유지한다. 실제 적용 전 기존 운영 release도 이 설정으로 기동하는지 격리 검증한다.
- 백업·배포는 같은 maintenance lock을 쓰도록 정기/수동 백업 진입점을 맞춘다. 기존 `backup-db.sh` 직접 호출은 이 잠금을 우회하므로 도입 후 wrapper를 사용한다.
- 최초 활성화 전에 서버 밖 암호화 백업 사본의 해시와 Mac의 복호화 키 접근을 확인하고 별도 DB 복원 증거를 남긴다. 이후 매 배포 백업은 서버에 저장한다. **외부 자동 복제는 이 계획에 포함되지 않아 서버 유실 대비가 완성된 것은 아니다.**
- 승인 요청 하나를 dry-run으로 확인한다. 이때 current/서비스/DB 변경은 없어야 한다. 합의한 점검 시간에 최초 적용을 확인한 뒤 60초 timer를 활성화한다. 초기화 이전 요청을 몰아서 실행하지 않는다.

## 4. 평소 배포하는 방법 — 구현 후

1. feature PR의 CI를 통과시켜 dev로 통합하고, 릴리스할 변경을 main PR로 통합한다.
2. Actions에서 **main push로 실행된 성공한 CI**를 고른다. commit SHA, build run ID/attempt, artifact ID를 확인한다. 현재 서버 release ID는 마지막 실제 배포 receipt와 서버 current를 대조해 확인한다.
3. `request-deploy`의 `Run workflow`에서 main을 선택하고 위 값 및 `expected_current_release`를 입력한다. 현재 버전이 예상과 다르면 배포는 거부된다.
4. `production` 승인 화면에서 변경 내용·대상 SHA·현재 버전·DB 변경 없음·점검 시간을 확인하고 승인한다. DB 변경이 있으면 이 경로를 사용하지 않는다.
5. 요청 workflow가 녹색이어도 기다린다. 그것은 **요청 제출 완료**다. 실제 Deployment가 `success`인지, `https://zeroverse.co.kr`와 로그인 화면·정적 파일이 정상인지 확인한다.

실패한 요청을 `Re-run jobs`로 반복하지 않는다. 원인을 해결한 뒤 정확한 현재 버전으로 새 요청을 만든다. artifact는30일 보관 제안이며 만료 시 새 CI와 새 승인을 거친다. `latest` 이름만 보고 다른 파일로 바꾸지 않는다.

## 5. 상태별 확인과 조치

| 상태 | 확인할 증거 | 운영자 조치 |
|---|---|---|
| CI 실패 | test/build 로그, ci-gate failure | 코드를 수정하고 CI 재검증; 서버는 그대로 |
| 승인 대기 | production review 화면 | 변경과 대상을 검토해 승인/거절 |
| 요청 제출 완료 | 요청 run success + 요청 artifact + Deployment ID | 실제 서버 결과를 기다림 |
| 적용 준비/대기 | timer·조회 로그, current, 요청 유효기간 | 활성화 상태/잠금/GitHub 접근 확인; 중복 요청 금지 |
| 적용 중 | durable journal APPLYING, GitHub in_progress, 점검503 | 진행 중 작업을 강제로 끊지 않음 |
| 적용 성공 | SUCCEEDED receipt·current/hash·HTTPS·Deployment success | 해당 버전이 실제 운영 중임을 기록 |
| 실패 후 복구 | FAILED_RECOVERED, previous-good readiness | 이전 버전 서비스 확인; 실패 원인 해결 후 새 요청 |
| 결과 보고 실패 | REPORT_PENDING, 정상 current/readiness | GitHub 연결 복구 후 결과만 재전송; 앱 재배포 금지 |
| 수동 개입 필요 | MANUAL_INTERVENTION 또는 실제 상태 불명 | timer 중단, 로그/포인터/DB history 대조 후 복구 판단 |

조회는60초 주기, 요청은24시간 뒤 만료한다. 준비 단계120초, 기존 앱 종료45초, 백업300초, 후보 readiness120초가 기본 상한 제안이다. 롤백·GitHub 장애 시간을 포함한 전체 배포 완료 시간을 보장하는 값은 아니다. 요청 제출 뒤2분 이상 아무 기록이 없으면 timer와 잠금을 읽기 전용으로 확인한다. 실제 서비스가 정상이라면 GitHub 표시 지연만으로 재배포하지 않는다.

운영 책임자는 저장소 소유자이고 root/QA는 증거 수집·대조를 지원한다. 세부 journal에는 phase 시작/완료, 현재/이전 release, backup hash, DB history fingerprint와 결과를 비밀 없이 남긴다.

## 6. 실패 복구·유지 관리 원칙

- 새 버전이 실패하면 DB history가 바뀌지 않았고 검증된 이전 정상 버전이 있을 때만 포인터를 복구한다. 이전 앱의 readiness까지 확인해야 복구 성공이다.
- DB history 변경·복구 실패·전원 종료로 상태가 모호하면 점검 화면을 유지하고 수동 확인한다. DB dump 자동 복원이나 pre-S3 버전 복귀는 하지 않는다.
- timer 중단은 진행 중 oneshot 취소가 아니다. 먼저 journal과 서비스 상태를 읽는다. 강제 종료 뒤 자동 재시도하지 않는다.
- DB migration 릴리스는 timer 중단 → 별도 백업/복원 검증 → 검토한 수동 migration/배포 → history·앱 검증 → 새 baseline 확정 순으로 별도 계획한다. 초기 자동 경로의 제한을 임의로 풀지 않는다.
- 매일04:00 KST 정기 백업/7일 보관은 유지하고 배포 전·수동 백업은 자동 삭제에서 제외한다. 서버의 현재/직전 정상 release는 보존한다. 첫 버전은 release 자동 삭제를 하지 않는다.
- 키 교체는 새 App key 설치·읽기/상태 보고 검증 후 기존 키를 폐기한다. 배포 App 문제 해결을 위해 앱의 S3 자격증명이나 DB 비밀번호를 바꾸지 않는다.

## 7. 최초 도입 완료 기준

CI 성공뿐 아니라 승인 거절/위조 요청/중복/만료 거부, 백업 잠금 충돌, 앱·정적 파일 실패 복구, 결과 보고만 재시도, 실제 운영 current/hash/HTTPS/Deployment 일치까지 증거가 있어야 한다. 장애 주입은 격리 환경에서 수행하며 운영 데이터에는 실행하지 않는다.

현재 이 기준은 **미실행**이다. 계획 문서의 T1~T7에 구현·독립 QA·운영 실제 증거를 각각 기록한다.
