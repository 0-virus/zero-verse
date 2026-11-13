#!/usr/bin/env python3
"""Run isolated checks for the deployment Caddy/release artifacts.

The test never binds the repository into a VM. It sends the Caddyfile through
stdin for validation and uses ``docker cp`` for a temporary fixture container.
The only network listener created by the harness is 127.0.0.1:18198.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import http.client
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import uuid


ROOT = Path(__file__).resolve().parents[1]
IMAGE = (
    "caddy@sha256:78bd35025c7ba85961dd149ae670315129954e77abe2383ac47c89106b35ade9"
)
DOCKER_HOST = "unix:///private/tmp/zeroverse-colima/docker.sock"
DEFAULT_PORT = 18198
RECEIPT_ROOT = ROOT / "build" / "x-deployment-artifacts-20261113"
INDEX_BODY = b"<!doctype html><html><body>zeroverse-caddy-fixture</body></html>\n"
ASSET_BODY = b"window.zeroverseFixture = true;\n"
API_BODY = b'{"error":"dummy-api"}'


class CheckError(RuntimeError):
    """A failed independent check."""


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def clip(text: str, limit: int = 2400) -> str:
    text = text.strip()
    return text if len(text) <= limit else text[:limit] + "..."


class Runner:
    def __init__(self, port: int):
        self.port = port
        self.run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
        self.receipt_dir = RECEIPT_ROOT / self.run_id
        self.receipt_dir.mkdir(parents=True, exist_ok=False)
        self.log_path = self.receipt_dir / "run.log"
        self.checks: list[dict[str, object]] = []
        self.container_name: str | None = None
        self.temp_dir: tempfile.TemporaryDirectory[str] | None = None
        self.fixture_dir: Path | None = None

    def log(self, message: str) -> None:
        with self.log_path.open("a", encoding="utf-8") as log:
            log.write(message.rstrip() + "\n")

    def command(self, args: list[str], *, input_bytes: bytes | None = None, timeout: int = 30) -> subprocess.CompletedProcess[bytes]:
        env = os.environ.copy()
        env["DOCKER_HOST"] = DOCKER_HOST
        self.log("$ " + " ".join(args))
        try:
            result = subprocess.run(
                args,
                cwd=ROOT,
                env=env,
                input=input_bytes,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=timeout,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            self.log("command error: " + repr(error))
            raise CheckError(f"command failed: {args[0]}: {error}") from error
        output = result.stdout.decode("utf-8", errors="replace")
        self.log(output)
        return result

    def require_command(self, args: list[str], *, input_bytes: bytes | None = None, timeout: int = 30) -> str:
        result = self.command(args, input_bytes=input_bytes, timeout=timeout)
        output = result.stdout.decode("utf-8", errors="replace")
        if result.returncode != 0:
            raise CheckError(f"exit {result.returncode}: {clip(output)}")
        return output

    def check(self, name: str, action) -> None:
        started = time.monotonic()
        try:
            detail = action()
        except Exception as error:  # noqa: BLE001 - receipt must capture the failing check
            elapsed = round(time.monotonic() - started, 3)
            self.checks.append({"id": name, "status": "FAIL", "seconds": elapsed, "error": str(error)})
            raise
        elapsed = round(time.monotonic() - started, 3)
        self.checks.append({"id": name, "status": "PASS", "seconds": elapsed, "detail": str(detail)})

    def source_contract(self) -> str:
        caddy = ROOT / "deploy" / "Caddyfile"
        release = ROOT / "deploy" / "release.py"
        release_tests = ROOT / "deploy" / "test_release.py"
        missing = [str(path) for path in (caddy, release, release_tests) if not path.is_file()]
        if missing:
            raise CheckError("missing deployment artifact: " + ", ".join(missing))
        text = caddy.read_text(encoding="utf-8")
        expected = (
            "{$DOMAIN}",
            "handle @api",
            "reverse_proxy",
            "handle /assets/*",
            "try_files {path} /index.html",
            'header /index.html Cache-Control "no-store"',
        )
        absent = [item for item in expected if item not in text]
        if absent:
            raise CheckError("Caddyfile contract markers missing: " + ", ".join(absent))
        return "deploy/Caddyfile, release.py, test_release.py present; required route markers found"

    def release_tests(self) -> str:
        result = self.command([sys.executable, "deploy/test_release.py"], timeout=60)
        output = result.stdout.decode("utf-8", errors="replace")
        if result.returncode != 0:
            raise CheckError(f"deploy/test_release.py exit {result.returncode}: {clip(output)}")
        if "OK" not in output:
            raise CheckError("deploy/test_release.py did not report unittest OK")
        return clip(output, 800)

    def image_and_docker(self) -> str:
        version = self.require_command(["docker", "version", "--format", "{{.Server.Version}}"], timeout=30).strip()
        image_id = self.require_command(["docker", "image", "inspect", IMAGE, "--format", "{{.Id}}"], timeout=30).strip()
        if not image_id:
            raise CheckError("Caddy image digest was not found")
        return f"Docker server {version}; image {IMAGE} ({image_id})"

    def caddy_validate(self) -> str:
        caddyfile = (ROOT / "deploy" / "Caddyfile").read_bytes()
        output = self.require_command(
            [
                "docker",
                "run",
                "--rm",
                "-i",
                "-e",
                "DOMAIN=:80",
                "-e",
                "BACKEND_ADDRESS=127.0.0.1:8081",
                "-e",
                "STATIC_ROOT=/srv/frontend",
                "--entrypoint",
                "/bin/sh",
                IMAGE,
                "-c",
                "cat > /tmp/Caddyfile && caddy validate --config /tmp/Caddyfile --adapter caddyfile",
            ],
            input_bytes=caddyfile,
            timeout=60,
        )
        if "Valid configuration" not in output:
            raise CheckError("Caddy validate did not report Valid configuration")
        return clip(output, 1000)

    def port_available(self) -> str:
        probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            probe.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            probe.bind(("127.0.0.1", self.port))
        except OSError as error:
            raise CheckError(f"127.0.0.1:{self.port} is not available: {error}") from error
        finally:
            probe.close()
        return f"127.0.0.1:{self.port} available before container start"

    def make_fixture(self) -> str:
        self.temp_dir = tempfile.TemporaryDirectory(prefix="zeroverse-deploy-caddy-")
        self.fixture_dir = Path(self.temp_dir.name)
        static = self.fixture_dir / "frontend"
        (static / "assets").mkdir(parents=True)
        (static / "index.html").write_bytes(INDEX_BODY)
        (static / "assets" / "app.js").write_bytes(ASSET_BODY)

        actual = (ROOT / "deploy" / "Caddyfile").read_text(encoding="utf-8").rstrip()
        dummy_site = r'''

:8081 {
	@api path /api/*
	header @api Content-Type application/json
	respond @api "{\"error\":\"dummy-api\"}" 404
}
'''
        (self.fixture_dir / "Caddyfile").write_text(actual + dummy_site, encoding="utf-8")
        return "fixture created with actual deploy/Caddyfile plus an in-container JSON 404 API site"

    def docker_fixture(self) -> str:
        if self.fixture_dir is None:
            raise CheckError("fixture directory was not created")
        self.container_name = "zeroverse-caddy-" + uuid.uuid4().hex[:12]
        self.require_command(
            [
                "docker",
                "create",
                "--name",
                self.container_name,
                "--publish",
                f"127.0.0.1:{self.port}:80",
                "--env",
                "DOMAIN=:80",
                "--env",
                "BACKEND_ADDRESS=127.0.0.1:8081",
                "--env",
                "STATIC_ROOT=/srv/frontend",
                IMAGE,
                "caddy",
                "run",
                "--config",
                "/srv/Caddyfile",
                "--adapter",
                "caddyfile",
            ],
            timeout=60,
        )
        self.require_command(["docker", "cp", str(self.fixture_dir / "Caddyfile"), f"{self.container_name}:/srv/Caddyfile"], timeout=30)
        self.require_command(["docker", "cp", str(self.fixture_dir / "frontend" / "."), f"{self.container_name}:/srv/frontend/"], timeout=30)
        self.require_command(["docker", "start", self.container_name], timeout=60)
        self.wait_for_listener()
        return f"container {self.container_name} started on 127.0.0.1:{self.port}; config/static copied via Docker API"

    def fixture_caddy_validate(self) -> str:
        if self.fixture_dir is None:
            raise CheckError("fixture directory was not created")
        output = self.require_command(
            [
                "docker",
                "run",
                "--rm",
                "-i",
                "-e",
                "DOMAIN=:80",
                "-e",
                "BACKEND_ADDRESS=127.0.0.1:8081",
                "-e",
                "STATIC_ROOT=/srv/frontend",
                "--entrypoint",
                "/bin/sh",
                IMAGE,
                "-c",
                "cat > /tmp/Caddyfile && caddy validate --config /tmp/Caddyfile --adapter caddyfile",
            ],
            input_bytes=(self.fixture_dir / "Caddyfile").read_bytes(),
            timeout=60,
        )
        if "Valid configuration" not in output:
            raise CheckError("fixture Caddy validate did not report Valid configuration")
        return clip(output, 1000)

    def wait_for_listener(self) -> None:
        deadline = time.monotonic() + 12
        last_error = "not attempted"
        while time.monotonic() < deadline:
            probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            probe.settimeout(1)
            try:
                probe.connect(("127.0.0.1", self.port))
                probe.close()
                connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=1)
                try:
                    connection.request("GET", "/deep/spa", headers={"Accept-Encoding": "identity"})
                    response = connection.getresponse()
                    response.read()
                    return
                finally:
                    connection.close()
            except OSError as error:
                last_error = str(error)
                time.sleep(0.2)
            except http.client.HTTPException as error:
                last_error = str(error)
                time.sleep(0.2)
            finally:
                probe.close()
        raise CheckError(f"Caddy did not listen on 127.0.0.1:{self.port}: {last_error}")

    def request(self, path: str) -> tuple[int, dict[str, str], bytes]:
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        try:
            connection.request("GET", path, headers={"Accept-Encoding": "identity"})
            response = connection.getresponse()
            body = response.read()
            headers = {key.lower(): value for key, value in response.getheaders()}
            return response.status, headers, body
        finally:
            connection.close()

    @staticmethod
    def content_type(headers: dict[str, str]) -> str:
        return headers.get("content-type", "").lower()

    def http_routes(self) -> str:
        observations: list[str] = []
        status, headers, body = self.request("/deep/spa")
        if status != 200 or body != INDEX_BODY or "no-store" not in headers.get("cache-control", "").lower():
            raise CheckError(f"SPA fallback mismatch: status={status}, cache-control={headers.get('cache-control')!r}, body={clip(body.decode('utf-8', 'replace'))!r}")
        if not self.content_type(headers).startswith("text/html"):
            raise CheckError("SPA fallback is not HTML")
        observations.append(f"/deep/spa={status} content-type={headers.get('content-type', '')} cache-control={headers.get('cache-control', '')}")

        status, headers, body = self.request("/assets/app.js")
        if status != 200 or body != ASSET_BODY:
            raise CheckError(f"static asset mismatch: status={status}, body={body!r}")
        observations.append(f"/assets/app.js={status} content-type={headers.get('content-type', '')}")

        status, headers, body = self.request("/assets/missing.js")
        if status != 404 or body == INDEX_BODY or "<html" in body.lower().decode("utf-8", "replace") or self.content_type(headers).startswith("text/html"):
            raise CheckError(f"missing asset became HTML/success: status={status}, content-type={headers.get('content-type')!r}, body={clip(body.decode('utf-8', 'replace'))!r}")
        observations.append(f"/assets/missing.js={status} content-type={headers.get('content-type', '')} non-html=true")

        status, headers, body = self.request("/api/v1/feed/public")
        if status != 404 or body.strip() != API_BODY or self.content_type(headers) != "application/json":
            raise CheckError(f"API response changed/fell through SPA: status={status}, content-type={headers.get('content-type')!r}, body={body!r}")
        observations.append(f"/api/v1/feed/public={status} content-type={headers.get('content-type', '')} body=dummy-api")

        for path in ("/api/v1/health", "/swagger-ui/index.html", "/v3/api-docs"):
            status, headers, body = self.request(path)
            if status != 404 or body == INDEX_BODY or API_BODY in body or "<html" in body.lower().decode("utf-8", "replace"):
                raise CheckError(f"internal path was externally served: path={path}, status={status}, body={clip(body.decode('utf-8', 'replace'))!r}")
            observations.append(f"{path}=404 external-block")
        return "; ".join(observations)

    def cleanup(self) -> None:
        if self.container_name is not None:
            self.command(["docker", "inspect", self.container_name, "--format", "{{json .State}}"], timeout=30)
            self.command(["docker", "logs", "--tail", "100", self.container_name], timeout=30)
            result = self.command(["docker", "rm", "--force", self.container_name], timeout=60)
            if result.returncode == 0:
                self.log("temporary container removed")
            else:
                self.log("temporary container removal failed; inspect Docker state")
        if self.temp_dir is not None:
            self.temp_dir.cleanup()

    def write_receipt(self, overall: str, error: str | None) -> Path:
        receipt = {
            "status": overall,
            "error": error,
            "runId": self.run_id,
            "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
            "dockerHost": DOCKER_HOST,
            "image": IMAGE,
            "port": self.port,
            "transport": ["stdin", "docker cp"],
            "container": self.container_name,
            "checks": self.checks,
            "source": {
                "deploy/Caddyfile": sha256(ROOT / "deploy" / "Caddyfile") if (ROOT / "deploy" / "Caddyfile").is_file() else None,
                "deploy/release.py": sha256(ROOT / "deploy" / "release.py") if (ROOT / "deploy" / "release.py").is_file() else None,
                "deploy/test_release.py": sha256(ROOT / "deploy" / "test_release.py") if (ROOT / "deploy" / "test_release.py").is_file() else None,
            },
        }
        path = self.receipt_dir / "receipt.json"
        path.write_text(json.dumps(receipt, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return path

    def run(self) -> int:
        error: str | None = None
        overall = "PASS"
        try:
            self.check("source-contract", self.source_contract)
            self.check("release-unit-tests", self.release_tests)
            self.check("docker-image", self.image_and_docker)
            self.check("caddy-validate", self.caddy_validate)
            self.check("port-availability", self.port_available)
            self.check("fixture-creation", self.make_fixture)
            self.check("fixture-caddy-validate", self.fixture_caddy_validate)
            self.check("docker-fixture", self.docker_fixture)
            self.check("http-routes", self.http_routes)
        except Exception as failure:  # noqa: BLE001 - final receipt records the failed gate
            overall = "FAIL"
            error = str(failure)
        finally:
            self.cleanup()
            receipt = self.write_receipt(overall, error)
        print(f"deployment artifact checks: {overall}")
        print(f"receipt: {receipt}")
        if error:
            print(f"failure: {error}", file=sys.stderr)
        return 0 if overall == "PASS" else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    return Runner(args.port).run()


if __name__ == "__main__":
    raise SystemExit(main())
