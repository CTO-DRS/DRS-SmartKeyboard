#!/usr/bin/env python3
"""DRS v1.25.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1250_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the second voice gate: which recognizer may listen (بوابة المتعرّف الثانية) ---
    "pref__voice__recognizer_mode__label": (
        "وضع المتعرّف الصوتي",
        "Speech recognizer mode",
    ),
    # NOTE: no __summary key — jetpref's ListPreference derives its
    # summary from the selected entry, so a summary string would be an
    # orphan (the exact v1.22.0 lesson).
    "enum__voice_recognizer_mode__auto": (
        "تلقائي (على الجهاز إن توفر)",
        "Auto (on-device when available)",
    ),
    "enum__voice_recognizer_mode__on_device_only": (
        "على الجهاز فقط (بلا شبكة)",
        "On-device only (no network)",
    ),
    "enum__voice_recognizer_mode__standard": (
        "الخدمة القياسية",
        "Standard service",
    ),
    "voice__on_device_unavailable": (
        "التعرف الصوتي على الجهاز غير متوفر على هذا النظام، والوضع الصارم مفعّل",
        "On-device speech recognition is unavailable on this ROM and strict mode is on",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "pref__voice__recognizer_mode__label": "Typing settings: list preference choosing which recognizer may listen",
    "enum__voice_recognizer_mode__auto": "Recognizer mode: automatic (on-device preferred when the ROM offers it)",
    "enum__voice_recognizer_mode__on_device_only": "Recognizer mode: strictly on-device, no network-backed service",
    "enum__voice_recognizer_mode__standard": "Recognizer mode: the classic standard recognizer",
    "voice__on_device_unavailable": "Toast when the strict on-device mode is on but the ROM has no on-device recognizer",
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
