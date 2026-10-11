#!/usr/bin/env python3
"""Verify the configured public Twitch ID survives R8 in an actual packaged DEX."""
import argparse
import os
from pathlib import Path
import re
import struct
import zipfile


def dex_strings(data):
    if not data.startswith(b"dex\n") or len(data) < 112:
        raise ValueError("Invalid DEX header")
    count, offset = struct.unpack_from("<II", data, 56)
    if offset + count * 4 > len(data):
        raise ValueError("Invalid DEX string table")
    for index in range(count):
        position = struct.unpack_from("<I", data, offset + index * 4)[0]
        # Skip the ULEB128 UTF-16 length. The expected ID is strictly ASCII.
        for _ in range(5):
            if position >= len(data):
                raise ValueError("Truncated DEX string")
            byte = data[position]
            position += 1
            if byte < 128:
                break
        else:
            raise ValueError("Invalid DEX string length")
        end = data.find(b"\0", position)
        if end < 0:
            raise ValueError("Unterminated DEX string")
        yield data[position:end]


def verify(apk, expected):
    if not re.fullmatch(r"[a-zA-Z0-9]{8,128}", expected):
        raise ValueError("Expected a valid public Client ID, never a secret/token")
    with zipfile.ZipFile(apk) as archive:
        entries = [name for name in archive.namelist() if re.fullmatch(r"classes[0-9]*\.dex", name)]
        if not entries:
            raise ValueError("APK contains no DEX files")
        found = any(expected.encode("ascii") in set(dex_strings(archive.read(name))) for name in entries)
    if not found:
        raise ValueError("Configured public Twitch Client ID is absent from packaged APK bytecode")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--expected-client-id")
    args = parser.parse_args()
    properties = Path(__file__).resolve().parents[1] / "gradle.properties"
    default = re.search(r"^mintv\.twitchClientId=(.*)$", properties.read_text(), re.MULTILINE)
    expected = args.expected_client_id or os.getenv("MINTV_TWITCH_CLIENT_ID", "").strip() or (default[1] if default else "")
    try:
        verify(args.apk, expected)
    except (ValueError, OSError, zipfile.BadZipFile, struct.error) as error:
        parser.exit(1, f"Twitch APK verification failed: {error}\n")
    print(f"Public Twitch Client ID verified in packaged DEX: {args.apk.name}")


if __name__ == "__main__":
    main()
