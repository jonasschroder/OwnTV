#!/usr/bin/env python3
"""Fail closed before a manual in-place update; never installs or deletes anything."""
import argparse
import re
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--previous", required=True, help="APK installed on the device, or its original download")
parser.add_argument("--candidate", required=True)
parser.add_argument("--aapt2", required=True)
parser.add_argument("--apksigner", required=True)
parser.add_argument("--application-id", choices=("se.jonasschroder.mintv", "se.jonasschroder.mintv.qa"),
                    default="se.jonasschroder.mintv", help="Expected installed package; select QA explicitly")
args = parser.parse_args()


def inspect(path):
    certificates = subprocess.check_output([args.apksigner, "verify", "--print-certs", path], text=True)
    digests = set(re.findall(r"^(?:Signer #\d+|V\d+ Signer): certificate SHA-256 digest: ([0-9a-f]+)$", certificates, re.M))
    if not digests:
        raise ValueError("No verified signer certificate")
    badging = subprocess.check_output([args.aapt2, "dump", "badging", path], text=True)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.M)
    if not package:
        raise ValueError("No packaged identity/version")
    return package.group(1), int(package.group(2)), digests


try:
    previous = inspect(args.previous)
    candidate = inspect(args.candidate)
except (ValueError, subprocess.CalledProcessError) as error:
    sys.exit(f"BLOCKED: could not verify APKs: {error}")

print(f"Previous: {previous[0]}, versionCode={previous[1]}, signer={','.join(sorted(previous[2]))}")
print(f"Candidate: {candidate[0]}, versionCode={candidate[1]}, signer={','.join(sorted(candidate[2]))}")
if previous[0] != candidate[0] or candidate[0] != args.application_id:
    sys.exit("BLOCKED: application IDs differ or are not Min TV. Do not uninstall/clear data.")
if previous[2] != candidate[2]:
    sys.exit("BLOCKED: signer certificates differ. Do not uninstall/clear data. See docs/min-tv-v0.2.md.")
if candidate[1] <= previous[1]:
    sys.exit("BLOCKED: candidate versionCode must increase. Rebuild with VERSION_CODE set higher.")
print("PASS: same application ID/signers and increasing versionCode; manual adb install -r can preserve data.")
