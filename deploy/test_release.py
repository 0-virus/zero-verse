import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("release", Path(__file__).with_name("release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "deploy").mkdir()
        for name in release.OPS_FILES:
            (self.root / "deploy" / name).write_text("example configuration\n")
        self.dist = self.root / "dist"
        self.dist.mkdir()
        (self.dist / "index.html").write_text("<!doctype html><title>Fixture</title>")
        (self.dist / "assets").mkdir()
        (self.dist / "assets" / "app.js").write_text("fetch('/api/v1/feed/public')")
        self.jar = self.root / "app.jar"
        self.write_jar()
        self.args = SimpleNamespace(
            jar=self.jar, frontend_dist=self.dist, release_id="test-release",
            output=self.root / "release.tar.gz",
        )
        self.addCleanup(patch.stopall)
        patch.object(release, "ROOT", self.root).start()
        patch.object(release.subprocess, "check_output",
                     side_effect=lambda args, **kwargs: "abc123\n" if kwargs.get("text") else b"").start()

    def write_jar(self, extra=None):
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("BOOT-INF/classes/application-prod.yml", "spring: {}")
            jar.writestr("BOOT-INF/lib/s3-2.49.6.jar", b"fixture")
            if extra:
                jar.writestr(extra, b"must not be packaged")

    def test_round_trip_and_existing_release_is_never_replaced(self):
        release.package(self.args)
        original = self.args.output.read_bytes()
        release.verify(self.args.output)
        with self.assertRaises(FileExistsError):
            release.package(self.args)
        self.assertEqual(original, self.args.output.read_bytes())

    def test_local_configuration_in_jar_is_rejected(self):
        self.write_jar("BOOT-INF/classes/application-local.yml")
        with self.assertRaisesRegex(ValueError, "local configuration"):
            release.package(self.args)
        self.assertFalse(self.args.output.exists())

    def test_frontend_symlink_or_env_file_cannot_enter_release(self):
        (self.dist / "outside").symlink_to(self.jar)
        with self.assertRaisesRegex(ValueError, "symlink"):
            release.package(self.args)
        (self.dist / "outside").unlink()
        (self.dist / ".env.production").write_text("synthetic fixture")
        with self.assertRaisesRegex(ValueError, "Secret configuration"):
            release.package(self.args)

    def test_changed_file_fails_manifest_verification(self):
        release.package(self.args)
        changed = self.root / "tampered.tar.gz"
        with tarfile.open(self.args.output, "r:gz") as source, tarfile.open(changed, "w:gz") as target:
            for member in source:
                data = source.extractfile(member).read()
                if member.name.endswith("frontend/index.html"):
                    data += b"tampered"
                member.size = len(data)
                target.addfile(member, io.BytesIO(data))
        with self.assertRaisesRegex(ValueError, "checksum mismatch"):
            release.verify(changed)

    def test_path_traversal_and_links_fail_before_extracting(self):
        for name, kind in (("../outside", tarfile.REGTYPE), ("test/link", tarfile.SYMTYPE)):
            with self.subTest(name=name):
                archive = self.root / "unsafe.tar.gz"
                with tarfile.open(archive, "w:gz") as target:
                    info = tarfile.TarInfo(name)
                    info.type = kind
                    info.linkname = "/etc/passwd" if kind == tarfile.SYMTYPE else ""
                    target.addfile(info, io.BytesIO())
                with self.assertRaises(ValueError):
                    release.verify(archive)


if __name__ == "__main__":
    unittest.main()
