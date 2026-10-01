#!/usr/bin/env python3
"""DRS v1.24.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1240_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the voice dictation settings gate (بوابة الإملاء الصوتي) ---
    "pref__voice__title": (
        "الإدخال الصوتي",
        "Voice input",
    ),
    "pref__voice__enabled__label": (
        "الإملاء الصوتي المدمج",
        "Built-in voice dictation",
    ),
    "pref__voice__enabled__summary": (
        "اسمح لمفتاح الميكروفون بالإملاء المباشر عبر محرك التعرّف المدمج في الجهاز — الكلام لا يمر عبر خوادمنا إطلاقًا، وحقول كلمات المرور ووضع التخفي معفيان دائمًا",
        "Let the microphone key dictate directly through the on-device recognizer — speech never touches our servers, and password fields and incognito mode are always exempt",
    ),
    "voice__disabled_by_setting": (
        "الإملاء الصوتي معطل من إعدادات الكتابة",
        "Voice dictation is disabled in typing settings",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "pref__voice__title": "Typing settings: title of the voice-input preference group",
    "pref__voice__enabled__label": "Typing settings: switch that gates the built-in dictation",
    "pref__voice__enabled__summary": "Typing settings: privacy summary of the dictation switch",
    "voice__disabled_by_setting": "Toast when the mic key is pressed while the user disabled dictation in settings",
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
