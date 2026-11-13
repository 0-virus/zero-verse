#!/usr/bin/env bash
# Crypto and failure-path test with a fake dump command; not a real DB restore test.
set -euo pipefail
backup_script=${1:?Provide the backup-db.sh path}
test_dir=$(mktemp -d)
trap 'rm -rf -- "$test_dir"' EXIT
mkdir "$test_dir/bin"
touch "$test_dir/client.cnf" "$test_dir/ca.pem"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=backup-fixture \
  -keyout "$test_dir/key.pem" -out "$test_dir/cert.pem" >/dev/null 2>&1
cat > "$test_dir/bin/mysqldump" <<'FIXTURE'
#!/usr/bin/env bash
printf '%s\n' 'CREATE TABLE fixture(id INT);' 'INSERT INTO fixture VALUES (42);'
exit "${DUMP_TEST_EXIT:-0}"
FIXTURE
chmod 700 "$test_dir/bin/mysqldump"
export PATH="$test_dir/bin:$PATH"
bash "$backup_script" "$test_dir/client.cnf" "$test_dir/ca.pem" fixture \
  "$test_dir/cert.pem" "$test_dir/backup.cms"
openssl cms -decrypt -binary -inform DER -in "$test_dir/backup.cms" \
  -recip "$test_dir/cert.pem" -inkey "$test_dir/key.pem" -out "$test_dir/restored.sql"
mysqldump > "$test_dir/expected.sql"
expected_hash=$(openssl dgst -sha256 -r "$test_dir/expected.sql")
actual_hash=$(openssl dgst -sha256 -r "$test_dir/restored.sql")
test "${expected_hash%% *}" = "${actual_hash%% *}"
if bash "$backup_script" "$test_dir/client.cnf" "$test_dir/ca.pem" fixture \
  "$test_dir/cert.pem" "$test_dir/backup.cms"; then
  echo "ERROR: existing backup was overwritten" >&2; exit 1
fi
if DUMP_TEST_EXIT=7 bash "$backup_script" "$test_dir/client.cnf" "$test_dir/ca.pem" fixture \
  "$test_dir/cert.pem" "$test_dir/failed.cms"; then
  echo "ERROR: failed dump was published" >&2; exit 1
fi
test ! -e "$test_dir/failed.cms"
cp "$test_dir/backup.cms" "$test_dir/corrupt.cms"
# Alter the final authentication tag byte, preserving the ASN.1 envelope lengths.
last_byte=$(tail -c 1 "$test_dir/corrupt.cms" | od -An -tu1 | tr -d ' ')
changed_byte=$((last_byte ^ 1))
octal=$(printf '%03o' "$changed_byte")
length=$(wc -c < "$test_dir/corrupt.cms")
printf "\\$octal" | dd of="$test_dir/corrupt.cms" bs=1 seek=$((length - 1)) conv=notrunc 2>/dev/null
if openssl cms -decrypt -binary -inform DER -in "$test_dir/corrupt.cms" \
  -recip "$test_dir/cert.pem" -inkey "$test_dir/key.pem" -out "$test_dir/rejected.sql" 2>/dev/null; then
  echo "ERROR: corrupt backup accepted" >&2; exit 1
fi
echo 'PASS: encrypted round trip, no overwrite, failed-dump rejection, tamper rejection (synthetic dump)'
