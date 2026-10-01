#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS M3.5 — the release gate checklist (11 checks, RELEASE READY or fail).

This is the ONLY opinion the repo has about "can we ship": every check
verifies a REAL artifact that exists in the repository right now — no
manual tick boxes, no prose. Run before every store upload:

  python3 ci/release_checklist.py            # 11/11 → RELEASE READY
"""
import io
import json
import os
import re
import subprocess
import sys
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

sys.path.insert(0, os.path.join(REPO_ROOT, "tools"))
import drs_package_sign  # noqa: E402

PINNED_PUBKEY_HEX = "9fef1d98448fc8625dfcc3fdc32d8ce78701d59ff69d018075590033df39865c"

CHECKS = []


def check(number, name, fn):
    try:
        detail = fn()
        CHECKS.append((number, name, True, detail or ""))
        print(f"  PASS [{number:2d}] {name}" + (f" — {detail}" if detail else ""))
    except Exception as error:  # noqa: BLE001 — the gate reports, then fails
        CHECKS.append((number, name, False, str(error)))
        print(f"  FAIL [{number:2d}] {name} — {error}")


def read(path, mode="r"):
    with io.open(os.path.join(REPO_ROOT, path), mode, encoding=None if "b" in mode else "utf-8") as f:
        return f.read()


def check_gradle_properties():
    props = {}
    for line in read("gradle.properties").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            props[k.strip()] = v.strip()
    code = props.get("projectVersionCode")
    name = props.get("projectVersionName")
    if not code or not code.isdigit():
        raise AssertionError(f"projectVersionCode missing/invalid: {code!r}")
    if not name:
        raise AssertionError("projectVersionName missing")
    return f"versionCode={code} versionName={name}"


def check_changelog_covers_version():
    props = dict(
        line.split("=", 1)
        for line in read("gradle.properties").splitlines()
        if "=" in line and not line.strip().startswith("#")
    )
    name = props["projectVersionName"].strip()
    changelog = read("CHANGELOG.md")
    if f"v{name}" not in changelog:
        raise AssertionError(f"CHANGELOG.md carries no entry for v{name}")
    return f"v{name} documented"


def check_privacy_canary_green():
    result = subprocess.run(
        ["bash", os.path.join(REPO_ROOT, "ci", "privacy_canary.sh")],
        capture_output=True, text=True, timeout=120,
    )
    if "ALL CHECKS GREEN" not in result.stdout:
        raise AssertionError("privacy canary did not report ALL CHECKS GREEN")
    return "ALL CHECKS GREEN"


def check_catalog_signed():
    catalog = json.loads(read("packages/manifest.json"))
    entries = catalog.get("packages", [])
    unsigned = [
        e.get("package_id") for e in entries
        if not e.get("signature") or e.get("pubkey_id") != "cto-drs-release-1"
    ]
    if unsigned:
        raise AssertionError(f"unsigned catalog entries: {unsigned}")
    if len(entries) < 8:
        raise AssertionError(f"expected >=8 catalog entries, found {len(entries)}")
    return f"{len(entries)} entries signed"


def check_catalog_signatures_verify():
    pubkey = bytes.fromhex(PINNED_PUBKEY_HEX)
    catalog = json.loads(read("packages/manifest.json"))
    for entry in catalog.get("packages", []):
        file_name = entry["download_url"].rsplit("/", 1)[-1]
        flex_path = os.path.join(REPO_ROOT, "packages", file_name)
        if not os.path.isfile(flex_path):
            raise AssertionError(f"missing package file: {file_name}")
        with open(flex_path, "rb") as f:
            package_bytes = f.read()
        if not drs_package_sign.verify(pubkey, package_bytes, bytes.fromhex(entry["signature"])):
            raise AssertionError(f"signature INVALID for {file_name}")
    return "8/8 ed25519 VALID"


def check_seed_stays_outside_git():
    seed_path = os.path.join(REPO_ROOT, ".local-secrets", "cto-drs-release-1.seed")
    if not os.path.isfile(seed_path):
        raise AssertionError("release seed not found locally (owner must provision it)")
    tracked = subprocess.run(
        ["git", "ls-files", ".local-secrets/"], cwd=REPO_ROOT,
        capture_output=True, text=True,
    )
    if tracked.stdout.strip():
        raise AssertionError("seed IS TRACKED BY GIT — rotate the key immediately")
    ignored = subprocess.run(
        ["git", "check-ignore", ".local-secrets/cto-drs-release-1.seed"], cwd=REPO_ROOT,
        capture_output=True, text=True,
    )
    if ".local-secrets" not in ignored.stdout:
        raise AssertionError("seed path not git-ignored")
    return "seed local, untracked, ignored"


def check_signed_packages_are_valid_zips():
    catalog = json.loads(read("packages/manifest.json"))
    for entry in catalog.get("packages", []):
        file_name = entry["download_url"].rsplit("/", 1)[-1]
        flex_path = os.path.join(REPO_ROOT, "packages", file_name)
        try:
            with zipfile.ZipFile(flex_path) as z:
                if "extension.json" not in z.namelist():
                    raise AssertionError(f"{file_name}: no manifest inside")
        except zipfile.BadZipFile:
            raise AssertionError(f"{file_name}: corrupt archive")
    return "8/8 archives open"


def check_budget_gate_selftest():
    result = subprocess.run(
        [sys.executable, os.path.join(REPO_ROOT, "ci", "perf_budget_gate.py"), "--selftest"],
        capture_output=True, text=True, timeout=60,
    )
    if result.returncode != 0:
        raise AssertionError("perf budget gate selftest failed")
    return "both directions pass"


def check_ci_workflows_present():
    for wf in ("android.yml", "release.yml", "security.yml", "benchmark.yml"):
        if not os.path.isfile(os.path.join(REPO_ROOT, ".github", "workflows", wf)):
            raise AssertionError(f"missing workflow {wf}")
    return "4 workflows"


def check_launch_and_policy_docs():
    for doc in ("docs/GLOBAL_LAUNCH.md", "PRIVACY_POLICY.md", "SECURITY.md", "docs/RELEASE_POLICY.md"):
        if not os.path.isfile(os.path.join(REPO_ROOT, doc)):
            raise AssertionError(f"missing {doc}")
    return "GLOBAL_LAUNCH + PRIVACY + SECURITY + RELEASE_POLICY"


def check_adrs_through_0007():
    for number in ("0001", "0002", "0003", "0004", "0005", "0006", "0007"):
        matches = [
            f for f in os.listdir(os.path.join(REPO_ROOT, "docs", "adr"))
            if f.startswith(number)
        ]
        if not matches:
            raise AssertionError(f"missing ADR {number}")
    return "ADR 0001..0007"


def main():
    print("DRS release checklist — 11 gates")
    check(1, "gradle.properties version contract", check_gradle_properties)
    check(2, "CHANGELOG covers the current version", check_changelog_covers_version)
    check(3, "privacy canary ALL GREEN", check_privacy_canary_green)
    check(4, "package catalog fully signed", check_catalog_signed)
    check(5, "catalog signatures verify (ed25519, pinned key)", check_catalog_signatures_verify)
    check(6, "release seed local + outside git", check_seed_stays_outside_git)
    check(7, "signed packages are valid archives", check_signed_packages_are_valid_zips)
    check(8, "perf budget gate selftest", check_budget_gate_selftest)
    check(9, "CI workflows present", check_ci_workflows_present)
    check(10, "launch + privacy + security docs", check_launch_and_policy_docs)
    check(11, "ADR chain complete (0001..0007)", check_adrs_through_0007)

    failed = [c for c in CHECKS if not c[2]]
    print()
    if failed:
        print(f"RELEASE NOT READY: {len(failed)}/11 gates failed")
        sys.exit(1)
    print("RELEASE READY 11/11")


if __name__ == "__main__":
    main()
