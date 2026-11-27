#!/usr/bin/env bash
set -euo pipefail

input_apk="${1:?Input APK is required}"
output_apk="${2:?Output APK is required}"
lineage="${3:?Signing lineage is required}"
original_sha256="${4:?Original certificate SHA-256 is required}"
: "${ANDROID_HOME:?Android SDK is required}"
: "${KEYSTORE_PATH:?Release keystore is required}"
: "${STORE_PASSWORD:?Keystore password is required}"
: "${KEY_PASSWORD:?Signing key password is required}"
apksigner="$ANDROID_HOME/build-tools/36.0.0/apksigner"

lineage_certs=$("$apksigner" lineage --in "$lineage" --print-certs)
mapfile -t fingerprints < <(awk -F': ' '/certificate SHA-256 digest:/ { print tolower($2) }' <<< "$lineage_certs")
if (( ${#fingerprints[@]} < 2 )) || [[ "${fingerprints[0]}" != "$original_sha256" ]]; then
    echo "Signing lineage does not start with the original HomeList certificate." >&2
    exit 1
fi

"$apksigner" sign --min-sdk-version 28 --rotation-min-sdk-version 28 \
    --v1-signing-enabled false --v2-signing-enabled false \
    --v3-signing-enabled true --v4-signing-enabled false --lineage "$lineage" \
    --ks "$KEYSTORE_PATH" --ks-key-alias upload --ks-pass env:STORE_PASSWORD \
    --key-pass env:KEY_PASSWORD --out "$output_apk" "$input_apk"

verification=$("$apksigner" verify --min-sdk-version 28 --print-certs "$output_apk")
signed_sha256=$(awk -F': ' '/^Signer #1 certificate SHA-256 digest:/ { print tolower($2) }' <<< "$verification")
if [[ -z "$signed_sha256" || "$signed_sha256" != "${fingerprints[-1]}" ]]; then
    echo "APK signer does not match the final certificate in the signing lineage." >&2
    exit 1
fi

printf '%s\n' "$signed_sha256"
