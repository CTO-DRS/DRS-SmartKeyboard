#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
DRS M3.4 — the language-pack scaffold generator.

Generates a REAL, valid language-pack skeleton for a new language
(manifest contract + characters/symbols2/numericRow layout stubs) and
immediately validates what it generated — a generator that does not
prove its own output is a trap, so this one refuses to print OK on
anything the validator would reject.

Usage:
  drs_language_pack_scaffold.py --code ckb --name "کوردیی ناوەندی" --author "CTO-DRS" [--out DIR]

Exit 0 + "OK" on a generated+validated scaffold; exit 1 on any problem.
"""
import argparse
import io
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO_ROOT, "tools"))

LAYOUT_TEMPLATE = """[
  [
    { "code": 113, "label": "@c1@" },
    { "code": 119, "label": "@c2@" },
    { "code": 101, "label": "@c3@" }
  ]
]
"""


def generate(code, name, author, out_dir):
    pack_id = f"local.languagepack.{code}"
    pack = os.path.join(out_dir, f"drs-languagepack-{code}")
    os.makedirs(os.path.join(pack, "stylesheets"), exist_ok=True)

    manifest = {
        "$": "ime.extension.languagepack",
        "meta": {
            "id": pack_id,
            "version": "0.1.0",
            "title": f"حزمة اللغة {name} • {code}",
            "description": f"حزمة لغة {name} مولدة بأداة الـSDK الرسمية — عدّل الأنماط ثم وقّع الحزمة.",
            "maintainers": [author],
            "license": "proprietary",
            "keywords": [code, "drs", "keyboard"],
        },
        "items": [
            {
                "id": f"{code}_default",
                "label": name,
                "authors": [author],
                "locale": code,
                "layoutMap": {
                    "characters": f"{pack_id}:{code}_characters",
                    "symbols": f"{pack_id}:{code}_symbols2",
                    "numericRow": f"{pack_id}:{code}_numeric_row",
                },
            },
        ],
    }
    with io.open(os.path.join(pack, "extension.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    layouts_dir = os.path.join(pack, "layouts", "characters")
    os.makedirs(layouts_dir, exist_ok=True)
    for layout in (f"{code}_characters", f"{code}_symbols2", f"{code}_numeric_row"):
        with io.open(os.path.join(layouts_dir, f"{layout}.json"), "w", encoding="utf-8") as f:
            f.write(LAYOUT_TEMPLATE.replace("@c1@", "\u0627").replace("@c2@", "\u0644").replace("@c3@", "\u0628"))

    return pack


def validate(pack):
    """Immediate validation — the scaffold's own output is its first user."""
    with io.open(os.path.join(pack, "extension.json"), encoding="utf-8") as f:
        manifest = json.load(f)
    problems = []
    meta = manifest.get("meta", {})
    if not re.match(r"""^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$""", meta.get("id", "")):
        problems.append(f"invalid id '{meta.get('id')}'")
    if manifest.get("$") != "ime.extension.languagepack":
        problems.append("wrong package type")
    for item in manifest.get("items", []):
        for layout_type, component in item.get("layoutMap", {}).items():
            ext_id, comp_id = component.split(":")
            layout_path = os.path.join(pack, "layouts", "characters", f"{comp_id}.json")
            if not os.path.isfile(layout_path):
                problems.append(f"layout {comp_id} missing ({layout_type})")
            elif ext_id != meta.get("id"):
                problems.append(f"layout {comp_id} names a foreign extension '{ext_id}'")
    return problems


def main():
    parser = argparse.ArgumentParser(description="DRS language-pack scaffold generator")
    parser.add_argument("--code", required=True, help="BCP-47-ish language code (e.g. ckb, fa, ps)")
    parser.add_argument("--name", required=True, help="display name of the language")
    parser.add_argument("--author", default="CTO-DRS", help="maintainer name")
    parser.add_argument("--out", default=None, help="output directory (default: ./generated)")
    args = parser.parse_args()

    if not re.match(r"""^[a-z]{2,3}(-[A-Za-z]+)?$""", args.code):
        print("FAIL: language code must look like 'ckb', 'fa' or 'ps-af'")
        sys.exit(1)

    out_dir = args.out or os.path.join(REPO_ROOT, "generated")
    pack = generate(args.code, args.name, args.author, out_dir)
    problems = validate(pack)
    if problems:
        for p in problems:
            print(f"FAIL  {p}")
        sys.exit(1)
    print(f"OK  scaffold generated and validated: {pack}")
    print("    next: edit layouts/<characters>/*.json, then sign with tools/drs_package_sign.py")


if __name__ == "__main__":
    main()
