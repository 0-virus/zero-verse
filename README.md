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
현재 인증·블로그 설정·카테고리 확인에는 LocalStack/S3가 필요하지 않습니다.

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

```powershell
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host '로컬 zeroverse DB 비밀번호' -AsSecureString)).Password

# 32바이트 랜덤 키를 Base64로 인코딩합니다. 키 자체는 출력하지 않습니다.
$zeroverseKeyBytes = New-Object byte[] 32
$zeroverseRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$zeroverseRng.GetBytes($zeroverseKeyBytes)
$zeroverseRng.Dispose()
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($zeroverseKeyBytes)

.\gradlew.bat --no-daemon --max-workers=1 bootRun --args="--spring.profiles.active=local --server.address=127.0.0.1 --server.port=8080"
```

`Started ZeroverseServerApplication`이 나오면 준비된 것입니다. 이 창은 실행 중 그대로 둡니다.
Flyway가 필요한 migration을 적용하고 Hibernate가 스키마를 검증합니다. V1/V2 SQL을 수동으로 반복 실행하거나 기존 migration을 수정하지 마세요.

위 환경변수는 현재 터미널과 그 자식 프로세스에만 적용됩니다. 새 창에서는 다시 설정해야 합니다.
키를 새로 생성하면 이전 로그인 토큰은 유효하지 않으므로 브라우저에서 다시 로그인하세요.

### JAR로 실행하고 싶다면

`bootRun` 대신 같은 환경변수가 설정된 창에서 아래를 실행합니다. 두 방식을 동시에 실행하지 마세요.

```powershell
.\gradlew.bat --no-daemon --max-workers=1 bootJar
if ($LASTEXITCODE -eq 0) {
    java -jar build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=8080
}
```

`bootJar`는 실행 파일을 만드는 명령이며 전체 테스트 통과를 뜻하지 않습니다.
로컬 설정 파일도 JAR에 포함될 수 있으므로 이 로컬 빌드 산출물을 배포·공유하지 마세요.

## 4. 웹 화면 실행

**다른 PowerShell 창**을 열고 저장소 루트에서 실행합니다.

```powershell
Set-Location frontend
npm.cmd ci
$env:VITE_API_BASE_URL = 'http://localhost:8080'
npm.cmd run dev -- --host localhost --port 5173 --strictPort
```

의존성을 이미 설치했고 `package-lock.json`이 바뀌지 않았다면 `npm.cmd ci`는 생략할 수 있습니다.
API 주소에는 `/api/v1`을 붙이지 않습니다. 클라이언트가 각 요청 경로에 붙입니다.
브라우저의 프론트와 API 호스트는 모두 `localhost`로 통일하세요. 한쪽만 `127.0.0.1`로 바꾸면 SameSite 쿠키 기반 세션 복구가 실패할 수 있습니다.

| 용도 | 주소 |
| --- | --- |
| 회원가입 / 로그인 | http://localhost:5173/signup / http://localhost:5173/signin |
| 프로필·블로그 설정 / 카테고리 관리 | http://localhost:5173/settings / http://localhost:5173/settings/posts |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

기본 테스트 계정은 제공하지 않습니다. 회원가입 → 초기 블로그 설정 → 카테고리 관리 순서로 확인하세요.
화면은 데스크톱 전용이며 브라우저 표시 영역 가로 **1440px 이상**에서 확인합니다.
게시글 작성·이미지 업로드 등 후속 마일스톤 기능은 아직 동작하지 않을 수 있습니다.

## 5. 동작 확인과 자동 테스트

실행 중인 API의 문서 응답을 별도 터미널에서 확인할 수 있습니다.

```powershell
(Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/v3/api-docs').StatusCode
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
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\qa\m3-api-smoke.ps1 -BaseUri http://127.0.0.1:8080
```

## 종료·문제 해결

- 백엔드와 프론트는 각각 실행한 터미널에서 `Ctrl+C`로 종료합니다.
- 위에서 만든 DB만 중지하려면 `docker stop zeroverse-local-test`를 사용합니다. 컨테이너·volume은 남아 다음 `docker start` 때 데이터를 재사용합니다. `docker rm -v`나 volume 삭제는 필요하지 않습니다.
- `8080`, `5173`, `13307`이 이미 사용 중이면 기존 실행을 확인하세요. 다른 프로젝트 프로세스를 무작정 종료하지 마세요. `--strictPort`는 프론트가 다른 포트로 자동 이동해 Origin 설정과 어긋나는 것을 막습니다.
- DB 연결 실패: 컨테이너 준비 상태, URL의 포트·DB 이름, 계정 비밀번호를 확인합니다. `Public Key Retrieval is not allowed`는 위 loopback 전용 JDBC URL과 일치하는지 확인합니다.
- JWT 초기화 실패: 같은 터미널의 `JWT_SECRET_BASE64` 설정과 로컬 YAML의 `${JWT_SECRET_BASE64}` 참조를 확인합니다. Base64 디코딩 후 32바이트 이상이어야 합니다.
- 로그인 후 새로고침/로그아웃 실패: `local` 프로파일, `secure: false`, FE/API의 `localhost` 일치, CORS와 auth Origin의 `http://localhost:5173`을 확인합니다. SameSite를 임의로 완화하지 마세요.
- PowerShell에서 npm 실행 정책 오류: `npm` 대신 이 문서처럼 `npm.cmd`를 사용합니다.
- Gradle 캐시 권한/잠금 문제: 다른 실행을 먼저 확인하고, 필요하면 Gradle 명령에 `--gradle-user-home .gradle-home2`를 추가합니다. 캐시나 DB를 삭제하는 명령이 아닙니다.

설정 정본은 [application.yml](src/main/resources/application.yml), [로컬 예제](src/main/resources/application-local.example.yml), 테스트 범위는 [QA 기록](qa/M3-review.md)을 참고하세요.
