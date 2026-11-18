# 운영 배포 준비

이 디렉터리는 승인된 배포 A-v2의 실행 도구다. **코드 준비·개발 S3 검증·실제 운영 배포는 별도 단계**이며 현재 판정은 [배포 기록](../docs/governance/meetings/X-20261113-deployment.md)과 [QA](../qa/X-deployment-review.md)를 확인한다. 아래 명령은 운영자의 적용 절차이며 이 문서 작성만으로 서버/DB에 실행하지 않는다.

대상은 Lightsail 서울의 Java 21 + Caddy 2 + systemd, 신규 Aiven MySQL, private S3다. 기존 로컬 DB와 `.local-data/`는 자동 이관하지 않는다. 예시 도메인·호스트·비밀값은 실제 값으로 교체한다.

CI/CD 도입 계획과 최초 설정·일상 배포 절차는 [CICD.md](CICD.md)를 참고한다. 현재는 계획 단계이며 해당 workflow/서버 도구는 아직 설치되지 않았다.

## 1. 검증과 release 생성

개발 머신에서 JDK 21, Node 22, Python 3.9 이상과 테스트용 Docker를 사용한다. 저장소 루트에서 실행한다. Gradle 테스트는 격리 MySQL을 생성하므로 사용자 DB를 지정하지 않는다.

```sh
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain test bootJar
npm --prefix frontend ci
npm --prefix frontend test
npm --prefix frontend run lint
npm --prefix frontend run build
python3 -m unittest discover -s deploy -p 'test_*.py' -v
python3 deploy/release.py package \
  --jar build/libs/zeroverse-server-0.0.1-SNAPSHOT.jar \
  --frontend-dist frontend/dist \
  --release-id RELEASE_ID \
  --output build/releases/RELEASE_ID.tar.gz
python3 deploy/release.py verify build/releases/RELEASE_ID.tar.gz
```

JAR 파일명은 `build/libs`의 실제 `bootJar` 출력으로 확인한다. production FE의 기본 API는 same-origin이고 업로드 transport는 S3다. 잘못된 HTTP/localhost API 설정은 build가 거부한다. 개발에서는 기존 local multipart를 유지하며 실제 S3 개발 테스트만 명시적으로 transport를 바꾼다.

패키지는 JAR, FE dist, 명시된 운영 템플릿, 파일별 SHA-256 manifest만 담는다. local 설정/키 저장소·symlink를 거부하고 기존 archive를 덮어쓰지 않는다. manifest의 sourceRevision/sourceDirty는 포장 시 Git 정보이지 테스트 통과 증명이 아니다. 같은 소스/빌드에 대한 QA 로그·배포 승인과 archive 전체 checksum을 함께 보관한다. 서버에서 npm/Gradle build하지 않는다.

archive는 최상위 `RELEASE_ID/` 디렉터리를 포함한다. `/opt/zeroverse/releases/RELEASE_ID/`를 먼저 만든 경우에는 검증 후 `tar --strip-components=1 -xzf ARCHIVE -C /opt/zeroverse/releases/RELEASE_ID`로 해제하여 이중 중첩을 피한다. 기존 release 디렉터리에 덮어쓰지 않는다.

archive 안의 운영 도구는 `ops/`에 있다. 검증기는 신뢰된 저장소의 `deploy/release.py`를 먼저 사용하고, 서버로 전송한 archive의 전체 SHA-256도 전달 전 값과 대조한다. 이 안내의 `docs/`, `qa/` 링크는 소스 저장소를 기준으로 하며 운영 archive에 정본 문서나 QA 자료를 함께 넣지는 않는다.

## 2. 운영 환경과 CA

- `/etc/zeroverse/zeroverse.env`: `zeroverse.env.example`을 복사하고 root 소유·0600. 실제 비밀을 Git, 채팅, 명령행, JAR 또는 `JAVA_TOOL_OPTIONS`에 넣지 않는다.
- `/etc/zeroverse/aiven-truststore.p12`: Aiven이 제공한 해당 서비스 CA를 넣고 root:zeroverse·0640. CA 출처와 DB hostname을 확인한다.
- `/opt/zeroverse/releases/RELEASE_ID/`: 배포 archive를 검증한 뒤 새 디렉터리에만 압축 해제한다. root 소유이고 zeroverse/Caddy는 읽기만 한다.
- `/opt/zeroverse/current`: 현재 release를 가리키는 symlink. 앱과 FE가 같은 release를 사용한다.

CA import는 비밀번호 입력 프롬프트를 사용한다. truststore 비밀번호는 환경 파일의 `DB_SSL_TRUSTSTORE_PASSWORD`에 동일하게 설정한다.

