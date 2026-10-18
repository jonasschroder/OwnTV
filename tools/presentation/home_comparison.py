#!/usr/bin/env python3
"""Collect actual production screenshots into an offline, source-labelled review artifact.

No image synthesis, rescaling, APKs, network requests or access to user data.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import struct


def collect(before: Path, after: Path, output: Path, baseline_sha: str, candidate_sha: str):
    for revision in (baseline_sha, candidate_sha):
        if not re.fullmatch(r"[0-9a-f]{40}", revision):
            raise ValueError("Screenshot source revisions must be full Git commit hashes")
    selected = []
    for width, height in ((1920, 1080), (1280, 720)):
        for label, root, revision in (("before", before, baseline_sha), ("after", after, candidate_sha)):
            files = list(root.rglob(f"premium-{width}x{height}-*-profile-7.png"))
            if len(files) != 1:
                raise ValueError(f"Expected one {label} {width}x{height} production screenshot; found {len(files)}")
            data = files[0].read_bytes()
            if len(data) < 33 or data[:16] != b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR" or struct.unpack(">II", data[16:24]) != (width, height):
                raise ValueError(f"Incorrect PNG dimensions/header for {label} {width}x{height}")
            selected.append((files[0], {"file": f"{label}-{width}x{height}.png", "sourceCommit": revision,
                "width": width, "height": height, "sha256": hashlib.sha256(data).hexdigest()}))
    # Validate every input first: incomplete evidence must never produce a successful comparison.
    output.mkdir(parents=True, exist_ok=True)
    for source, metadata in selected:
        shutil.copyfile(source, output / metadata["file"])
    (output / "evidence.json").write_text(json.dumps([meta for _, meta in selected], indent=2) + "\n")
    sections = []
    for width, height in ((1920, 1080), (1280, 720)):
        figures = "".join(f'<figure><figcaption>{label.title()} · {revision[:8]}</figcaption>'
            f'<a href="{label}-{width}x{height}.png"><img src="{label}-{width}x{height}.png" alt="{label.title()} production Home {width}×{height}"></a></figure>'
            for label, revision in (("before", baseline_sha), ("after", candidate_sha)))
        sections.append(f"<h2>{width} × {height}</h2><section>{figures}</section>")
    (output / "index.html").write_text('''<!doctype html><html lang="en"><meta charset="utf-8">
<meta name="viewport" content="width=device-width"><title>Min TV v0.9 · Home review</title>
<style>body{margin:32px;background:#090f1c;color:#eaf0f7;font:16px system-ui}p{max-width:85ch;color:#a6b5ca}section{display:flex;gap:16px}figure{margin:0;flex:1;min-width:0}img{width:100%;border-radius:12px}figcaption{margin:10px 0}a{color:#80dcf1}@media(max-width:900px){section{display:block}}</style>
<h1>Min TV · Premium Home review</h1><p>Actual production Application, database, Activity, shell and Home. Same persisted synthetic fixture: two profiles, two sources, 600 channels, favorites, history and stored EPG. Swedish app locale; emulator only. Click any image for its original resolution. No live stream, account, installed owner app or installer is involved.</p>
<p>Before: unchanged v0.8.1 UI from main 5326c641, plus test fixture. After: candidate UI. The public version string is intentionally unchanged: this PR does not create a version or signed candidate. Capture times and asynchronous status may differ.</p>
''' + "".join(sections) + '<p>Source commits and PNG SHA256 values: <a href="evidence.json">evidence.json</a>. Physical Chromecast validation and owner visual approval remain required.</p></html>\n')


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("before", "after", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    parser.add_argument("--baseline-sha", required=True)
    parser.add_argument("--candidate-sha", required=True)
    args = parser.parse_args()
    try:
        collect(args.before, args.after, args.output, args.baseline_sha, args.candidate_sha)
    except ValueError as error:
        parser.exit(1, f"Production Home comparison failed: {error}\n")
