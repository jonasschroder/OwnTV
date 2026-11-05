# Private development migration for Min TV (prepared, not executed)

## Why this is a split, not a visibility toggle

As of 2026-11-05, `jonasschroder/OwnTV` and `jonasschroder/OwnTV_Core` are **public forks** of GPL-3.0 projects. GitHub does not permit changing a public fork alone to private. The installed Min TV updater reads `https://api.github.com/repos/jonasschroder/OwnTV/releases` and downloads authenticated manifests/APKs from `https://github.com/jonasschroder/OwnTV/releases/download/...`. Changing that endpoint breaks deployed apps.

GPL-3.0 also requires providing complete corresponding source to recipients of distributed binaries. Do not treat private development as permission to distribute closed-source versions of this fork. Keep license notices, third-party licenses, build scripts and source for *each* distributed APK, including its pinned Core. See https://www.gnu.org/licenses/gpl-faq.en.html.

## Target architecture

| Repository | Visibility | Purpose |
| --- | --- | --- |
| `jonasschroder/OwnTV` | Public, unchanged | Compatibility endpoint for existing app; signed APK/manifest releases plus complete corresponding GPL source per new release |
| `jonasschroder/OwnTV_Core` | Public, unchanged | Existing fork/history corresponding to already distributed versions |
| `jonasschroder/OwnTV_Source` | Private **standalone**, new | Future app development, tests, signing and release orchestration |
| `jonasschroder/OwnTV_Core_Source` | Private **standalone**, new | Future private Core development |
| `jonasschroder/sniply` | Private already | No change |

The public fork's old code/history cannot be made secret retroactively. Ongoing/unreleased changes can be private. Avoid deleting/recreating `OwnTV`: it would put installed update URLs and existing releases at risk.

## Step 1 — non-destructive private copies

With GitHub CLI installed/authenticated on your Mac:

```bash
brew install gh  # only if not already installed
gh auth login
# In a clone of jonasschroder/OwnTV:
bash tools/migration/bootstrap-private-sources.sh
bash tools/migration/bootstrap-private-sources.sh --execute
```

The script refuses existing destinations, creates two **private standalone** repositories, turns off Actions in the copies, clones branches/tags and verifies ref SHAs. It does NOT migrate the live build/distribution, delete/rename public forks, or modify any existing APK/update URL. Run it once only. If it stops halfway, inspect the partial private copy rather than rerunning against an existing destination.

**Required permissions:** GitHub CLI signed in as the owner with permission to create private repositories. This script cannot be executed via the current GitHub connector because repository creation/visibility changes are not available there.

## Step 2 — CI/source access in the private copies (must precede enabling Actions)

1. Change the private app's `gradle/owntv-core.repository` to `https://github.com/jonasschroder/OwnTV_Core_Source.git`; update the explicit allowlists in `settings.gradle.kts` and `tools/prepare-core.sh`.
2. Give `OwnTV_Source` read-only access to `OwnTV_Core_Source` with a fine-grained token or GitHub App. Inject credentials only for the Core fetch. Do not put credentials into `gradle/owntv-core.repository`, logs, or Git remote URLs. Preserve the exact pinned Core commit SHA.
3. Migrate signing environment protection, required reviewers, release signing secrets, and branch protections. Confirm the private workflows use **the private source repository** for source SHA, CI status and signing run verification. Current `tools/signing/release_guard.py` and `distribution_guard.py` hard-code `jonasschroder/OwnTV`.
4. The source repo has a new GitHub Actions run counter. **Do not use the old `CODE_BASE + github.run_number` unchanged.** The public releases currently reach QA `1000025` and Stable `1000024`. Use a migration-reviewed higher monotonic version-code series and update all validations consistently, including `distribution_guard.py`. Never reuse an existing version code.
5. Review costs before enabling all CI: private GitHub-hosted Actions minutes and artifacts count against the plan's allowance, unlike standard public Actions.

## Step 3 — publishing without breaking the existing app

1. Keep `UpdatePolicy.REPO = "jonasschroder/OwnTV"` in the Android app **unchanged**. Also keep the existing package IDs, signing certificate and authenticated manifest schema unchanged.
2. The private app must download/verify its approved signing artifact from **its private source repo**, then release only to **public `jonasschroder/OwnTV`**, using a fine-grained token/GitHub App scoped to `OwnTV` contents write. Never give that token to the Android app.
3. The public repo must contain the release tag `qa-<versionCode>` or `stable-<versionCode>`, and the exact existing APK filename, `MinTV-qa-update.json` or `MinTV-stable-update.json`, and `SHA256SUMS`. Verify hash, signing certificate, version and release ordering before publication. A public release's Git tag should point to a commit **in the public repo**, not a private-only source SHA; the signed manifest already records the actual source SHA.
4. Attach a reproducible **complete corresponding source** archive to each new public GPL APK release: app source, Core at the exact pinned commit, licenses, relevant scripts/build instructions and other GPL-required source. The archive need not reveal *future unreleased work*. Keep earlier release source accessible as required by GPL.
5. Only after verified private QA signing and public publishing, enable the private build workflows selectively.

## Mandatory end-to-end release gate

- [ ] Both new repositories are private, standalone, and have verified branch/tag history.
- [ ] Private app CI can fetch the exact private Core commit without embedding credentials.
- [ ] Private QA build passes all expected tests and its signed candidate matches existing package identity/certificate.
- [ ] New version code exceeds every released code for that same app/channel.
- [ ] QA signing and protected environment approval pass from reviewed private main.
- [ ] QA release is published on the **existing public OwnTV endpoint**; manifest and APK URL return HTTP 200 without credentials.
- [ ] Released APK has an exact-source GPL archive and SHA256 checks.
- [ ] Already-installed Min TV Test updates from the public endpoint without reinstalling or changing package ID.
- [ ] Stable update follows only after manual device validation.
- [ ] Private Actions costs and limits have been reviewed.

**Rollback:** Keep public `OwnTV` releases as-is. If the private build fails, do not publish a new release and do not modify public URLs. Existing deployed installations continue to use their old endpoint.

## Links

- https://docs.github.com/en/pull-requests/reference/forks
- https://www.gnu.org/licenses/gpl-3.0.en.html
- https://docs.github.com/en/billing/concepts/product-billing/github-actions
