#!/usr/bin/env python3
"""مزامنة آلية لأصول الإصدار إلى استضافة موقع التوثيق (GitHub Pages).

يعاد تشغيله من مهمة sync-site-hosting في release.yml بعد نشر كل إصدار مستقر:
1. يتحقق من بصمات SHA-256 لملفات dist مقابل SHA256SUMS.txt (بوابة صدق — أي خلل يفشل المهمة)
2. يحدّث docs/apk/ (نسخة مسماة + نسخة latest ثابتة الاسم + SHA256SUMS.txt + latest.json)
3. يحدّث docs/flex/ (حزم .flex + flex.json من packages/manifest.json)
4. يعيد كتابة مناطق AUTO:* في docs/index.html (بادج الواجهة، أزرار التنزيل، البصمة، شبكة السمات)

الصفحة بلا سكربتات إطلاقًا (عقيدة الصفحة) — التحديث كله يحدث هنا في CI لا في المتصفح.
"""
import argparse
import hashlib
import html
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APK_DIR = REPO / "docs" / "apk"
FLEX_DIR = REPO / "docs" / "flex"
INDEX = REPO / "docs" / "index.html"


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def mb(size: int) -> str:
    return f"{round(size / 1048576, 1):g}"


def kb(size: int) -> str:
    return f"{round(size / 1024, 1):g}"


def load_sums(dist: Path) -> dict:
    sums = {}
    for line in (dist / "SHA256SUMS.txt").read_text().splitlines():
        parts = line.split()
        if len(parts) == 2:
            sums[parts[1]] = parts[0]
    return sums


def verify(dist: Path, sums: dict) -> None:
    problems = []
    for f in sorted(dist.iterdir()):
        if f.name == "SHA256SUMS.txt":
            continue
        actual = sha256_of(f)
        expected = sums.get(f.name)
        if expected is None:
            problems.append(f"asset {f.name} not listed in SHA256SUMS.txt")
        elif expected != actual:
            problems.append(f"{f.name}: sha mismatch ({actual} != {expected})")
    if problems:
        print("FAIL:", *problems, sep="\n- ")
        sys.exit(1)


def replace_zone(text: str, zone: str, inner: str) -> str:
    pattern = re.compile(
        r"(<!-- AUTO:" + zone + r"-START[^\n]*-->\n)(.*?)(<!-- AUTO:" + zone + r"-END -->)",
        re.DOTALL,
    )
    if not pattern.search(text):
        print(f"FAIL: AUTO:{zone} markers missing from index.html")
        sys.exit(1)
    return pattern.sub(lambda m: m.group(1) + inner + m.group(3), text, count=1)


