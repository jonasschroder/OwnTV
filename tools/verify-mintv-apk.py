#!/usr/bin/env python3
"""Validate the packaged identity/HOME contract without claiming device behavior."""
import argparse
import pathlib
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument("--apk", required=True)
parser.add_argument("--aapt2", required=True)
parser.add_argument("--manifest", default="app/build/intermediates/packaged_manifests/standardDebug/processStandardDebugManifestForPackage/AndroidManifest.xml")
args = parser.parse_args()
root = ET.parse(args.manifest).getroot()
ns = "{http://schemas.android.com/apk/res/android}"
package = "se.jonasschroder.mintv"
home_class = "tv.own.owntv.home.MinTvHomeActivity"
assert root.get("package") == package
assert root.get(ns + "sharedUserId") is None
app = root.find("application")
assert app.get(ns + "name") == "tv.own.owntv.OwnTVApp"
activities = app.findall("activity")

def categories(activity):
    return {c.get(ns + "name") for f in activity.findall("intent-filter") for c in f.findall("category")}

homes = [a for a in activities if "android.intent.category.HOME" in categories(a)]
assert len(homes) == 1
home = homes[0]
assert home.get(ns + "name") == home_class
assert home.get(ns + "exported") == "true"
assert home.get(ns + "enabled", "true") == "true"
assert home.get(ns + "launchMode") == "singleTask"
assert home.get(ns + "taskAffinity") == package + ".home"
assert "android.intent.category.LEANBACK_LAUNCHER" not in categories(home)
assert any(
    {"android.intent.category.HOME", "android.intent.category.DEFAULT"}
    <= {c.get(ns + "name") for c in f.findall("category")}
    and any(a.get(ns + "name") == "android.intent.action.MAIN" for a in f.findall("action"))
    for f in home.findall("intent-filter")
)
iptv = [a for a in activities if "android.intent.category.LEANBACK_LAUNCHER" in categories(a)]
assert len(iptv) == 9, "Keep all supported AppIconSwitcher activity names"
assert sum(a.get(ns + "enabled", "true") == "true" for a in iptv) == 1
for activity in iptv:
    assert activity.get(ns + "name").startswith("tv.own.owntv.MainActivity")
    assert activity.get(ns + "icon") == "@mipmap/mintv_icon"
    schemes = {d.get(ns + "scheme") for f in activity.findall("intent-filter") for d in f.findall("data") if d.get(ns + "scheme")}
    assert schemes == {"mintv"}, schemes
for provider in app.findall("provider"):
    assert provider.get(ns + "authorities").startswith(package + "."), ET.tostring(provider)
for permission in root.findall("permission"):
    assert permission.get(ns + "name").startswith(package + "."), ET.tostring(permission)
badging = subprocess.check_output([args.aapt2, "dump", "badging", args.apk], text=True)
assert "package: name='" + package + "'" in badging
assert "application-label:'Min TV'" in badging
assert "'arm64-v8a'" in badging and "'armeabi-v7a'" in badging
assert pathlib.Path(args.apk).stat().st_size > 0
print("PASS: Min TV identity, isolated providers/permissions, stable HOME, IPTV icon entries and ARM APK")
