#!/usr/bin/env bash
#
# ci/privacy_canary.sh — DRS roadmap phase 3 (privacy & trust, task 6)
#
# The STATIC half of the provable network block. Fails the build when:
#   1. the merged manifest requests any permission outside the five
#      documented ones (SECURITY.md / PRIVACY_POLICY.md own the list),
#   2. the network security config stops denying cleartext or the app
#      tag loses the config reference,
#   3. any file under the keyboard core (ime/**, drs/**) imports a
#      network stack (java.net, javax.net, okhttp, ktor, retrofit,
#      volley) — the ONLY sanctioned network callers live in
#      app/drsupdater (update center, package center) behind the
#      absolute-privacy-mode gates,
#   4. the runtime sentinel's manifest expectations
#      (DrsNetworkSentinel.EXPECTED_PERMISSIONS) drift from the manifest
#      itself — the two lists must stay identical.
#
# Exit 0 = every claim still holds. Exit 1 = a privacy claim broke;
# the build must not ship until the drift is either fixed or turned
# into a documented, reviewed decision.

set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "PRIVACY CANARY FAILED: $1" >&2; exit 1; }
pass() { echo "privacy canary: OK — $1"; }

# --- 1) manifest permission allowlist -------------------------------------

MANIFEST="app/src/main/AndroidManifest.xml"

EXPECTED_PERMISSIONS=(
  "android.permission.VIBRATE"
  "android.permission.RECORD_AUDIO"
  "android.permission.POST_NOTIFICATIONS"
  "android.permission.INTERNET"
  "android.permission.REQUEST_INSTALL_PACKAGES"
)

actual_permissions="$(grep -o 'uses-permission android:name="[^"]*"' "$MANIFEST" \
  | sed 's/uses-permission android:name="//; s/"$//' | sort)"

expected_sorted="$(printf '%s\n' "${EXPECTED_PERMISSIONS[@]}" | sort)"

if [ "$actual_permissions" != "$expected_sorted" ]; then
  echo "--- expected ---"; echo "$expected_sorted"
  echo "--- actual ---"; echo "$actual_permissions"
  fail "the manifest permission set drifted from the documented allowlist"
fi
pass "manifest permissions exactly match the documented allowlist (5)"

# --- 2) cleartext denied ---------------------------------------------------

grep -q 'android:networkSecurityConfig="@xml/network_security_config"' "$MANIFEST" \
  || fail "the application tag lost the network security config"
if grep -qi 'cleartextTrafficPermitted="true"' app/src/main/res/xml/network_security_config.xml; then
  fail "the network security config started permitting cleartext"
fi
pass "cleartext traffic stays denied (network security config intact)"

# --- 3) no network stacks in the keyboard core -----------------------------
#
# java.net.URI is explicitly ALLOWED: it is a pure RFC-2396 parser with
# zero I/O capability (DrsAssetResolver uses it to parse theme asset
# descriptors). Everything else under java.net / javax.net and every
# third-party HTTP stack is denied — any NEW java.net.* import must be
# reviewed here before the canary allowlist grows.

CORE_DIRS=(
  app/src/main/kotlin/com/drs/smartkeyboard/ime
  app/src/main/kotlin/com/drs/smartkeyboard/drs
)

offenders="$(grep -rnE '^import (javax\.net\.|okhttp|io\.ktor\.|retrofit2?\.|com\.android\.volley)' \
  "${CORE_DIRS[@]}" --include='*.kt' || true)"

java_net_hits="$(grep -rnE '^import java\.net\.' \
  "${CORE_DIRS[@]}" --include='*.kt' | grep -vE 'import java\.net\.URI$' || true)"

if [ -n "$java_net_hits" ]; then
  offenders="${offenders}${java_net_hits}"
fi

if [ -n "$offenders" ]; then
  echo "$offenders"
  fail "a network stack import appeared inside the keyboard core (ime/drs trees)"
fi
pass "keyboard core (ime/drs trees) imports zero network stacks (java.net.URI parse-only exempted)"

# --- 4) sentinel expectations match the manifest ---------------------------

sentinel_file="app/src/main/kotlin/com/drs/smartkeyboard/drs/privacy/DrsNetworkSentinel.kt"
for perm in "${EXPECTED_PERMISSIONS[@]}"; do
  grep -q "\"$perm\"" "$sentinel_file" \
    || fail "the sentinel's EXPECTED_PERMISSIONS list is missing $perm"
done
pass "runtime sentinel expectations match the manifest allowlist"

echo "privacy canary: ALL CHECKS GREEN"
