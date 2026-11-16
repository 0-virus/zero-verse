import datetime as dt
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('scheduled_backup', Path(__file__).with_name('scheduled-backup.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RetentionTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.now = dt.datetime(2026, 11, 16, 19, tzinfo=dt.timezone.utc)

    def file(self, name, days=9):
        p = self.root / name
        p.write_bytes(b'fixture ciphertext')
        stamp = (self.now - dt.timedelta(days=days)).timestamp()
        os.utime(p, (stamp, stamp))
        return p

    def test_only_old_job_outputs_are_selected(self):
        old = self.file('scheduled-20261107T190000Z-aabbccdd.cms')
        self.file('scheduled-20261115T190000Z-aabbccdd.cms', 1)
        self.file('pre-migration-20261101.cms', 15)
        self.file('manual-20261101.cms', 15)
        self.file('scheduled-invalid-aabbccdd.cms', 15)
        self.file('scheduled-20260230T190000Z-aabbccdd.cms', 15)
        self.assertEqual(module.expired_backups(self.root, self.now, os.getuid()), [old])

    def test_symlinks_hardlinks_and_recent_mtime_are_preserved(self):
        old = self.file('manual-old.cms')
        (self.root / 'scheduled-20261107T190000Z-11223344.cms').symlink_to(old)
        os.link(old, self.root / 'scheduled-20261107T190000Z-55667788.cms')
        self.file('scheduled-20261107T190000Z-aabbccdd.cms', 1)
        self.assertEqual(module.expired_backups(self.root, self.now, os.getuid()), [])
        self.assertEqual(module.expired_backups(self.root, self.now, os.getuid()+1), [])

    def test_failed_backup_does_not_prune_previous_backup(self):
        old = self.file('scheduled-20261107T190000Z-aabbccdd.cms')
        def fail(args, check):
            raise subprocess.CalledProcessError(1, args)
        with self.assertRaises(subprocess.CalledProcessError):
            module.backup_and_prune(self.root, self.now, os.getuid(), run=fail)
        self.assertTrue(old.exists())

    def test_success_prunes_only_expired_scheduled_backup(self):
        old = self.file('scheduled-20261107T190000Z-aabbccdd.cms')
        manual = self.file('pre-migration-20261101.cms')
        def success(args, check):
            Path(args[-1]).write_bytes(b'new encrypted backup')
        receipt = module.backup_and_prune(self.root, self.now, os.getuid(), run=success)
        self.assertFalse(old.exists())
        self.assertTrue(manual.exists())
        self.assertEqual(receipt['prunedScheduledBackups'], 1)
        self.assertTrue((self.root / receipt['backup']).exists())

    def test_empty_output_never_prunes(self):
        old = self.file('scheduled-20261107T190000Z-aabbccdd.cms')
        def empty(args, check):
            Path(args[-1]).touch()
        with self.assertRaises(RuntimeError):
            module.backup_and_prune(self.root, self.now, os.getuid(), run=empty)
        self.assertTrue(old.exists())


if __name__ == '__main__':
    unittest.main()
