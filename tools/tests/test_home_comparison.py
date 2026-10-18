import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("home_comparison", Path(__file__).resolve().parents[1] / "presentation/home_comparison.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class HomeComparisonTest(unittest.TestCase):
    def fixture(self, root, missing=False, wrong=False):
        for width, height in ((1920, 1080), (1280, 720)):
            if missing and height == 720:
                continue
            file = root / "screenshots" / f"premium-{width}x{height}-en-profile-7.png"
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" + struct.pack(">II", width + int(wrong), height) + b"\x00" * 9)

    def test_pairs_real_artifact_layout_without_resizing_and_records_both_source_commits(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root / "baseline")
            self.fixture(root / "candidate")
            module.collect(root / "baseline", root / "candidate", root / "review", "a" * 40, "b" * 40)
            evidence = json.loads((root / "review/evidence.json").read_text())
            self.assertEqual(len(evidence), 4)
            self.assertEqual({item["sourceCommit"] for item in evidence}, {"a" * 40, "b" * 40})
            self.assertEqual((root / "baseline/screenshots/premium-1920x1080-en-profile-7.png").read_bytes(), (root / "review/before-1920x1080.png").read_bytes())
            self.assertIn('href="after-1280x720.png"', (root / "review/index.html").read_text())

    def test_rejects_missing_duplicate_wrong_resolution_and_unverified_revision(self):
        for problem in ("missing", "duplicate", "wrong", "revision"):
            with self.subTest(problem=problem), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                self.fixture(root / "baseline", missing=problem == "missing", wrong=problem == "wrong")
                self.fixture(root / "candidate")
                if problem == "duplicate":
                    source = root / "candidate/screenshots/premium-1920x1080-en-profile-7.png"
                    source.with_name(source.name.replace("-en-", "-sv-")).write_bytes(source.read_bytes())
                with self.assertRaises(ValueError):
                    module.collect(root / "baseline", root / "candidate", root / "review", "moving-main" if problem == "revision" else "a" * 40, "b" * 40)
                self.assertFalse((root / "review/index.html").exists())
