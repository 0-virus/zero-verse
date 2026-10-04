# ZeroVerse

Spring Boot API와 React 웹 클라이언트로 구성된 블로그 프로젝트입니다.
아래는 **내 PC에서만 사용하는 테스트·개발 환경**의 실행 방법입니다. 운영 배포용 설정이 아닙니다.
현재 구현·검증 범위는 [마일스톤 기록](docs/worklog/)에서 확인하세요.

## 준비물

- JDK **21** — `JAVA_HOME`과 `java`가 같은 JDK를 가리켜야 합니다. Gradle은 저장소의 wrapper를 사용합니다.
- Docker Desktop — Linux containers 모드로 실행합니다. MySQL 컨테이너와 백엔드 통합 테스트에 필요합니다.
- 웹 화면까지 확인하려면 Node.js **22.13 이상인 22.x**와 npm이 필요합니다. 이 작업 환경에서는 Node 22.21.1 / npm 10.9.4를 사용했습니다.
- Windows PowerShell. 아래 명령은 별도 표시가 없으면 **저장소 루트**에서 실행합니다.

```powershell
java -version
docker version
node --version
npm.cmd --version
```

API만 확인한다면 Node/npm과 프론트 실행은 생략할 수 있습니다.
현재 로컬 인증·블로그·카테고리·글·이미지 기능에는 LocalStack/S3가 필요하지 않습니다.

### M4 로컬 이미지 저장

2026-10-03 승인된 M4는 LocalStack 없이 서버 로컬 파일 업로드를 사용합니다. 글·이미지 권한과 최대 5MB(5,242,880바이트)를 유지하며 S3 연동은 후속 범위입니다. 구현·인수 결과는 [M4 기록](docs/worklog/M4-posts.md)을 확인하세요. 아래 U0 설명은 과거 별도 검증 환경이며 현재 M4 실행에 필요하지 않습니다.

로컬 이미지 기본 경로는 저장소 루트의 `.local-data/uploads`입니다. 이 경로는 Git과 Gradle `clean` 대상에서 제외됩니다. 서버를 항상 같은 작업 디렉터리에서 시작하거나 `zeroverse.upload.directory`에 고정된 절대 경로를 설정하세요. DB에는 이미지 참조·소유·연결 정보가 있으므로 백업/복원 시 DB와 파일 폴더를 함께 보존해야 합니다. 폴더만 이동·삭제하거나 DB만 되돌리지 마세요. 미연결 업로드의 자동 파일 삭제는 제공하지 않으며 운영 정리는 별도입니다.

저장된 파일 폴더를 정적 웹 디렉터리로 공개하지 마세요. 이미지 내용은 권한을 검사하는 `/api/v1/uploads/{id}/content`를 통해서만 제공합니다. S3 이전은 별도 구현·검증이 필요하고 로컬 통과를 AWS 정책 검증으로 간주하지 않습니다.

로컬 업로드/이미지 읽기 컨트롤러는 `local` 또는 `test` 프로필에서만 활성화됩니다. 아래 기동 명령의 `--spring.profiles.active=local`을 유지하세요. 이 프로필을 운영 배포용 이미지 제공 설정으로 사용하지 마세요.

조회수 중복 방지는 `VIEWER_HMAC_SECRET`(최소 32 UTF-8 바이트)을 사용합니다. IP/User-Agent 원문을 저장하지 않고 HMAC으로 구분하며, 키가 없거나 짧으면 기동에 실패합니다. 같은 키를 유지하면 재시작 전후 24시간 중복 방지 기준이 이어집니다. 키를 교체하면 익명 방문자 식별 기준이 바뀌어 같은 방문도 다시 집계될 수 있습니다. 키를 Git·로그에 남기지 마세요.

### LocalStack — M4 준비용, 제품 연결 전

현재 PC는 Windows 예약 범위 `4474–4573` 때문에 `lstk 1.0.0`의 기본 서비스 포트와 `4566`을 열 수 없습니다.
[compose.localstack.yml](compose.localstack.yml)은 사용자가 받은 LocalStack **2026.8.1** 이미지의 digest를 고정하고,
**`127.0.0.1:14566 → 4566`만** 연결합니다. Windows 예약·방화벽·기존 MySQL은 변경하지 않습니다.
S3만 활성화하며 Docker socket은 공유하지 않습니다. `lstk start`는 이 Compose 설정을 사용하지 않습니다.

