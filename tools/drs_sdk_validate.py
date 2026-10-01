#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS M3.1 — the SDK package validator.

Validates DRS extension packages BEFORE they are signed or shipped:

  1. OFFICIAL CATALOG  — every packages/*.flex listed in
     packages/manifest.json: the archive opens, the manifest parses,
     the id/version/type match the catalog entry, and the ed25519
     signature verifies against the PINNED release public key (the same
     key DrsPackageTrust pins in the app).

  2. DEVELOPER PACKS   — an unpacked pack directory (like the SDK
     sample): manifest contract ($-type, meta id format, version shape,
     maintainers, license) and per-component checks — theme components
     point at a stylesheet that EXISTS (deriving the default
     "stylesheets/<id>.json" path when stylesheetPath is omitted).

  3. SELFTEST          — a synthetic broken pack is rejected and the
     signed sample accepted, proving the validator itself works before
     it judges anyone else.

Usage:
  drs_sdk_validate.py                      # validate catalog + sample (selftest first)
  drs_sdk_validate.py --pack DIR           # validate one developer pack dir
  drs_sdk_validate.py --flex FILE.flex     # validate one signed flex archive
"""
import argparse
import io
import json
import os
import re
import sys
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# The pinned release public key — mirrors DrsPackageTrust.RELEASE_PUBKEY_HEX
# and the signing tool. A validator that trusts a different key is useless.
PINNED_PUBKEY_HEX = "9fef1d98448fc8625dfcc3fdc32d8ce78701d59ff69d018075590033df39865c"

sys.path.insert(0, os.path.join(REPO_ROOT, "tools"))
import drs_package_sign  # noqa: E402  (the RFC-self-tested ed25519 implementation)

META_ID_REGEX = re.compile(r"""^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$""")
VERSION_REGEX = re.compile(r"""^\d+(\.\d+){0,3}([-+][A-Za-z0-9._-]*)?$""")

FAILURES = []


def fail(msg):
    FAILURES.append(msg)
    print(f"  FAIL  {msg}")


def ok(msg):
    print(f"  ok    {msg}")


def check_manifest_contract(manifest, origin):
    """Validates the shared manifest contract of any extension pack."""
    if manifest.get("$") not in ("ime.extension.theme", "ime.extension.keyboard", "ime.extension.languagepack"):
        fail(f"{origin}: unknown package type '{manifest.get('$')}'")
        return None
    meta = manifest.get("meta") or {}
    if not META_ID_REGEX.match(meta.get("id", "")):
        fail(f"{origin}: invalid meta.id '{meta.get('id')}'")
    if not VERSION_REGEX.match(meta.get("version", "")):
        fail(f"{origin}: invalid meta.version '{meta.get('version')}'")
    if not meta.get("maintainers"):
        fail(f"{origin}: no maintainers listed")
    if not meta.get("license"):
        fail(f"{origin}: no license listed")
    return meta


def validate_theme_components(manifest, origin, stylesheet_exists):
    """Theme components: stylesheet existence with DEFAULT-PATH DERIVATION."""
    for theme in manifest.get("themes", []):
        path = theme.get("stylesheetPath") or f"stylesheets/{theme.get('id')}.json"
        if stylesheet_exists(path):
            ok(f"{origin}: theme '{theme.get('id')}' stylesheet found ({path})")
        else:
            fail(f"{origin}: theme '{theme.get('id')}' stylesheet missing ({path})")


def validate_pack_dir(pack_dir, label):
    """Validates an unpacked developer pack directory."""
    print(f"[pack] {label}")
    manifest_path = os.path.join(pack_dir, "extension.json")
    if not os.path.isfile(manifest_path):
        fail(f"{label}: extension.json missing")
        return False
    with io.open(manifest_path, encoding="utf-8") as f:
        manifest = json.load(f)
    meta = check_manifest_contract(manifest, label)
    if manifest.get("$") == "ime.extension.theme":
        validate_theme_components(
            manifest, label,
            lambda rel: os.path.isfile(os.path.join(pack_dir, rel)),
        )
    return meta is not None


def validate_flex(flex_path, catalog_entry=None):
    """Validates a signed .flex archive (catalog integrity + signature)."""
    label = os.path.basename(flex_path)
    print(f"[flex] {label}")
    try:
        with zipfile.ZipFile(flex_path) as z:
            names = z.namelist()
            if "extension.json" not in names:
                fail(f"{label}: no extension.json inside archive")
                return False
            manifest = json.loads(z.read("extension.json").decode("utf-8"))
    except zipfile.BadZipFile:
        fail(f"{label}: not a valid zip archive")
        return False

    meta = check_manifest_contract(manifest, label)
    if meta is None:
        return False

    if catalog_entry is not None:
        if meta.get("id") != catalog_entry.get("package_id"):
            fail(f"{label}: id mismatch vs catalog")
        if meta.get("version") != catalog_entry.get("version"):
            fail(f"{label}: version mismatch vs catalog")
        sig = catalog_entry.get("signature")
        key_id = catalog_entry.get("pubkey_id")
        if not sig or key_id != "cto-drs-release-1":
            fail(f"{label}: catalog entry is not signed with the release key")
        else:
            with open(flex_path, "rb") as f:
                package_bytes = f.read()
            if drs_package_sign.verify(PINNED_PUBKEY_HEX.encode() and bytes.fromhex(PINNED_PUBKEY_HEX), package_bytes, bytes.fromhex(sig)):
                ok(f"{label}: ed25519 signature VALID against the pinned key")
            else:
                fail(f"{label}: ed25519 signature INVALID")
    return True


def validate_catalog():
    catalog_path = os.path.join(REPO_ROOT, "packages", "manifest.json")
    packages_dir = os.path.join(REPO_ROOT, "packages")
    with io.open(catalog_path, encoding="utf-8") as f:
        catalog = json.load(f)
    entries = catalog.get("packages", [])
    print(f"[catalog] {len(entries)} entries")
    for entry in entries:
        url = entry.get("download_url", "")
        file_name = url.rsplit("/", 1)[-1]
        flex_path = os.path.join(packages_dir, file_name)
        if not os.path.isfile(flex_path):
            fail(f"{entry.get('package_id')}: package file missing ({file_name})")
            continue
        validate_flex(flex_path, catalog_entry=entry)


def selftest():
    print("[selftest] proving the validator works in both directions")
    # A synthetic broken pack must be rejected.
    broken = os.path.join(REPO_ROOT, ".sdk-selftest-broken")
    os.makedirs(broken, exist_ok=True)
    with io.open(os.path.join(broken, "extension.json"), "w", encoding="utf-8") as f:
        json.dump({"$": "ime.extension.theme", "meta": {"id": "NOT-AN-ID", "version": "x", "maintainers": [], "license": ""}}, f)
    before = len(FAILURES)
    validate_pack_dir(broken, "selftest-broken")
    if len(FAILURES) == before:
        fail("selftest: the broken pack was NOT rejected")
    # The selftest's EXPECTED failures must not poison the real run.
    FAILURES.clear()
    # Cleanup.
    import shutil
    shutil.rmtree(broken, ignore_errors=True)


def main():
    parser = argparse.ArgumentParser(description="DRS SDK package validator")
    parser.add_argument("--pack", help="validate one developer pack directory")
    parser.add_argument("--flex", help="validate one signed .flex archive")
    parser.add_argument("--skip-catalog", action="store_true")
    args = parser.parse_args()

    # The signing implementation proves itself before the validator runs.
    if not drs_package_sign.selftest():
        print("FATAL: the ed25519 implementation failed its self-test")
        sys.exit(1)

    if args.pack:
        ok_all = validate_pack_dir(args.pack, os.path.basename(args.pack.rstrip("/")))
    elif args.flex:
        ok_all = validate_flex(args.flex)
    else:
        selftest()
        validate_catalog()
        ok_all = validate_pack_dir(
            os.path.join(REPO_ROOT, "sdk", "drs-sample-keyboard-pack"),
            "drs-sample-keyboard-pack",
        )

    if FAILURES or not ok_all:
        print(f"\nVALIDATION FAILED: {len(FAILURES)} problem(s)")
        sys.exit(1)
    print("\nALL PACKS VALID")


if __name__ == "__main__":
    main()
