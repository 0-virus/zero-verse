#!/usr/bin/env python3
"""Root-only encrypted DB backup; prune only this job's expired output names."""
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess

OUTPUT_DIR = Path('/var/backups/zeroverse')
RETENTION_DAYS = 7
NAME = re.compile(r'^scheduled-(\d{8}T\d{6}Z)-[0-9a-f]{8}\.cms$')


def expired_backups(directory, now, owner_uid):
    cutoff = now - dt.timedelta(days=RETENTION_DAYS)
    expired = []
    for path in directory.iterdir():
        match = NAME.fullmatch(path.name)
        if not match:
            continue
        try:
            stamp = dt.datetime.strptime(match.group(1), '%Y%m%dT%H%M%SZ').replace(tzinfo=dt.timezone.utc)
            info = path.lstat()
        except (ValueError, FileNotFoundError):
            continue
        if (stat.S_ISREG(info.st_mode) and info.st_uid == owner_uid and info.st_nlink == 1
                and stamp < cutoff and info.st_mtime < cutoff.timestamp()):
            expired.append(path)
    return expired


def backup_and_prune(directory, now, owner_uid, run=subprocess.run):
    output = directory / ('scheduled-' + now.strftime('%Y%m%dT%H%M%SZ') + '-' + secrets.token_hex(4) + '.cms')
    run(['bash', '/usr/local/lib/zeroverse/backup-db.sh', '/etc/zeroverse/mysql-client.cnf',
         '/etc/zeroverse/aiven-ca.pem', 'defaultdb', '/etc/zeroverse/backup-recipient-cert.pem',
         str(output)], check=True)
    info = output.lstat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid != owner_uid or info.st_size == 0:
        raise RuntimeError('Backup output verification failed; retention was not applied')
    hasher = hashlib.sha256()
    with output.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(chunk)
    digest = hasher.hexdigest()
    old = expired_backups(directory, now, owner_uid)
    for path in old:
        path.unlink()
    return {'status': 'PASS', 'backup': output.name, 'sha256': digest,
            'prunedScheduledBackups': len(old), 'retentionDays': RETENTION_DAYS}


def main():
    if os.geteuid() != 0:
        raise SystemExit('Run as root via zeroverse-backup.service')
    os.umask(0o077)
    info = OUTPUT_DIR.lstat()
    if not stat.S_ISDIR(info.st_mode) or info.st_uid != 0 or stat.S_IMODE(info.st_mode) != 0o700:
        raise SystemExit('Backup directory must be a root-owned 0700 directory, not a symlink')
    flags = os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW
    fd = os.open(OUTPUT_DIR / '.scheduled-backup.lock', flags, 0o600)
    with os.fdopen(fd, 'w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        print(json.dumps(backup_and_prune(OUTPUT_DIR, dt.datetime.now(dt.timezone.utc), 0)))


if __name__ == '__main__':
    main()