이미 생성된 컨테이너의 시작·확인·종료:

```powershell
docker start zeroverse-localstack
(Invoke-RestMethod 'http://127.0.0.1:14566/_localstack/health').services.s3
docker stop zeroverse-localstack
```

최초 생성이나 설정 적용을 위해 재생성할 때만 아래를 사용합니다. `lstk`의 시스템 저장소 로그인은 Compose에 자동 전달되지 않습니다.
토큰은 LocalStack 계정의 Auth Tokens에서 확인하여 **아래 로컬 입력창에만** 넣으세요. 채팅·파일·Git에 남기지 마세요.
이미지 자동 다운로드는 막아 두었으므로 다른 PC에서는 Compose에 고정된 이미지를 먼저 준비해야 합니다.

```powershell
$localstackPreviousToken = $env:LOCALSTACK_AUTH_TOKEN
try {
    $env:LOCALSTACK_AUTH_TOKEN = [System.Net.NetworkCredential]::new('', (Read-Host 'LocalStack Auth Token' -AsSecureString)).Password
    docker compose -f compose.localstack.yml up --detach --wait
} finally {
    $env:LOCALSTACK_AUTH_TOKEN = $localstackPreviousToken
    $localstackPreviousToken = $null
}
```

Docker 컨테이너 환경에는 실행용 토큰이 들어 있으므로 전체 `docker inspect`/Compose 설정 출력을 공유하지 마세요.
현재는 **기동 확인용 임시 환경**이며 데이터 영속성은 설정하지 않았습니다. 재시작·재생성 시 테스트 데이터 보존을 기대하지 마세요.
health 성공은 이미지 업로드/권한 검증 U0 통과가 아닙니다. 제품 코드·DB·실제 AWS 연결은 아직 변경하지 않았습니다.

### U0 업로드 검증 — 제품 서버와 별도

LocalStack이 실행 중일 때 저장소 루트에서 실행합니다. 검증용 SDK와 별도 실행 설정은 `gradle/u0`에 있으며(Tika는 승인된 M4 제품 MIME 검사에도 사용),
매 실행마다 고유 테스트 버킷과 합성 이미지가 생성됩니다. 기존 버킷은 삭제하지 않습니다.

```powershell
.\gradlew.bat --gradle-user-home .gradle-home2 --no-daemon --max-workers=1 -p gradle/u0 run
```

`http://127.0.0.1:14567`을 열어 **Run U0를 한 번만** 누릅니다. 다시 시험하려면 이 도구만 `Ctrl+C`로 종료하고 새로 실행하세요.
같은 프로세스의 URL·객체를 재사용하면 덮어쓰기 거부·만료로 결과가 달라집니다.
브라우저 없는 자체 검사는 위 명령 끝에 `--args=--self-check`를 붙이며, 실제 브라우저 시험을 대체하지 않습니다.

2026-09-09 실측은 **U0 실패**입니다. 브라우저 요청 18/19·서버 검사 27/28이 통과했지만,
무서명 private GET이 `403` 대신 `200`이었습니다. 현재 IAM enforcement는 비활성이며,
기존 라이선스의 오프라인 검증에서도 이 기능은 허용되지 않았습니다. 재생성·재시험은 보류하고 테스트 데이터를 보존합니다.
이 환경에 개인 이미지·실제 데이터를 올리지 마세요. 상세 결과와 재개 조건은 [M4 기록](docs/worklog/M4-posts.md)을 따릅니다.

### SeaweedFS U0 대체 검증 — 2026-09-23 승인

이 PC에는 공식 SeaweedFS native `4.47`을 `build/u0-seaweedfs-4.47/`에 준비했습니다(Git 제외).
공식 `windows_amd64.zip` SHA-256은 `8809359079e62fcd60574ff661449160899622c52072f3f569d346669079efe9`입니다.
이 폴더의 `s3.json`에는 U0 합성 identity만 있고 익명 identity는 없습니다. 계정 가입·결제·Docker가 필요하지 않습니다.

