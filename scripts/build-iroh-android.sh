#!/usr/bin/env bash
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destination="${1:-$repo/app/src/main/jniLibs}"
mkdir -p "$destination"
destination="$(cd "$destination" && pwd)"

tag=v1.0.0
revision=afcb46d9f583eca81a592eddeae531efe91f3bd1
source_dir="$(mktemp -d)"
trap 'rm -rf -- "$source_dir"' EXIT
git clone --quiet --depth 1 --branch "$tag" https://github.com/n0-computer/iroh-ffi.git "$source_dir"
test "$(git -C "$source_dir" rev-parse HEAD)" = "$revision"

export RUSTFLAGS="${RUSTFLAGS:+$RUSTFLAGS }-C link-arg=-Wl,-z,max-page-size=16384"
(
    cd "$source_dir"
    cargo ndk --platform 24 -t arm64-v8a -t armeabi-v7a -o "$destination" \
        build --locked --release --lib -p iroh-ffi
)
for abi in arm64-v8a armeabi-v7a; do
    test -s "$destination/$abi/libiroh_ffi.so"
done
