#!/usr/bin/env bash
# Fetch the immutable Core revision used by the default composite build.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
core_dir="$repo_root/.gradle-cache/OwnTV_Core"
core_commit="$(tr -d '\r\n' < "$repo_root/gradle/owntv-core.commit")"
if [[ ! "$core_commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Invalid Core commit in gradle/owntv-core.commit" >&2
    exit 1
fi

if [[ -e "$core_dir" ]]; then
    if [[ ! -d "$core_dir/.git" ]] ||
       [[ "$(git -C "$core_dir" rev-parse HEAD)" != "$core_commit" ]] ||
       [[ -n "$(git -C "$core_dir" status --porcelain)" ]]; then
        echo "Core checkout differs from the pin or has local changes: $core_dir" >&2
        echo "Preserve local work and move that directory aside before retrying." >&2
        exit 1
    fi
else
    mkdir -p "$(dirname "$core_dir")"
    # Prepare separately so an interrupted fetch cannot become the default checkout.
    stage="$(mktemp -d "$(dirname "$core_dir")/core-fetch.XXXXXX")"
    trap 'rm -rf "$stage"' EXIT
    git -C "$stage" init --quiet
    git -C "$stage" remote add origin https://github.com/ahXN00/OwnTV_Core.git
    git -C "$stage" fetch --depth=1 origin "$core_commit"
    git -C "$stage" checkout --quiet --detach "$core_commit"
    mv "$stage" "$core_dir"
    trap - EXIT
fi

echo "Core ready at $core_commit ($core_dir)"