검증용 저장소를 다시 시작할 때는 먼저 같은 프로세스가 실행 중인지 확인한 뒤 별도 터미널에서 실행합니다.

```powershell
Set-Location build/u0-seaweedfs-4.47
.\weed.exe server -master -volume -filer -s3 -ip=127.0.0.1 -ip.bind=127.0.0.1 -s3.ip.bind=127.0.0.1 -dir=data -volume.port=9340 -s3.port=14568 -s3.config=s3.json -s3.allowedOrigins=http://127.0.0.1:14567 -s3.port.iceberg=0 -s3.port.lance=0 -master.telemetry=false -volume.max=24 -master.volumeSizeLimitMB=128 -s3.allowDeleteBucketNotEmpty=false -s3.autoCreateBucket=false
```

`mini -admin.ui=false`도 이 버전에서 관리 gRPC `::33646`을 열었으므로 `server`를 사용합니다.
리더 실측에서는 `server`의 8개 listener가 모두 `127.0.0.1`이었습니다. S3 endpoint는 `14568`이며 기존 LocalStack `14566`과 분리됩니다.

저장소 루트의 다른 터미널에서 검증기를 실행합니다. 두 명령은 동시에 실행하지 않습니다.

```powershell
# 실제 브라우저: http://127.0.0.1:14567 에서 Run U0를 한 번 실행
.\gradlew.bat --gradle-user-home .gradle-home2 --no-daemon --max-workers=1 -p gradle/u0 run --args=--seaweedfs
# 브라우저 없는 별도 새 프로세스 검사
.\gradlew.bat --gradle-user-home .gradle-home2 --no-daemon --max-workers=1 -p gradle/u0 run --args="--seaweedfs --self-check"
```

실행마다 고유 합성 버킷을 생성하고 삭제하지 않습니다. 종료는 각 터미널의 `Ctrl+C`이며 데이터 폴더는 보존합니다.
최신 결과는 [M4 기록](docs/worklog/M4-posts.md)을 따릅니다. 로컬 U0 통과도 제품 U1 완료나 AWS IAM 정책 동등성의 증거는 아닙니다.

2026-09-23 실측: Java 요청19/19·서버검사27/28, 실제브라우저 요청18/19·서버검사27/28.
Public Access Block은 HTTP501 미지원이고, 브라우저 초과크기 요청은 HTTP코드 대신 fetch TypeError/CORS였다.
무서명GET403과 초과객체미생성은 확인했지만 전체U0는 BLOCKED다. 임시서버는 종료했고 합성데이터는 보존했다.

후속 진단에서도 두 문제는 미해결입니다. PAB는 공식4.47 handler 자체가 미구현이며,
초과 PUT은 원시 HTTP에서 조기403·정확한 CORS 헤더·Connection close가 확인됐지만 브라우저 fetch와 XHR 모두 응답을 읽지 못했습니다.
합성 버킷은 총6개 보존했습니다. 마지막 진단에서 volume.max를16→24로 늘렸고 현재 data의 볼륨 파일은23개입니다.
새 버킷 재시험 전 남은 볼륨 슬롯과 디스크 용량을 확인해야 하며, 데이터 삭제로 공간을 자동 확보하지 않습니다.
위 명령은 마지막 진단 설정이며 두 실패를 해결하는 설정이 아닙니다. 변화 없는 동일 재시험은 하지 않습니다.

## 1. 테스트용 MySQL 준비

이미 사용할 **로컬 테스트 DB**가 있다면 새로 만들지 말고 2단계에서 해당 주소·계정을 사용하세요.
운영 DB나 보존이 필요한 공유 DB는 연결하지 마세요. 서버 기동 시 Flyway가 스키마를 변경합니다.

새 DB가 필요한 경우에만 아래를 한 번 실행합니다. 기존 smoke DB와의 충돌을 줄이기 위해 호스트 포트는 `13307`을 사용합니다.

```powershell
# 입력값은 화면과 명령 기록에 노출하지 않습니다. 재실행 시 사용할 DB 비밀번호를 안전하게 보관하세요.
$env:MYSQL_ROOT_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '새 로컬 MySQL root 비밀번호' -AsSecureString)).Password
$env:MYSQL_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '새 로컬 zeroverse 계정 비밀번호' -AsSecureString)).Password

docker run --name zeroverse-local-test --detach `
  --publish 127.0.0.1:13307:3306 `
  --env MYSQL_DATABASE=zeroverse --env MYSQL_USER=zeroverse `
  --env MYSQL_ROOT_PASSWORD --env MYSQL_PASSWORD `
  --mount type=volume,source=zeroverse-local-test-data,target=/var/lib/mysql `
  mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
