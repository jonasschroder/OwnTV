"""Narrow visibility needed by explicit TV shortcuts and owner-requested Twitch setup.

A package query permits discovery, not installation or playback. Installed-client SDK/export/
permission/TV-entry eligibility is rechecked by TwitchPlayback; no package name proves compatibility.
"""
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
TV_PACKAGES = frozenset({
    "com.s0und.s0undtv", "com.fgl27.twitch", "tv.twitch.android.app",
    "org.smarttube.stable", "org.smarttube.beta", "app.smarttube.fdroid",
    "com.teamsmart.videomanager.tv", "com.liskovsoft.smarttubetv.beta",
    "se.svt.android.svtplay", "com.android.vending",
})


def verify_visibility(root: ET.Element) -> None:
    permissions = {p.get(ANDROID + "name") for tag in ("uses-permission", "uses-permission-sdk-23")
                   for p in root.findall(tag)}
    assert "android.permission.QUERY_ALL_PACKAGES" not in permissions, "Broad package visibility is forbidden"
    packages = {p.get(ANDROID + "name") for p in root.findall("queries/package")}
    assert packages == TV_PACKAGES, "Explicit TV package visibility must equal the reviewed shortcut/client list"
    assert not root.findall("queries/provider"), "Unexpected provider visibility query"
    # Retain the public URL contracts; Core's existing external IPTV-player queries remain unchanged.
    data = [{k.removeprefix(ANDROID): v for k, v in node.attrib.items()}
            for node in root.findall("queries/intent/data")]
    assert {"scheme": "s0undtv", "host": "stream"} in data, "Missing public S0undTV stream visibility"
    assert {"scheme": "https", "host": "www.twitch.tv"} in data, "Missing public Twitch channel visibility"
