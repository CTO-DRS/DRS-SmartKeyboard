#!/usr/bin/env python3
"""DRS v1.22.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1220_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the honest pin cap of the clipboard (سقف التثبيتات) ---
    "clipboard__pin_cap_toast": (
        "سقف التثبيتات بلغ حده — ألغِ تثبيت عنصر لإضافة غيره",
        "Pin cap reached — unpin an item to pin another",
    ),
    "pref__clipboard__pinned_max_size__label": (
        "سقف العناصر المثبتة",
        "Pinned items cap",
    ),
    # (no summary key: DialogSliderPreference renders valueLabel, not summary —
    # an orphan translation would violate the no-dead-strings rule)
    # --- the pinned-tasks drag reorder hint (إعادة الترتيب بالسحب) ---
    "drs__tools_drawer__drag_hint": (
        "اضغط مطولًا على مهمة واسحبها لإعادة ترتيبها",
        "Long-press a task and drag to reorder",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "clipboard__pin_cap_toast": "Toast shown when a new pin exceeds the pinned items cap",
    "pref__clipboard__pinned_max_size__label": "Preference title",
    "pref__clipboard__pinned_max_size__summary": "Preference summary",
    "drs__tools_drawer__drag_hint": "Hint under the pinned tasks section of the tools drawer",
}

def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'").replace('"', '\\"')

def main():
    for lang, path in PATHS.items():
        with open(path, encoding="utf-8") as fh:
            src = fh.read()
        added, skipped = 0, 0
        before = len(re.findall(r"<string name=", src))
        insertions = []
        for key, (ar, en) in NEW.items():
            if f'name="{key}"' in src:
                skipped += 1
                continue
            value = ar if lang == "ar" else en
            comment = COMMENTS.get(key, "")
            insertions.append(f'    <string name="{key}" comment="{comment}">{esc(value)}</string>\n')
            added += 1
        if insertions:
            # Insert before the closing resources tag.
            idx = src.rstrip().rfind("</resources>")
            src = src[:idx] + "".join(insertions) + "\n" + src[idx:]
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(src)
        after = len(re.findall(r"<string name=", src))
        print(f"{lang}: +{added} skipped={skipped} strings={before} -> {after}")

    # Parity check
    names = {}
    for lang, path in PATHS.items():
        with open(path, encoding="utf-8") as fh:
            names[lang] = set(re.findall(r'<string name="([^"]+)"', fh.read()))
    only_ar = names["ar"] - names["en"]
    only_en = names["en"] - names["ar"]
    print(f"parity: ar-only={len(only_ar)} en-only={len(only_en)}")
    if only_ar or only_en:
        print("AR-ONLY:", sorted(only_ar)[:10])
        print("EN-ONLY:", sorted(only_en)[:10])
        sys.exit(1)

if __name__ == "__main__":
    main()