```

비밀번호는 빈 값으로 입력하지 마세요. 첫 실행은 이미지 다운로드와 DB 초기화에 시간이 걸립니다.
준비 상태를 확인하고 `mysqld is alive`가 나올 때까지 기다립니다.

```powershell
docker exec zeroverse-local-test sh -c 'MYSQL_PWD=$MYSQL_PASSWORD mysqladmin ping -uzeroverse --host=127.0.0.1 --silent'
```

다음 실행부터는 `docker run`을 반복하지 않고 `docker start zeroverse-local-test`를 사용합니다.
기존 volume의 DB 비밀번호는 `docker run` 환경변수만 바꿔도 변경되지 않습니다.
Docker 컨테이너 환경에는 비밀번호가 보관되므로 `docker inspect` 결과를 공유하지 마세요.

## 2. 로컬 설정 파일 준비

아직 파일이 없을 때만 예제를 복사합니다. 기존 개인 설정은 덮어쓰지 않습니다.

```powershell
if (-not (Test-Path -LiteralPath 'src/main/resources/application-local.yml')) {
    Copy-Item -LiteralPath 'src/main/resources/application-local.example.yml' -Destination 'src/main/resources/application-local.yml'
}
```

`src/main/resources/application-local.yml`을 편집해 아래 항목을 맞춥니다.
기존에 다른 설정이 있다면 해당 항목만 수정하세요. `${...}`는 실제 값을 적는 칸이 아니라 **그대로 남겨 둘 환경변수 참조**입니다.

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:13307/zeroverse?serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false
    username: zeroverse
    password: ${DB_PASSWORD}

zeroverse:
  jwt:
    secret-base64: ${JWT_SECRET_BASE64}
  upload:
    viewer-hmac-secret: ${VIEWER_HMAC_SECRET}
    directory: .local-data/uploads
  cors:
    allowed-origins: http://localhost:5173
  auth:
    cookie:
      secure: false
      same-site: Strict
      allowed-origins:
        - http://localhost:5173
```

기존 DB를 쓰면 URL의 포트·DB 이름·계정을 변경합니다. `useSSL=false`, `allowPublicKeyRetrieval=true`, `secure: false`는 이 문서의 **loopback 로컬 환경에만** 적용하세요.
이 파일은 Git에서 제외됩니다. 비밀번호·JWT 키가 포함된 설정이나 로그를 커밋·공유하지 마세요.
예제의 `<...>` 문구를 그대로 남기면 DB 연결 또는 JWT 초기화가 실패합니다.

## 3. 백엔드 실행

루트의 **같은 PowerShell 창**에서 비밀번호를 입력하고 서명 키를 생성한 뒤 실행합니다.
DB 비밀번호는 1단계에서 정한 **zeroverse 계정 비밀번호**이며 root 비밀번호가 아닙니다.
이 PC의 2026-10-03 검증에서는 Windows 예약 범위 `8010–8109`에 8080이 포함돼 API를 **18080**에서 실행합니다. OS 예약·방화벽을 변경하지 않으며 프론트 API 주소도 같은 포트로 맞춥니다.

```powershell
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '로컬 zeroverse DB 비밀번호' -AsSecureString)).Password

# 32바이트 랜덤 키를 Base64로 인코딩합니다. 키 자체는 출력하지 않습니다.
$zeroverseKeyBytes = New-Object byte[] 32
$zeroverseRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$zeroverseRng.GetBytes($zeroverseKeyBytes)
$zeroverseRng.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($zeroverseKeyBytes)

# 같은 터미널에서 재시작할 때 조회수 식별 키를 유지합니다.
if (-not $env:VIEWER_HMAC_SECRET) {
    $zeroverseViewerBytes = New-Object byte[] 32
    $zeroverseViewerRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $zeroverseViewerRng.GetBytes($zeroverseViewerBytes)
    $zeroverseViewerRng.Dispose()
    $env:VIEWER_HMAC_SECRET = [Convert]::ToBase64String($zeroverseViewerBytes)
}

.\gradlew.bat --no-daemon --max-workers=1 bootRun --args="--spring.profiles.active=local --server.address=127.0.0.1 --server.port=18080"
```

