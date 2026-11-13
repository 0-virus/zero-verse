#!/usr/bin/env python3
"""Package explicit artifacts or verify a release. Never contacts a deployment host."""

import argparse
import datetime
import gzip
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
OPS_FILES = (
    "Caddyfile", "zeroverse.service", "zeroverse.env.example", "README.md",
    "s3-cors-development.json", "backup-db.sh", "release.py",
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def safe_path(name):
    path = PurePosixPath(name)
    if path.is_absolute() or ".." in path.parts or "\\" in name or str(path) != name:
        raise ValueError("Unsafe archive path")
    if any(part.startswith(".env") or part == ".aws" for part in path.parts):
        raise ValueError("Secret configuration is not a release artifact")


def check_jar(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names = jar.namelist()
        if "BOOT-INF/classes/application-prod.yml" not in names:
            raise ValueError("JAR is missing the production profile")
        if not any(name.startswith("BOOT-INF/lib/s3-") for name in names):
            raise ValueError("JAR is not an S3-capable release")
        for name in names:
            base = PurePosixPath(name).name
            if (base.startswith("application-local.") and ".example." not in base
                    or base.startswith(".env") or base.endswith((".jks", ".p12", ".pem", ".key"))):
                raise ValueError("JAR contains a local configuration or key store")


def read_regular(path):
    if path.is_symlink() or not path.is_file():
        raise ValueError("Artifacts must be regular files, not symlinks")
    return path.read_bytes()


def package(args):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,79}", args.release_id):
        raise ValueError("Invalid release id")
    jar_data = read_regular(args.jar)
    check_jar(jar_data)
    if args.frontend_dist.is_symlink() or not (args.frontend_dist / "index.html").is_file():
        raise ValueError("Frontend dist must contain index.html")
    files = {"backend/app.jar": jar_data}
    for path in sorted(args.frontend_dist.rglob("*")):
        if path.is_symlink():
            raise ValueError("Frontend artifacts must not contain symlinks")
        if path.is_file():
            name = "frontend/" + path.relative_to(args.frontend_dist).as_posix()
            safe_path(name)
            if path.suffix in (".key", ".pem", ".jks", ".p12"):
                raise ValueError("Frontend artifacts must not contain key stores")
            files[name] = read_regular(path)
    for name in OPS_FILES:
        files["ops/" + name] = read_regular(ROOT / "deploy" / name)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT))
    manifest = {
        "format": 1, "releaseId": args.release_id,
        "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "sourceRevision": revision, "sourceDirty": dirty,
        "storage": "S3", "schema": 7,
        "validation": "Packaging checks only; consult the matching QA evidence before deploying.",
        "files": {name: {"sha256": digest(data), "size": len(data)} for name, data in files.items()},
    }
    files["manifest.json"] = (json.dumps(manifest, indent=2, sort_keys=True) + "\n").encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temp_name = None
    try:
        with tempfile.NamedTemporaryFile(dir=args.output.parent, delete=False) as output:
            temp_name = output.name
            with gzip.GzipFile(filename="", mode="wb", fileobj=output, mtime=0) as compressed:
                with tarfile.open(fileobj=compressed, mode="w") as archive:
                    for name, data in sorted(files.items()):
                        info = tarfile.TarInfo(args.release_id + "/" + name)
                        info.size, info.mode, info.mtime = len(data), 0o644, 0
                        archive.addfile(info, io.BytesIO(data))
        # Exclusive installation: never replace another release archive.
        os.link(temp_name, args.output)
    finally:
        if temp_name is not None:
            os.unlink(temp_name)
    verify(args.output)


def verify(path):
    files = {}
    # Release artifacts are bounded; reject archive bombs before reading members.
    total = 0
    with tarfile.open(path, "r:gz") as archive:
        for member in archive:
            safe_path(member.name)
            total += member.size
            if not member.isfile() or member.size < 0 or total > 512 * 1024 * 1024:
                raise ValueError("Invalid release member or release exceeds 512 MiB")
            if member.name in files:
                raise ValueError("Duplicate release member")
            files[member.name] = archive.extractfile(member).read()
    manifests = [name for name in files if name.count("/") == 1 and name.endswith("/manifest.json")]
    if len(manifests) != 1:
        raise ValueError("Exactly one release manifest is required")
    manifest = json.loads(files.pop(manifests[0]))
    release_id = manifests[0].split("/")[0]
    if manifest.get("format") != 1 or manifest.get("releaseId") != release_id:
        raise ValueError("Manifest identity mismatch")
    expected = manifest["files"]
    if set(files) != {release_id + "/" + name for name in expected}:
        raise ValueError("Manifest member set mismatch")
    for name, metadata in expected.items():
        data = files[release_id + "/" + name]
        if len(data) != metadata["size"] or digest(data) != metadata["sha256"]:
            raise ValueError("Release checksum mismatch")
    check_jar(files[release_id + "/backend/app.jar"])
    print("Verified release {}: {} files; SHA-256 {}".format(
        release_id, len(files), digest(Path(path).read_bytes())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack = commands.add_parser("package")
    pack.add_argument("--jar", type=Path, required=True)
    pack.add_argument("--frontend-dist", type=Path, required=True)
    pack.add_argument("--release-id", required=True)
    pack.add_argument("--output", type=Path, required=True)
    check = commands.add_parser("verify")
    check.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        package(args) if args.command == "package" else verify(args.archive)
    except (ValueError, OSError, KeyError, tarfile.TarError, zipfile.BadZipFile) as error:
        parser.exit(1, "Release check failed: {}\n".format(error))


if __name__ == "__main__":
    main()