```sh
keytool -importcert -alias aiven-ca -storetype PKCS12 \
  -keystore /etc/zeroverse/aiven-truststore.p12 -file /path/to/aiven-ca.pem
```

`DB_URL`은 실제 Aiven endpoint/port/database를 사용한다. TLS `VERIFY_IDENTITY`와 CA 신뢰·UTC를 유지하고 `useSSL=false`, 인증서 검증 해제 옵션을 추가하지 않는다. 신규 운영 DB에도 Flyway를 처음 실행하기 전 snapshot/dump를 확보한다. V1~V6를 수정하거나 Flyway baseline/repair로 오류를 우회하지 않는다. V7 전환과 LOCAL image row 점검을 통과해야 prod를 시작할 수 있다.

`JWT_SECRET_BASE64`와 `VIEWER_HMAC_SECRET`은 별도로 생성하고 재시작 때 유지한다. 세 origin 변수에는 같은 **HTTPS origin(경로 없이)** 을 넣는다. systemd env 파일은 `${PUBLIC_ORIGIN}` 같은 변수 참조를 확장하지 않는다.

## 3. S3와 CORS

버킷은 `zero-verse-s3`, 서울 `ap-northeast-2`다. public access block과 BucketOwnerEnforced를 유지하고 SSE-S3 및 HTTPS 정책을 확인한다. 앱 identity는 해당 환경 prefix의 GetObject/PutObject/DeleteObject만 사용한다. bucket listing·ACL·backup 권한을 앱에 합치지 않는다. 개발 `zeroverse-s3-dev`의 `zeroverse/dev/` 권한으로 운영 `zeroverse/prod/`를 사용할 수 있다고 가정하지 않는다.

개발 콘솔 CORS 예시는 [s3-cors-development.json](s3-cors-development.json)이다. 기존 규칙을 보존해 새 규칙을 추가한다. 운영에서는 실제 HTTPS origin을 별도 허용하며 `*`로 확대하지 않는다. CORS는 객체 접근 권한을 부여하지 않는다. 만료 URL과 필수 헤더는 제품 API가 발급하며 프론트 Bearer/cookie를 S3에 전달하지 않는다.

만료된 미완료 업로드는 패키지의 관리 명령으로 점검한다. 기본은 dry-run이며 schema 생성·Flyway migration·HTTP listener를 사용하지 않는다. 운영 env 파일을 셸에서 출력하거나 source하지 않고 systemd가 읽게 한다.