`Started ZeroverseServerApplication`이 나오면 준비된 것입니다. 이 창은 실행 중 그대로 둡니다.
Flyway가 필요한 migration을 적용하고 Hibernate가 스키마를 검증합니다. 적용한 V1/V2/V3 SQL을 수동으로 반복 실행하거나 기존 migration을 수정하지 마세요.

위 환경변수는 현재 터미널과 그 자식 프로세스에만 적용됩니다. 새 창에서는 다시 설정해야 합니다.
키를 새로 생성하면 이전 로그인 토큰은 유효하지 않으므로 브라우저에서 다시 로그인하세요.

### JAR로 실행하고 싶다면

`bootRun` 대신 같은 환경변수가 설정된 창에서 아래를 실행합니다. 두 방식을 동시에 실행하지 마세요.

```powershell
.\gradlew.bat --no-daemon --max-workers=1 bootJar
if ($LASTEXITCODE -eq 0) {
    java -jar build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18080
}
```

`bootJar`는 실행 파일을 만드는 명령이며 전체 테스트 통과를 뜻하지 않습니다.
로컬 설정 파일도 JAR에 포함될 수 있으므로 이 로컬 빌드 산출물을 배포·공유하지 마세요.

## 4. 웹 화면 실행

**다른 PowerShell 창**을 열고 저장소 루트에서 실행합니다.

```powershell
Set-Location frontend
npm.cmd ci
$env:VITE_API_BASE_URL = 'http://localhost:18080'
npm.cmd run dev -- --host localhost --port 5173 --strictPort
```

의존성을 이미 설치했고 `package-lock.json`이 바뀌지 않았다면 `npm.cmd ci`는 생략할 수 있습니다.
API 주소에는 `/api/v1`을 붙이지 않습니다. 클라이언트가 각 요청 경로에 붙입니다.
브라우저의 프론트와 API 호스트는 모두 `localhost`로 통일하세요. 한쪽만 `127.0.0.1`로 바꾸면 SameSite 쿠키 기반 세션 복구가 실패할 수 있습니다.

| 용도 | 주소 |
| --- | --- |
| 회원가입 / 로그인 | http://localhost:5173/signup / http://localhost:5173/signin |
| 프로필·블로그 설정 / 카테고리 관리 | http://localhost:5173/settings / http://localhost:5173/settings/posts |
| Swagger UI | http://localhost:18080/swagger-ui.html |
| OpenAPI JSON | http://localhost:18080/v3/api-docs |

기본 테스트 계정은 제공하지 않습니다. 회원가입 → 초기 블로그 설정 → 카테고리 관리 순서로 확인하세요.
화면은 데스크톱 전용이며 브라우저 표시 영역 가로 **1440px 이상**에서 확인합니다.
글 작성은 `/write`, 임시저장·글 관리는 `/settings/posts`에서 확인합니다. M4의 최신 검증 결과는 [M4 기록](docs/worklog/M4-posts.md)을 따르며 관계 관리·댓글·좋아요 등 M5 이후 기능은 이번 범위에 포함하지 않습니다.

## 5. 동작 확인과 자동 테스트

실행 중인 API의 문서 응답을 별도 터미널에서 확인할 수 있습니다.

```powershell
(Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18080/v3/api-docs').StatusCode
```

기대 결과는 `200`입니다. 루트 `/`나 인증 없이 `/api/v1/auth/me`에 접근했을 때의 `401`은 기동 실패가 아닙니다.

### 백엔드 전체 테스트·빌드

서버를 띄우는 `local` 프로파일과 자동 테스트의 `test` 프로파일은 다릅니다.
아래 테스트는 Docker의 **별도 Testcontainers MySQL 8.4**를 사용하므로 위 수동 서버/DB가 실행 중일 필요는 없습니다.
테스트 설정을 운영 DB로 바꾸거나 DB 오류를 피하려고 테스트를 건너뛰지 마세요.

