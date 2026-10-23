#!/usr/bin/env bash
# Fetch one reviewed repository/commit pair. Never modify an existing immutable checkout.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_dir="$repo_root/.gradle-cache/OwnTV_Core"
core_repository="$(cat "$repo_root/gradle/owntv-core.repository")"
core_commit="$(cat "$repo_root/gradle/owntv-core.commit")"
case "$core_repository" in
    https://github.com/ahXN00/OwnTV_Core.git|https://github.com/jonasschroder/OwnTV_Core.git) ;;
    *) echo "Invalid Core repository pin; expected an approved HTTPS GitHub repository." >&2; exit 1 ;;
esac
if [[ ! "$core_commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Invalid Core commit in gradle/owntv-core.commit" >&2
    exit 1
fi

valid_checkout() {
    local checkout="$1" actual_repository actual_commit changes
    [[ ! -L "$checkout" && -d "$checkout/.git" && ! -L "$checkout/.git" ]] || return 1
    actual_repository="$(git -C "$checkout" remote get-url --all origin 2>/dev/null)" || return 1
    actual_commit="$(git -C "$checkout" rev-parse HEAD 2>/dev/null)" || return 1
    changes="$(git -C "$checkout" status --porcelain 2>/dev/null)" || return 1
    [[ "$actual_repository" == "$core_repository" && "$actual_commit" == "$core_commit" && -z "$changes" ]] &&
        ! git -C "$checkout" symbolic-ref -q HEAD >/dev/null 2>&1
}

if [[ -e "$core_dir" || -L "$core_dir" ]]; then
    if ! valid_checkout "$core_dir"; then
        echo "Core cache differs from the repository/commit pin, is dirty, or is not detached." >&2
        echo "Preserve local work and move that directory aside before retrying." >&2
        exit 1
    fi
else
    mkdir -p "$(dirname "$core_dir")"
    stage="$(mktemp -d "$(dirname "$core_dir")/core-fetch.XXXXXX")"
    trap 'rm -rf "$stage"' EXIT
    git -C "$stage" init --quiet
    git -C "$stage" remote add origin "$core_repository"
    # Validate effective URLs too: a local insteadOf rule must not redirect this pin elsewhere.
    if [[ "$(git -C "$stage" remote get-url --all origin 2>/dev/null)" != "$core_repository" ]]; then
        echo "Core repository resolution differs from the approved source pin." >&2
        exit 1
    fi
    # Git transport errors can contain URLs/credentials. Keep them out of console/CI output.
    if ! git -C "$stage" fetch --quiet --depth=1 --no-tags origin "$core_commit" >/dev/null 2>&1 ||
       ! git -C "$stage" checkout --quiet --detach "$core_commit" >/dev/null 2>&1 ||
       ! valid_checkout "$stage"; then
        echo "Could not prepare the pinned Core checkout; verify source access and commit availability." >&2
        exit 1
    fi
    if [[ -e "$core_dir" || -L "$core_dir" ]]; then
        echo "Core cache appeared during preparation; leaving it untouched." >&2
        exit 1
    fi
    mv "$stage" "$core_dir"
    trap - EXIT
fi

echo "Core ready at $core_commit"
