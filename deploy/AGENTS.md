# Deployment artifacts

Owner: root. Contract: `docs/governance/meetings/X-20261113-deployment.md` §10/12 and ADR-0009.

These files prepare a release; they do not authorize executing it against a real server or database. Keep credentials, CA stores, dumps and local uploads outside the release. Package only explicit build outputs, verify checksums before activation, and preserve prior releases/data. Rollback requires an S3-capable release compatible with the current schema. QA owns independent checks in `qa/`.

2026-11-16: user explicitly approved T6 for zeroverse.co.kr / Lightsail 54.116.133.220 / new Aiven defaultdb. Root owns production IAM/CORS JSON templates. IAM scope is zero-verse-s3/zeroverse/prod/* object Get/Put/Delete; merge production CORS with existing rules. Never place credentials in these templates. Execution evidence and remaining gates belong to deployment meeting §15+.

Root also owns s3-bucket-https-only.json: merge its DenyInsecureTransport statement with existing bucket policy, never replace existing statements blindly. This implements the approved HTTPS transport boundary without granting access.

The user chose daily 04:00 Asia/Seoul encrypted backups with 7-day retention on 2026-11-16. Root owns scheduled-backup.py, test_scheduled_backup.py, zeroverse-backup.service, and zeroverse-backup.timer. Prune only expired regular root-owned single-link scheduled-UTCSTAMP-HEX.cms files after a new backup succeeds; preserve manual/pre-migration files and symlinks. Private recovery keys remain off the app server.

2026-11-17: root owns CICD.md and proposed CI/CD tooling described in docs/governance/meetings/X-20261117-cicd.md. The user requested the plan and operating guidelines; workflows/App/host changes remain unimplemented. A nonprivileged puller, fixed root broker/executor, immutable approval receipt, migration fingerprint and disabled startup Flyway are proposed boundaries. Do not treat the guide as installation or activation authorization.
