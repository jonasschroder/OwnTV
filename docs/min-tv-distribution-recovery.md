# QA Build 1000002: distribution recovery

## Confirmed cause

Signing run [38117560434](https://github.com/jonasschroder/OwnTV/actions/runs/38117560434)
successfully uploaded `MinTV-approved-QA-1000002` (artifact ID 11694318278).
Its upload log reports seven files and the repository root as their least common
ancestor: inputs include both `signed/` and `evidence/`. Downloading that named
artifact into `candidate` preserves those directories:

```text
candidate/
  signed/
    MinTV-Test-v0.2.0-beta.2-1000002-arm.apk
    MinTV-qa-update.json
    SHA256SUMS
    SIGNING-CERTIFICATE.txt
    RELEASE-NOTES.md
    release-candidate.json
  evidence/
    ci-evidence.json
```

Distribution run [38119808259](https://github.com/jonasschroder/OwnTV/actions/runs/38119808259)
completed preflight and download successfully, then failed verification. The old
guard expected `candidate/MinTV-qa-update.json`; that file does not exist. The
release-creation step was skipped.

The fix reads the exact channel manifest and its authenticated APK filename from
`candidate/signed/`. It rejects missing files, symlinks and traversal rather than
searching recursively or accepting root-level substitutes. Evidence/checksum
files are not trust anchors. Only the verified APK, manifest, recomputed checksum
and authenticated release notes are staged in `public/`.

## Actual validation

All 42 Python policy/update/distribution tests pass, including 12 new regression
methods exercising the seven-file ZIP/download structure for QA and Stable,
root decoys, missing/symlink assets, negative verification gates and safe errors.
The synthetic layout tests use metadata/native adapters; the existing public
manifest fixture separately verifies real metadata signatures.

The **existing owner-signed QA artifact** was also downloaded and passed the full
fixed verification function using JDK 21, Android build-tools 37 and read-only
GitHub release/tag lookups. Actual certificate, RSA metadata signature, package,
code `1000002`, name `0.2.0-beta.2`, ARM ABIs, non-debuggable identity, APK size/hash,
v2/v3 signatures, forbidden-key inspection and source ancestry checks passed.
The authenticated source is `209211b84a89116c0eefcf364c14041bcb8fe2a6`.
No existing release/tag blocked staging at validation time.

Verified APK SHA256:

```text
1d727ce8a704995fac1968e6ff5b01751b2f50b9def5022406d2a3ff7c6ae8b0
```

Input and staged APK hashes match. No APK was rebuilt/re-signed, signing workflow
dispatched, key/environment/package/counter changed, tag/release created or app
installed. Stable's layout is covered by synthetic tests; no real Stable
candidate was signed or published for this fix.

Run `python3 -m unittest discover -s tools/tests -v` with JDK 21 on PATH.
The read-only **Min TV distribution checks** workflow runs that suite on relevant
PR/main changes. Existing Android/i18n workflows retain their broader checks.

## Reviewed distribution flow and manual next step

The successful-run/main/environment preflight, version binding, authentic
manifest, native APK checks, source ancestry, bounded monotonic release history,
existing-tag refusal, per-channel concurrency and explicit publication approval
are retained. Verification now reports the failing stage or expected file,
including safe GitHub HTTP status codes, without native stderr, credential values,
HTTP URLs or raw exception contents.

After the owner separately approves merging this fix and publication:

1. Start a **new** `Min TV approved distribution` manual run on updated `main`.
   Do not rerun failed run `38119808259`: checkout uses that run's original
   `github.sha`, so a rerun would execute the old guard.
2. Choose `qa`, candidate signing run ID `38117560434`, and `PUBLISH-QA`.
   Approve the existing protected distribution environment when requested.
3. It downloads `MinTV-approved-QA-1000002` again and verifies the original bytes.
   No new signing run/versionCode is needed while that artifact is retained and
   no same/newer QA release or conflicting tag has appeared.
4. Review the new run and release assets before installation. The workflow
   creates a QA prerelease and never overwrites a tag/asset or marks it latest.

Remote release creation/upload has not been executed by this fix. If that later
step fails, inspect whether GitHub created a partial release/tag before taking
manual recovery action; asset uploads are not an atomic transaction. Existing
overwrite guards must remain in force. Artifact expiry, changed access/protection
rules or an intervening release/tag can also block a later attempt safely.

**Nothing is merged, rerun or published automatically by this fix.**
