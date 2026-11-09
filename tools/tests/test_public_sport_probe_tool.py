import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PublicSportProbeToolTest(unittest.TestCase):
    def test_physical_device_is_refused_before_build_or_adb(self):
        run = subprocess.run(['bash', str(ROOT / 'tools/sporthub/build-public-probe.sh')],
                             env={**os.environ, 'ANDROID_SERIAL': 'physical-device'}, capture_output=True, text=True)
        self.assertNotEqual(0, run.returncode)
        self.assertIn('Physical device refused', run.stdout)

    def fixture(self, directory):
        root = Path(directory)
        tools = root / 'tools/sporthub'
        tools.mkdir(parents=True)
        shutil.copy(ROOT / 'tools/sporthub/build-public-probe.sh', tools)
        (tools / 'HubPublicSourceProbe.kt').write_text('// synthetic probe fixture')
        binary = root / 'bin'
        binary.mkdir()
        adb = binary / 'adb'
        adb.write_text('#!/bin/sh\nprintf "1\\n"\n')
        adb.chmod(0o755)
        destination = root / 'app/src/androidTest/java/tv/own/owntv/home/HubPublicSourceProbe.kt'
        destination.parent.mkdir(parents=True)
        gradle = root / 'gradlew'
        gradle.write_text('#!/bin/sh\nexit 7\n')
        gradle.chmod(0o755)
        env = {**os.environ, 'PATH': str(binary) + os.pathsep + os.environ['PATH'], 'ANDROID_SERIAL': 'emulator-5588'}
        return root, destination, env

    def test_existing_source_is_never_replaced_or_deleted(self):
        with tempfile.TemporaryDirectory() as directory:
            root, destination, env = self.fixture(directory)
            destination.write_text('existing owner source')
            run = subprocess.run(['bash', str(root / 'tools/sporthub/build-public-probe.sh'), str(root / 'out')], env=env, capture_output=True)
            self.assertNotEqual(0, run.returncode)
            self.assertEqual('existing owner source', destination.read_text())

    def test_failed_build_removes_only_its_temporary_source(self):
        with tempfile.TemporaryDirectory() as directory:
            root, destination, env = self.fixture(directory)
            run = subprocess.run(['bash', str(root / 'tools/sporthub/build-public-probe.sh'), str(root / 'out')], env=env, capture_output=True)
            self.assertNotEqual(0, run.returncode)
            self.assertFalse(destination.exists())
            self.assertTrue((root / 'tools/sporthub/HubPublicSourceProbe.kt').exists())
