#!/usr/bin/env bash
# Safe, resumable bootstrap for two private standalone GitHub repositories.
# Usage: bash tools/migration/bootstrap-private-sources.sh [--execute]
# A partially created EMPTY destination can be reused; populated destinations are
# accepted only if refs are equal or existing branches can fast-forward. Never force-push.
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
  [[ "$is_fork" == true ]] || { echo "Expected public fork: $origin. Stop and reassess." >&2; exit 1; }
done

echo "Copy public forks to private standalone repositories; leave the public release/update URL intact."
echo "Existing private destinations may be safely fast-forwarded; divergent history is never overwritten."
echo "GPL-3.0 corresponding source for distributed versions must remain available."
if [[ "$#" != 1 || "$1" != --execute ]]; then
  echo "Dry run complete. Use --execute to create or resume private copies."
  exit 0
fi

# Do not put authentication tokens into clone URLs or remote configuration.
gh auth setup-git
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

copy_repo() {
  local origin="$1" target="$2" folder="$3"
  local state private is_fork original_refs destination_refs
  if gh repo view "$target" --json isPrivate,isFork >/dev/null 2>&1; then
    private="$(gh repo view "$target" --json isPrivate --jq '.isPrivate')"
    is_fork="$(gh repo view "$target" --json isFork --jq '.isFork')"
    [[ "$private" == true && "$is_fork" == false ]] || {
      echo "STOP: destination exists but is not a private standalone repository: $target" >&2
      exit 1
    }
    echo "Resuming existing private destination: $target"
  else
    echo "Creating private standalone destination: $target"
    gh repo create "$target" --private \
      --description 'Private GPL-3.0 development copy; public release requires corresponding source'
  fi

  # Critical: prevent automatic CI/build/signing runs before migration of secrets.
  # Fail closed on permissions error; user can disable Actions manually in Settings.
  if ! gh api --method PUT "repos/$target/actions/permissions" -F enabled=false >/dev/null; then
    echo "STOP: Could not disable Actions on $target." >&2
    echo "In GitHub: $target > Settings > Actions > General > Disable actions." >&2
    echo "Then rerun this same script with --execute; it can resume safely." >&2
    exit 1
  fi

  echo "Cloning source history: $origin"
  git clone --quiet --bare "https://github.com/$origin.git" "$tmp/$folder.git"
  # Prefixes (not glob patterns) include nested refs such as feat/... and fix/....
  original_refs="$(git --git-dir="$tmp/$folder.git" for-each-ref --format='%(refname) %(objectname)' refs/heads refs/tags | LC_ALL=C sort)"
  [[ -n "$original_refs" ]] || { echo "STOP: origin has no branches or tags: $origin" >&2; exit 1; }

  destination_refs="$(git ls-remote --refs "https://github.com/$target.git" 'refs/heads/*' 'refs/tags/*' | awk '{print $2 " " $1}' | LC_ALL=C sort)"
  if [[ "$destination_refs" == "$original_refs" ]]; then
    echo "Already copied completely: $target"
  else
    # Existing references may be behind the public source (for example if
    # bootstrap previously copied the repo but the source then advanced).
    # Every existing private branch must be an ancestor of its source branch;
    # existing tags must match *exactly*. Never delete or force-push anything.
    if [[ -n "$destination_refs" ]]; then
      # ls-remote is normalized above to: <refname> <object SHA>.
      while read -r old_ref old_sha; do
        [[ -n "$old_sha" && -n "$old_ref" ]] || continue
        expected_sha="$(git --git-dir="$tmp/$folder.git" rev-parse --verify "$old_ref" 2>/dev/null)" || {
          echo "STOP: private destination has a ref absent from source: $old_ref" >&2
          exit 1
        }
        if [[ "$old_ref" == refs/heads/* ]]; then
          git --git-dir="$tmp/$folder.git" merge-base --is-ancestor "$old_sha" "$expected_sha" || {
            echo "STOP: $old_ref has diverged; no history will be overwritten." >&2
            exit 1
          }
        elif [[ "$old_sha" != "$expected_sha" ]]; then
          echo "STOP: existing tag differs: $old_ref; no tags will be overwritten." >&2
          exit 1
        fi
      done <<< "$destination_refs"
    fi
    echo "Copying missing refs and safely fast-forwarding existing branches: $target"
    git --git-dir="$tmp/$folder.git" push --quiet --all "https://github.com/$target.git"
    git --git-dir="$tmp/$folder.git" push --quiet --tags "https://github.com/$target.git"
  fi

  destination_refs="$(git ls-remote --refs "https://github.com/$target.git" 'refs/heads/*' 'refs/tags/*' | awk '{print $2 " " $1}' | LC_ALL=C sort)"
  [[ "$destination_refs" == "$original_refs" ]] || {
    echo "STOP: branch/tag SHA verification failed for $target" >&2
    exit 1
  }
  private="$(gh repo view "$target" --json isPrivate --jq '.isPrivate')"
  [[ "$private" == true ]] || { echo "STOP: private visibility verification failed for $target" >&2; exit 1; }
  echo "VERIFIED PRIVATE COPY: $target"
}

copy_repo "$app" "$private_app" "app"
copy_repo "$core" "$private_core" "core"

cat <<'DONE'
SUCCESS: both private source repositories verified; GitHub Actions disabled.
The existing public forks, release URLs, signing certificates, and APKs are unchanged.
Do not delete or hide the public releases.
Next: private CI Core access, release code numbering, signing environments,
GPL source archives, public distribution and installed-device update verification.
DONE
