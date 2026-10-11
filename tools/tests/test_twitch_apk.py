import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("twitch_apk", Path(__file__).parents[1] / "verify-twitch-apk.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def dex(strings):
    header = bytearray(112)
    header[:8] = b"dex\n039\0"
    struct.pack_into("<II", header, 56, len(strings), 112)
    table, body = bytearray(), bytearray()
    for value in strings:
        table.extend(struct.pack("<I", 112 + len(strings) * 4 + len(body)))
        body.extend(bytes([len(value)]) + value.encode("ascii") + b"\0")
    return bytes(header + table + body)


class TwitchApkTest(unittest.TestCase):
    def test_finds_exact_id_in_secondary_packaged_dex(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", dex(["unrelated"]))
                archive.writestr("classes2.dex", dex(["publicClientFixture123"]))
            module.verify(apk, "publicClientFixture123")

    def test_rejects_prefix_only_or_resource_only(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "fixture.apk"
            with zipfile.ZipFile(apk, "w") as archive:
                archive.writestr("classes.dex", dex(["publicClientFixture123suffix"]))
                archive.writestr("assets/config.txt", "publicClientFixture123")
            with self.assertRaisesRegex(ValueError, "absent"):
                module.verify(apk, "publicClientFixture123")

    def test_rejects_truncated_bytecode(self):
        with self.assertRaisesRegex(ValueError, "Unterminated"):
            list(module.dex_strings(dex(["publicClientFixture123"])[:117]))
