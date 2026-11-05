#!/usr/bin/env bash
# Safe migration bootstrap: make private standalone copies; never touch public releases.
# Usage: bash tools/migration/bootstrap-private-sources.sh [--execute]
set -euo pipefail

owner="jonasschroder"
app="$owner/OwnTV"
core="$owner/OwnTV_Core"
private_app="$owner/OwnTV_Source"
private_core="$owner/OwnTV_Core_Source"

command -v gh >/dev/null || { echo 'Install GitHub CLI: https://cli.github.com/' >&2; exit 1; }
command -v git >/dev/null || { echo 'Git is required' >&2; exit 1; }
gh auth status --hostname github.com >/dev/null

for origin in "$app" "$core"; do
  gh repo view "$origin" --json nameWithOwner,isPrivate,isFork,defaultBranchRef \
    --jq '"Source: " + .nameWithOwner + "  private=" + (.isPrivate|tostring) + "  fork=" + (.isFork|tostring) + "  default=" + .defaultBranchRef.name'
  is_fork="$(gh repo view "$origin" --json isFork --jq '.isFork')"
  [[ "$is_fork" == true ]] || { echo "Expected fork: $origin. Stop and reassess." >&2; exit 1; }
done

echo "Plan: private standalone $private_app and $private_core, copying all branch/tag history."
echo "Public OwnTV/OwnTV_Core, APKs, release downloads, and existing app update URLs stay unchanged."
echo "Private repo Actions will be disabled until the release pipeline is migrated."
echo "Public GPL-3.0 releases must keep access to their corresponding source."

if [[ "$#" != 1 || "$1" != --execute ]]; then
  echo "No changes made. Run with --execute to make the two private copies."
  exit 0
fi

for target in "$private_app" "$private_core"; do
  if gh repo view "$target" >/dev/null 2>&1; then
    echo "Refusing to overwrite existing destination: $target" >&2
    exit 1
  fi
done

# Use the GitHub credential helper, never embed tokens in remote URLs.
gh auth setup-git
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

copy_repo() {
  local source="$1" target="$2" folder="$3"
  gh repo create "$target" --private \
    --description 'Private GPL-3.0 development copy (public binary releases still require corresponding source)'
  # Prevent costly and insecure first-run CI before secrets and branch protections exist.
  gh api -X PUT "repos/$target/actions/permissions" -F enabled=false >/dev/null
  git clone --quiet --bare "https://github.com/$source.git" "$tmp/$folder.git"
  git --git-dir="$tmp/$folder.git" push --quiet --mirror "https://github.com/$target.git"
  local original_refs copied_refs private
  original_refs="$(git --git-dir="$tmp/$folder.git" for-each-ref --format='%(refname) %(objectname)' 'refs/heads/*' 'refs/tags/*' | LC_ALL=C sort)"
  copied_refs="$(git ls-remote --refs "https://github.com/$target.git" 'refs/heads/*' 'refs/tags/*' | awk '{print $2 " " $1}' | LC_ALL=C sort)"
  [[ "$original_refs" == "$copied_refs" ]] || { echo "Branch/tag SHA mismatch in $target; STOP migration." >&2; exit 1; }
  private="$(gh repo view "$target" --json isPrivate --jq '.isPrivate')"
  [[ "$private" == true ]] || { echo "Private visibility check FAILED: $target" >&2; exit 1; }
  echo "Verified: $target is private and all branches/tags match."
}

copy_repo "$app" "$private_app" "app"
copy_repo "$core" "$private_core" "core"

cat <<'DONE'
Private development copies created and verified, with CI intentionally disabled.
DO NOT delete the public forks or make a private-release cutover yet.
Next: source token access for Core, independent monotonic versionCode, signing
environments, public-release publishing and source bundles, then end-to-end QA.
DONE