def gradle_props() -> tuple[str, str]:
    props = (REPO / "gradle.properties").read_text()
    version = re.search(r"^projectVersionName=(.+)$", props, re.M).group(1).strip()
    code = re.search(r"^projectVersionCode=(\d+)$", props, re.M).group(1).strip()
    return version, code


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dist", default="dist", help="folder with release assets (apk/flex/SHA256SUMS.txt)")
    args = ap.parse_args()
    dist = Path(args.dist).resolve()

    version, code = gradle_props()
    apk_name = f"DRS-Smart-Keyboard-v{version}.apk"
    apk = dist / apk_name
    if not apk.exists():
        print(f"FAIL: {apk_name} missing from {dist}")
        return 1

    sums = load_sums(dist)
    verify(dist, sums)
    apk_sha = sums[apk_name]
    apk_size = apk.stat().st_size
    apk_mb = mb(apk_size)
    print(f"OK  verified {apk_name} {apk_sha[:12]}… ({apk_mb}MB)")

    # ---- 1) docs/apk/: أزل النسخ القديمة، انسخ المسماة + latest + SHA256SUMS + latest.json
    APK_DIR.mkdir(parents=True, exist_ok=True)
    for old in APK_DIR.glob("DRS-Smart-Keyboard-v*.apk"):
        old.unlink()
    shutil.copy2(apk, APK_DIR / apk_name)
    shutil.copy2(apk, APK_DIR / "DRS-Smart-Keyboard-latest.apk")
    shutil.copy2(dist / "SHA256SUMS.txt", APK_DIR / "SHA256SUMS.txt")
    release_date = subprocess.run(
        ["git", "log", "-1", "--format=%cs", f"v{version}"],
        cwd=REPO, capture_output=True, text=True,
    ).stdout.strip() or subprocess.run(
        ["git", "log", "-1", "--format=%cs"], cwd=REPO, capture_output=True, text=True
    ).stdout.strip()
    latest = {
        "version": version,
        "versionCode": int(code),
        "tag": f"v{version}",
        "apkVersioned": apk_name,
        "apkLatest": "DRS-Smart-Keyboard-latest.apk",
        "sha256": apk_sha,
        "sizeBytes": apk_size,
        "publishedAt": release_date,
        "source": "GitHub Releases (verified SHA-256)",
    }
    (APK_DIR / "latest.json").write_text(json.dumps(latest, ensure_ascii=False, indent=2) + "\n")
    print("OK  docs/apk/ refreshed + latest.json")

    # ---- 2) docs/flex/: أزل القديمة، انسخ الجديدة + flex.json من packages/manifest.json
    flex_files = sorted(dist.glob("pkg-*.flex"))
    if not flex_files:
        print("FAIL: no pkg-*.flex in dist")
        return 1
    FLEX_DIR.mkdir(parents=True, exist_ok=True)
    for old in FLEX_DIR.glob("pkg-*.flex"):
        old.unlink()
    entries = []
    manifest_path = REPO / "packages" / "manifest.json"
    manifest = json.loads(manifest_path.read_text()) if manifest_path.exists() else None
    for f in flex_files:
        shutil.copy2(f, FLEX_DIR / f.name)
        meta = None
        if manifest:
            for p in manifest["packages"]:
                if f.name == f"pkg-{p['package_id'].rsplit('.', 1)[1]}-v{p['version']}.flex":
                    meta = p
                    break
        if meta is None:
            print(f"FAIL: {f.name} has no entry in packages/manifest.json — refuse to host unlisted package")
            return 1
        if sha256_of(FLEX_DIR / f.name) != meta["sha256"]:
            print(f"FAIL: {f.name} sha mismatch vs packages/manifest.json")
            return 1
        entries.append({"file": f.name, "meta": meta, "size": f.stat().st_size})
    flex_doc = {
        "$schema": "site/flex-listing/v1",
        "generatedFrom": "packages/manifest.json",
        "count": len(entries),
        "packages": [
            {
                "packageId": e["meta"]["package_id"],
                "file": e["file"],
                "name": e["meta"]["name"],
                "description": e["meta"]["description"],
                "version": e["meta"]["version"],
                "type": e["meta"]["type"],
                "sizeBytes": e["size"],
                "sha256": e["meta"]["sha256"],
                "colors": e["meta"].get("colors", []),
                "minAppVersion": e["meta"].get("min_app_version", "1.0.0"),
            }
            for e in entries
        ],
    }
    (FLEX_DIR / "flex.json").write_text(json.dumps(flex_doc, ensure_ascii=False, indent=2) + "\n")
    print(f"OK  docs/flex/ refreshed ({len(entries)} packages, all verified)")

    # ---- 3) إعادة كتابة مناطق AUTO في index.html
    page = INDEX.read_text()

    page = replace_zone(page, "BADGE",
        f'<span class="badge"><span class="dot"></span> الإصدار v{version} · مستقر · versionCode {code}</span>\n      ')

    page = replace_zone(page, "HERO", f'''      <div class="ctas">
        <a class="btn primary" href="apk/DRS-Smart-Keyboard-latest.apk" download>
          ⬇ تنزيل APK — v{version} ({apk_mb}MB)
        </a>
        <a class="btn ghost" href="https://github.com/CTO-DRS/DRS-SmartKeyboard/releases">كل الإصدارات والثيمات</a>
      </div>
      ''')

    page = replace_zone(page, "DLCARD", f'''        <div class="apk-name">{apk_name}</div>
        <div class="meta">{apk_mb}MB · حزمة com.drs.smartkeyboard · أندرويد 8.0 فأحدث</div>
        <div class="ctas">
          <a class="btn primary" href="apk/DRS-Smart-Keyboard-latest.apk" download>⬇ تنزيل من الموقع مباشرة</a>
          <a class="btn ghost" href="https://github.com/CTO-DRS/DRS-SmartKeyboard/releases/download/v{version}/{apk_name}" download>مصدر بديل: إصدارات GitHub</a>
        </div>
        <details class="sha">
          <summary>التحقق من السلامة — SHA-256</summary>
          <code>{apk_sha}</code>
        </details>
        <details class="sha">
          <summary>التحقق من الجهاز (اختياري)</summary>
          <code>certutil -hashfile {apk_name} SHA256</code>
        </details>
        ''')

    cards = []
    for e in entries:
        m = e["meta"]
        dots = "".join(f'<i style="background:{html.escape(c)}"></i>' for c in m.get("colors", []))
        cards.append(f'''        <div class="card theme-card">
          <div class="dots">{dots}</div>
          <h3>{html.escape(m["name"])}</h3>
          <p>{html.escape(m["description"])}</p>
          <div class="theme-meta">{e["file"]} · v{m["version"]} · {kb(e["size"])}KB</div>
          <a class="btn ghost theme-dl" href="flex/{e["file"]}" download>⬇ تنزيل الحزمة</a>
        </div>''')
    page = replace_zone(page, "THEMES", "\n".join(cards) + "\n        ")

    INDEX.write_text(page)
    print("OK  index.html AUTO zones rewritten")
    return 0


if __name__ == "__main__":
    sys.exit(main())
