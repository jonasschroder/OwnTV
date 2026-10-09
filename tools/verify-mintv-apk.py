#!/usr/bin/env python3
"""Validate the packaged identity/normal-app contract without claiming device behavior."""
import argparse
import pathlib
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--apk", required=True)
parser.add_argument("--aapt2", required=True)
parser.add_argument("--manifest")
parser.add_argument("--qa", action="store_true", help="Verify the isolated Min TV Test ARM debug variant")
parser.add_argument("--peer-manifest", help="Also reject provider/permission/component/link collisions with the other installation")
args = parser.parse_args()
variant = "qaDebug" if args.qa else "standardDebug"
manifest = args.manifest or f"app/build/intermediates/packaged_manifests/{variant}/process{variant[0].upper() + variant[1:]}ManifestForPackage/AndroidManifest.xml"
root = ET.parse(manifest).getroot()
ns = "{http://schemas.android.com/apk/res/android}"
package = "se.jonasschroder.mintv" + (".qa" if args.qa else "")
label = "Min TV Test" if args.qa else "Min TV"
scheme = "mintv-qa" if args.qa else "mintv"
assert root.get("package") == package
assert root.get(ns + "sharedUserId") is None
app = root.find("application")
assert app.get(ns + "name") == "tv.own.owntv.OwnTVApp"
activities = app.findall("activity") + app.findall("activity-alias")

def categories(activity):
    return {c.get(ns + "name") for f in activity.findall("intent-filter") for c in f.findall("category")}

homes = [a for a in activities if "android.intent.category.HOME" in categories(a)]
assert not homes, "Min TV v0.2 must never register as Android HOME"
assert all(a.get(ns + "name") != "tv.own.owntv.home.MinTvHomeActivity" for a in activities)
iptv = [a for a in activities if "android.intent.category.LEANBACK_LAUNCHER" in categories(a)]
assert len(iptv) == 9, "Keep all supported AppIconSwitcher activity names"
assert sum(a.get(ns + "enabled", "true") == "true" for a in iptv) == 1
for activity in iptv:
    assert activity.get(ns + "name").startswith("tv.own.owntv.MainActivity")
    assert activity.get(ns + "icon") == "@mipmap/mintv_icon"
    schemes = {d.get(ns + "scheme") for f in activity.findall("intent-filter") for d in f.findall("data") if d.get(ns + "scheme")}
    assert schemes == {scheme}, schemes
    assert activity.get(ns + "banner") == ("@drawable/mintv_qa_banner" if args.qa else "@drawable/mintv_banner")
for provider in app.findall("provider"):
    assert provider.get(ns + "authorities").startswith(package + "."), ET.tostring(provider)
for permission in root.findall("permission"):
    assert permission.get(ns + "name").startswith(package + "."), ET.tostring(permission)
badging = subprocess.check_output([args.aapt2, "dump", "badging", args.apk], text=True)
assert "package: name='" + package + "'" in badging
assert "application-label:'" + label + "'" in badging
assert all(line.split(":", 1)[1] == f"'{label}'" for line in badging.splitlines() if line.startswith("application-label")), "App label must stay distinct in every packaged locale"
assert "'arm64-v8a'" in badging and "'armeabi-v7a'" in badging
assert pathlib.Path(args.apk).stat().st_size > 0

if args.peer_manifest:
    peer = ET.parse(args.peer_manifest).getroot()
    assert peer.get("package") != package
    assert peer.get(ns + "sharedUserId") is None
    peer_app = peer.find("application")
    own = {p.get(ns + "authorities") for p in app.findall("provider")}
    other = {p.get(ns + "authorities") for p in peer_app.findall("provider")}
    assert own.isdisjoint(other), "Provider authority collision"
    own_permissions = {p.get(ns + "name") for p in root.findall("permission")}
    other_permissions = {p.get(ns + "name") for p in peer.findall("permission")}
    assert own_permissions.isdisjoint(other_permissions), "Custom permission collision"
    peer_activities = peer_app.findall("activity") + peer_app.findall("activity-alias")
    peer_schemes = {d.get(ns + "scheme") for a in peer_activities for f in a.findall("intent-filter") for d in f.findall("data") if d.get(ns + "scheme")}
    assert scheme not in peer_schemes, "Deep link collision"
    assert not any("android.intent.category.HOME" in categories(a) for a in peer_activities)
    own_components = {(package, a.get(ns + "name")) for a in activities}
    other_components = {(peer.get("package"), a.get(ns + "name")) for a in peer_activities}
    assert own_components.isdisjoint(other_components), "Launcher component collision"
    print("PASS: regular/QA packages, providers, custom permissions, components and external link schemes are disjoint; neither registers Android HOME")
print(f"PASS: {label} identity, isolated providers/permissions, no Android HOME registration, IPTV icon entries and ARM APK")