```powershell
.\gradlew.bat --no-daemon --max-workers=1 build bootJar
```

성공 기준은 `BUILD SUCCESSFUL`과 종료 코드 `0`입니다. 결과는 `build/reports/tests/test/index.html`에 생성됩니다.

### 프론트 테스트·빌드

```powershell
Set-Location frontend
npm.cmd test
npm.cmd run lint
npm.cmd run build
```

### 실행 중인 서버에 M3 API smoke 실행

**합성 계정 2개와 블로그·카테고리 데이터를 생성·변경하며 검증 데이터를 남깁니다.** 개인 테스트 DB에만 실행하세요.
루트에서 실행하며, 실제 화면 검증이나 전체 통합 테스트를 대체하지 않습니다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\qa\m3-api-smoke.ps1 -BaseUri http://127.0.0.1:18080
```

### M4 글·로컬 이미지 인수

[M4 API smoke](qa/m4-api-smoke.ps1)는 기존 합성 계정 4개(작성자·정방향 친구·역방향 친구·무관계 사용자)와 관계 fixture를 입력받아 실행합니다. 계정이나 관계를 자동 생성하지 않으며 개인 테스트 DB에서만 사용하세요. 비밀번호는 명령 기록에 직접 적지 말고 메모리 변수로 전달하고, `-BaseUri http://127.0.0.1:18080`을 명시합니다. 검증용 글·이미지를 생성하고 그중 private 글 1개를 soft delete하며 파일은 보존합니다.

전체 인수 항목과 실행 결과는 [M4 QA](qa/M4-review.md) 및 [M4 worklog](docs/worklog/M4-posts.md)를 따릅니다. 2026-10-03 사용자 결정으로 브라우저 파일 선택·전송은 API·자동 테스트로 대체합니다. 이미지 실제 표시와 재시작 후 DB·파일 보존 검증은 별개로 유지합니다.

## 종료·문제 해결

- 백엔드와 프론트는 각각 실행한 터미널에서 `Ctrl+C`로 종료합니다.
- 위에서 만든 DB만 중지하려면 `docker stop zeroverse-local-test`를 사용합니다. 컨테이너·volume은 남아 다음 `docker start` 때 데이터를 재사용합니다. `docker rm -v`나 volume 삭제는 필요하지 않습니다.
- `18080`, `5173`, `13307`이 이미 사용 중이면 기존 실행을 확인하세요. 리스너가 없어도 Windows의 `netsh interface ipv4 show excludedportrange protocol=tcp`에 포함된 포트는 사용할 수 없습니다. 다른 프로젝트 프로세스를 무작정 종료하거나 예약 범위를 바꾸지 마세요. `--strictPort`는 프론트가 다른 포트로 자동 이동해 Origin 설정과 어긋나는 것을 막습니다.
- DB 연결 실패: 컨테이너 준비 상태, URL의 포트·DB 이름, 계정 비밀번호를 확인합니다. `Public Key Retrieval is not allowed`는 위 loopback 전용 JDBC URL과 일치하는지 확인합니다.
- JWT 초기화 실패: 같은 터미널의 `JWT_SECRET_BASE64` 설정과 로컬 YAML의 `${JWT_SECRET_BASE64}` 참조를 확인합니다. Base64 디코딩 후 32바이트 이상이어야 합니다.
- 로그인 후 새로고침/로그아웃 실패: `local` 프로파일, `secure: false`, FE/API의 `localhost` 일치, CORS와 auth Origin의 `http://localhost:5173`을 확인합니다. SameSite를 임의로 완화하지 마세요.
- PowerShell에서 npm 실행 정책 오류: `npm` 대신 이 문서처럼 `npm.cmd`를 사용합니다.
- Gradle 캐시 권한/잠금 문제: 다른 실행을 먼저 확인하고, 필요하면 Gradle 명령에 `--gradle-user-home .gradle-home2`를 추가합니다. 캐시나 DB를 삭제하는 명령이 아닙니다.

설정 정본은 [application.yml](src/main/resources/application.yml), [로컬 예제](src/main/resources/application-local.example.yml), 테스트 범위는 [M3 QA](qa/M3-review.md)와 [M4 QA](qa/M4-review.md)를 참고하세요.