```sh
sudo systemd-run --wait --pipe --collect --uid=zeroverse \
  --property=EnvironmentFile=/etc/zeroverse/zeroverse.env \
  --property=WorkingDirectory=/opt/zeroverse/current \
  /usr/bin/java -Duser.timezone=UTC -Xmx384m \
  -Dloader.main=com.zeroverse.tools.UploadIntentCleanup \
  -cp /opt/zeroverse/current/backend/app.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

결과의 후보 수와 대상 환경을 확인한 뒤 같은 명령 끝에 `--upload.cleanup.apply=true`를 붙여 실행한다. 한 번에 최대 100건이며 24시간 intent 만료와 PUT URL 만료를 모두 만족하고 image metadata가 없는 건만 처리한다. READY·바인딩된 이미지·탈착 이미지·LOCAL 데이터는 이 도구의 삭제 대상이 아니다. 실패한 삭제는 DELETING 상태로 남아 다음 실행에서 재시도하며, 자동 스케줄은 설치하지 않는다.

## 4. 서버 배치와 전환

별도 시스템 사용자 `zeroverse`와 Caddy 서비스를 준비한다. 방화벽은 80/443, SSH는 관리자 IP로 제한하고 8080/DB 포트를 공개하지 않는다. `zeroverse.service`를 systemd에 설치한 뒤 env/CA/release 파일 권한을 확인한다. 서비스는 loopback 8080, UTC, JVM heap 768MiB·metaspace256MiB·direct128MiB로 실행한다. 이 값 외의 native memory도 소비하므로 실제 2GB 환경에서 RSS/재시작·동시 이미지 read를 점검한다.

Caddy의 환경에는 `DOMAIN=실제도메인`을 지정하고 Caddyfile을 설치한다. 기본 static root는 `/opt/zeroverse/current/frontend`, upstream은 `127.0.0.1:8080`이다. Caddy의 서비스 환경과 앱의 환경 파일은 별개다. Caddy TLS 인증서 저장소는 Caddy 서비스가 관리하며 release 교체 시 삭제하지 않는다.

```sh
caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
# backup, migration 호환성, 검증된 release 경로를 확인한 후 적용한다.
sudo systemctl stop zeroverse
sudo ln -s /opt/zeroverse/releases/RELEASE_ID /opt/zeroverse/current.next
sudo mv -Tf /opt/zeroverse/current.next /opt/zeroverse/current
sudo systemctl daemon-reload
sudo systemctl start zeroverse
curl --fail --silent --show-error http://127.0.0.1:8080/api/v1/health/readiness
sudo systemctl reload caddy
```

`current.next`가 이미 있으면 원인을 확인하고 임의로 덮어쓰지 않는다. 기존 `current`가 실제 디렉터리이면 symlink 전환 전에 별도 정리 계획을 세운다. 자동 재시작 횟수 초과 시 원인 수정 뒤 `systemctl reset-failed zeroverse`를 사용한다.

readiness는 DB 연결/사전 점검을 확인하며 실제 S3 업로드 성공을 대신하지 않는다. 외부 `/api/v1/health/*`, Swagger, Actuator는 Caddy가 차단한다. `/api`의 오류가 SPA HTML로 변환되지 않고, deep link는 index.html, 없는 asset은404인지 확인한다.

## 5. 백업과 복원

별도 운영 계정/머신에서 MySQL 8 client, OpenSSL 3.x, AWS CLI를 사용한다. 복호화용 RSA private key는 앱 서버·버킷 밖에서 보관하고 backup job에는 public recipient certificate만 제공한다. 암호화는 CMS AES-256-GCM + RSA-OAEP/SHA-256이다. [OpenSSL CMS](https://docs.openssl.org/3.0/man1/openssl-cms/) 지원 버전을 확인한다.

MySQL client CNF는 host/port/user/password를 넣은 `[client]` 파일로, 소유자 전용0600으로 둔다. [single-transaction dump](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)는 InnoDB snapshot이며 실행 중 DDL/migration을 하지 않는다.

```sh
bash deploy/backup-db.sh /secure/backup-client.cnf /secure/aiven-ca.pem defaultdb \
  /secure/backup-recipient-cert.pem /secure/backups/UNIQUE_BACKUP.cms
# 별도 backup identity와 저장 위치가 승인된 뒤에만 업로드한다.
aws --profile BACKUP_PROFILE --region ap-northeast-2 s3 cp \
  /secure/backups/UNIQUE_BACKUP.cms s3://BACKUP_BUCKET/BACKUP_PREFIX/UNIQUE_BACKUP.cms \
  --sse AES256 --only-show-errors
```

스크립트는 평문 dump를 디스크에 남기지 않으며 dump 실패 시 파일을 게시하지 않는다. 복호화는 private 임시 폴더에서 먼저 완료하고 exit0 및 checksum/내용을 검증한 후 **새 격리 DB**에 적용한다. 인증된 복호화가 끝나기 전에 SQL을 DB로 pipe하지 않는다.

```sh
umask 077
openssl cms -decrypt -binary -inform DER -in /secure/backups/UNIQUE_BACKUP.cms \
  -recip /secure/backup-recipient-cert.pem -inkey /offline/backup-private-key.pem \
  -out /secure/restore/verified.sql
# 위 명령 성공 확인 후, 복원 전용 신규 DB를 가리키는 CNF와 DB_NAME 사용
mysql --defaults-extra-file=/secure/restore-client.cnf \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca=/secure/aiven-ca.pem RESTORE_DB < /secure/restore/verified.sql
```

복원 후 Flyway history, 주요 row counts, 이미지 metadata와 S3 bytes/read ACL, UTC audit를 검증한다. 복구 키 분실·인증 실패 시 복원을 성공으로 기록하지 않는다. Aiven 자동 백업 범위는 별도로 확인한다. 이 저장소의 수동 암호화 dump는 아래 §7의 사용자 결정에 따라 매일04:00KST/7일보관 timer로 운영한다. bucket lifecycle이나 외부 백업 자동 복제는 설치하지 않았다.

## 6. 롤백과 인수

S3 row가 생긴 뒤 M10 pre-S3 JAR로 돌아갈 수 없다. 현재 schema와 호환되는 **S3 지원 release**에 한해 같은 stop→current 교체→start→readiness 절차를 사용한다. 자동 역 migration·객체 삭제를 하지 않는다. 첫 배포 실패는 서비스를 중단하고 수정하거나 검증된 백업/복원 계획을 적용한다.

실제 HTTPS에서 가입→로그인→새로고침/refresh rotation→글·프로필/본문/대표 이미지→댓글·알림→로그아웃을 확인한다. PUBLIC/친구/PRIVATE/draft ACL, anonymous S3 deny, 권한 철회 후 read 차단, 앱 재시작 보존, backup restore, rollback을 통과해야 운영 배포 완료다. 아직 도메인·서버·DB·운영 identity가 없으면 코드 준비 결과만 보고한다.

기술 근거: [Caddy SPA/API 분리](https://caddyserver.com/docs/caddyfile/patterns), [systemd 서비스 격리](https://freedesktop.org/software/systemd/man/latest/systemd.exec.html), [ADR-0009](../docs/governance/decisions/ADR-0009-deployment-s3-contract.md).

## 7. 2026-11-16 첫 운영 대상

사용자가 T6 실행을 명시 승인했다(배포 회의 §15). 도메인은 `zeroverse.co.kr`, Lightsail IPv4는 `54.116.133.220`, 신규 DB는 제공된 Aiven 서비스 `defaultdb`다. 실제 비밀은 서버의 root-only 설정에만 입력한다.

- 운영 앱 IAM 사용자 예: `zeroverse-s3-prod`. [최소 객체 정책](s3-iam-production.json)을 해당 사용자에 적용한다. 콘솔 로그인·관리 권한·ListBucket는 필요하지 않다. 개발 identity를 운영 서버에 복사하지 않는다.
- [운영 CORS 규칙](s3-cors-production.json)은 기존 버킷 CORS 배열에 객체를 추가하여 병합한다. 개발 규칙 등 기존 항목을 통째로 대체하지 않는다. public access block은 유지한다.
- [HTTPS 전용 버킷 정책](s3-bucket-https-only.json)은 기존 정책이 없을 때 전체를 적용한다. 기존 정책이 있으면 Statement를 보존하고 DenyInsecureTransport만 병합한다. AWS 서비스 주체는 이 전송 조건의 Deny에서 제외하며, 별도 접근 권한을 부여하지 않는다. 근거: AWS S3 policy keys의 SecureTransport/PrincipalIsAWSService 예제.
- 환경·DB·HTTPS·S3 및 backup/restore/rollback의 실제 통과 여부는 배포 회의의 후속 증거로 확인한다.

### 정기 백업 — 2026-11-16 사용자 선택

매일 04:00 Asia/Seoul에 암호화 backup을 `/var/backups/zeroverse/`에 생성하고 정기 생성 파일을 7일 보관한다. `zeroverse-backup.timer`가 root oneshot `zeroverse-backup.service`를 실행한다. `scheduled-backup.py`와 검증된 `backup-db.sh`는 `/usr/local/lib/zeroverse/`에 root 소유로 설치한다. 복호화 개인 키는 서버에 설치하지 않는다.

새 백업 성공 후에만 `scheduled-UTCSTAMP-HEX.cms` 중 이름 시각과 mtime이 모두 7일보다 오래된 root 소유 regular/single-link 파일을 정리한다. 마이그레이션 전·수동 백업과 symlink는 삭제하지 않는다. 동시 실행은 flock으로 차단한다. 상태는 `systemctl list-timers zeroverse-backup.timer`, 결과는 `journalctl -u zeroverse-backup.service`로 확인한다. 서버 내 정기 백업은 서버 자체 장애에 대한 별도 외부 사본을 대신하지 않는다. 중요한 변경 전 수동 암호화 백업은 Mac 등 별도 위치에도 복사한다.

### 운영 인수 결과 — 2026-11-16

`https://zeroverse.co.kr`에서 운영 중이다. 실제 HTTPS/browser/API·비공개 S3·권한·refresh rotation, 재시작 후 데이터 보존, 암호화 DB 백업의 격리 복원을 확인했다. 상세 증거와 범위는 [배포 회의 §15.4](../docs/governance/meetings/X-20261113-deployment.md#154-실제-운영-인수복원정리-완료--2026-11-16-kst), [독립 QA](../qa/X-deployment-review.md)를 따른다.

- 현재 release: `/opt/zeroverse/releases/zeroverse-deployment-20261113-a7fb8fe`, 앱/웹 서비스는 `zeroverse`/`caddy`다. 첫 배포의 이전 버전 rollback 대상은 없으며, 동일 artifact의 경로 전환과 복귀·데이터 보존을 실제 검증했다.
- DB 백업: 서버 `/var/backups/zeroverse/`. 배포 전·검증 후·최종 정리 후 암호화 사본은 Mac `/Users/zerovirus/.zeroverse-recovery/20261116/`에도 보관했다. `backup-private-key.pem`은 복원에 필요하므로 안전하게 보존한다. 서버에 이 개인키를 복사하지 않는다.
- 최종 기준 사본은 `ready-20261116.cms`다. 과거 post-migration 백업은 검증용 계정·이미지 메타데이터가 포함된 이전 시점이며, 이후 삭제한 테스트 S3 바이트를 포함하지 않는다. DB 백업과 S3 객체 보존은 별개다.
- 운영 검증 계정3개는 비활성화하고 토큰을 제거했으며 정확한 테스트 객체4개를 정리했다. 하위 DB 이력은 보존했다. 실제 사용자는 홈페이지에서 회원가입해 사용한다.
