#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS — catalog consistency sync (companion to drs_package_sign.py).

Recomputes the `sha256` and `file_size` fields of packages/manifest.json
from the EXACT bytes of packages/*.flex — the same bytes the signing tool
signed and the release pipeline uploads. Never touches `signature` /
`pubkey_id`: signatures stay valid because the bytes do not change.

Why this tool exists (the v2.1.0 theme-install incident): the catalog's
hash/size fields were computed over one build of the theme archives while
the signature was created over another, and the download URLs pointed to
release v1.0.0 which never carried the .flex assets at all — every on-device
install failed with "file verification failed". A catalog whose hash does
not describe its own signed bytes is silently broken until a user taps
install. This tool closes that gap; the `DrsCatalogConsistencyTest` keeps
it closed.

Usage:
  python3 tools/drs_catalog_sync.py            # sync sha256 + file_size
  python3 tools/drs_catalog_sync.py --check    # verify only, exit 1 on drift
"""
import argparse
import hashlib
import json
import os
import sys

MANIFEST_KEYS = ("sha256", "file_size")


def canonical_bytes(repo_root, entry):
    """Return the raw bytes of the entry's .flex file, or None if missing."""
    file_name = entry.get("download_url", "").rsplit("/", 1)[-1]
    path = os.path.join(repo_root, "packages", file_name)
    if not os.path.isfile(path):
        return None
    with open(path, "rb") as handle:
        return handle.read()


def main():
    parser = argparse.ArgumentParser(description="Sync manifest hashes with package bytes")
    parser.add_argument("--check", action="store_true",
                        help="report drift only; exit 1 when out of sync")
    args = parser.parse_args()

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    catalog_path = os.path.join(repo_root, "packages", "manifest.json")
    with open(catalog_path, "r", encoding="utf-8") as handle:
        catalog = json.load(handle)

    drift = 0
    missing = 0
    for entry in catalog.get("packages", []):
        package_id = entry.get("package_id", "?")
        data = canonical_bytes(repo_root, entry)
        if data is None:
            missing += 1
            print(f"MISSING: {package_id} — file not found in packages/")
            continue
        actual_sha = hashlib.sha256(data).hexdigest()
        actual_size = len(data)
        if entry.get("sha256") != actual_sha or entry.get("file_size") != actual_size:
            drift += 1
            print(f"DRIFT:   {package_id}  sha {entry.get('sha256', '-')[:10]}.. -> {actual_sha[:10]}.."
                  f"  size {entry.get('file_size', '-')} -> {actual_size}")
            if not args.check:
                entry["sha256"] = actual_sha
                entry["file_size"] = actual_size
        else:
            print(f"OK:      {package_id}")

    if missing:
        print(f"FAIL: {missing} package file(s) missing", file=sys.stderr)
        sys.exit(1)
    if args.check:
        print("IN SYNC" if drift == 0 else f"OUT OF SYNC: {drift} entr(y/ies)")
        sys.exit(0 if drift == 0 else 1)

    if drift:
        with open(catalog_path, "w", encoding="utf-8") as handle:
            json.dump(catalog, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print(f"catalog updated: {drift} entr(y/ies) re-hashed from package bytes")
    else:
        print("catalog already in sync — nothing written")


if __name__ == "__main__":
    main()
