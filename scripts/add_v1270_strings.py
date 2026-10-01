#!/usr/bin/env python3
"""DRS v1.27.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1270_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the listening state joins the theme surface (حالة الاستماع في الثيمات) ---
    "settings__theme_editor__rule_window_mode": (
        "نمط نافذة الإدخال المستهدف",
        "Target ime window mode",
    ),
    "settings__theme_editor__rule_voice": (
        "حالة الاستماع",
        "Listening state",
    ),
    "settings__theme_editor__rule_voice_value": (
        "شريط الاستماع",
        "Listening bar",
    ),
    # --- the popup clipboard editor hears (المحرر المنبثق يسمع) ---
    "clip__dictation_start": (
        "بدء الإملاء",
        "Start dictation",
    ),
    "clip__dictation_stop": (
        "إيقاف الإملاء",
        "Stop dictation",
    ),
    "clip__dictation_hint": (
        "تحدث الآن…",
        "Speak now…",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "settings__theme_editor__rule_window_mode": "Theme editor: section title for the target ime window mode attribute",
    "settings__theme_editor__rule_voice": "Theme editor: section title for the smartbar listening-state attribute",
    "settings__theme_editor__rule_voice_value": "Theme editor: chip label adding voice=true to a smartbar rule",
    "clip__dictation_start": "Accessibility label of the popup editor's dictate button while idle",
    "clip__dictation_stop": "Accessibility label of the popup editor's dictate button while listening",
    "clip__dictation_hint": "Popup editor dictation strip shown before any partial transcript arrives",
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
