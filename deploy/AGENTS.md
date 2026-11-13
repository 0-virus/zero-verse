# Deployment artifacts

Owner: root. Contract: `docs/governance/meetings/X-20261113-deployment.md` §10/12 and ADR-0009.

These files prepare a release; they do not authorize executing it against a real server or database. Keep credentials, CA stores, dumps and local uploads outside the release. Package only explicit build outputs, verify checksums before activation, and preserve prior releases/data. Rollback requires an S3-capable release compatible with the current schema. QA owns independent checks in `qa/`.
