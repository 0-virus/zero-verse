#!/usr/bin/env bash
# Create one encrypted dump. This script never uploads or restores a database.
set -euo pipefail
umask 077

if [[ $# -ne 5 ]]; then
  echo "Usage: bash backup-db.sh MYSQL_CLIENT_CNF MYSQL_CA_PEM DB_NAME RECIPIENT_CERT_PEM OUTPUT_CMS" >&2
  exit 2
fi
client_cnf=$1
ca_file=$2
database_name=$3
recipient_cert=$4
output_file=$5
[[ "$database_name" =~ ^[A-Za-z0-9_]{1,64}$ ]] || { echo "Invalid database name" >&2; exit 2; }
[[ -f "$client_cnf" && -f "$ca_file" && -f "$recipient_cert" ]] || { echo "Required input file missing" >&2; exit 2; }
[[ ! -e "$output_file" && ! -L "$output_file" ]] || { echo "Output already exists" >&2; exit 2; }
case "$(openssl version)" in
  "OpenSSL 3."*) ;;
  *) echo "OpenSSL 3.x with CMS AES-GCM support is required" >&2; exit 2 ;;
esac

output_parent=$(dirname -- "$output_file")
[[ -d "$output_parent" ]] || { echo "Create the private output directory first" >&2; exit 2; }
backup_temp=$(mktemp -d "$output_parent/.zeroverse-backup.XXXXXXXX")
cleanup() {
  rm -f -- "$backup_temp/encrypted.cms"
  rmdir -- "$backup_temp"
}
trap cleanup EXIT

# No DDL/migration may run during this InnoDB snapshot. Credentials stay in CNF.
mysqldump --defaults-extra-file="$client_cnf" \
  --ssl-mode=VERIFY_IDENTITY --ssl-ca="$ca_file" \
  --single-transaction --quick --skip-lock-tables --no-tablespaces \
  --set-gtid-purged=OFF --hex-blob --default-character-set=utf8mb4 \
  "$database_name" \
  | openssl cms -encrypt -binary -aes-256-gcm -outform DER \
      -out "$backup_temp/encrypted.cms" -recip "$recipient_cert" \
      -keyopt rsa_padding_mode:oaep -keyopt rsa_oaep_md:sha256

[[ -s "$backup_temp/encrypted.cms" ]] || { echo "Empty encrypted backup" >&2; exit 1; }
# Hard-link installation is atomic and fails instead of replacing a prior backup.
ln -- "$backup_temp/encrypted.cms" "$output_file"
openssl dgst -sha256 "$output_file"
