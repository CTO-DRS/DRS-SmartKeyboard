#!/usr/bin/env python3
"""DRS v1.23.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1230_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the built-in voice dictation (الإملاء الصوتي المدمج) ---
    "voice__listening": (
        "تحدث الآن…",
        "Speak now…",
    ),
    "voice__cancel": (
        "إيقاف الاستماع",
        "Stop listening",
    ),
    "voice__disabled_sensitive": (
        "الإملاء الصوتي معطل في حقول كلمات المرور ووضع التخفي",
        "Voice dictation is disabled in password fields and incognito mode",
    ),
    "voice__permission_denied": (
        "رُفض إذن الميكروفون — فعّله من إعدادات النظام لاستخدام الإملاء الصوتي",
        "Microphone permission denied — enable it in system settings to use voice dictation",
    ),
    "voice__error": (
        "تعذر الإملاء الصوتي — حاول مجددًا",
        "Voice dictation failed — try again",
    ),
    "voice__error_no_match": (
        "لم يُتعرف على كلام — حاول مجددًا",
        "No speech recognized — try again",
    ),
    "voice__error_no_speech": (
        "لم نسمع شيئًا — تقرّب من الميكروفون",
        "Heard nothing — move closer to the microphone",
    ),
    # --- the modifier family face (وجه الموديفايرات) ---
    "key__ctrl": (
        "Ctrl",
        "Ctrl",
    ),
    "key__alt": (
        "Alt",
        "Alt",
    ),
    "key__fn": (
        "Fn",
        "Fn",
    ),
    # --- the kaomoji palette (لوحة الكاوموجي) ---
    "media__emoticons_toggle": (
        "لوحة الكاوموجي",
        "Kaomoji palette",
    ),
    # --- the FN tech-toolbar tile (بلاطة Fn في شريط التقني) ---
    "drs__unified__tool_fn": (
        "مفتاح Fn",
        "Fn key",
    ),
    "drs__unified__tool_fn_desc": (
        "النابض الأخير: أثناء التفعيل تصبح الأرقام مفاتيح F1–F10 حقيقية للطرفية وسطح المكتب البعيد",
        "The last latch: while armed, digits become real F1–F10 keys for terminals and remote desktop",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "voice__listening": "Hint shown in the dictation bar before any partial text arrives",
    "voice__cancel": "Accessibility label of the dictation-bar cancel button",
    "voice__disabled_sensitive": "Toast when the mic key is pressed in a password/incognito context",
    "voice__permission_denied": "Toast in the permission trampoline when RECORD_AUDIO is denied",
    "voice__error": "Generic recognizer failure shown in the dictation bar",
    "voice__error_no_match": "Recognizer finished but produced no transcript",
    "voice__error_no_speech": "Recognizer timed out without hearing speech",
    "key__ctrl": "Visible label of CTRL keys declared in custom layouts",
    "key__alt": "Visible label of ALT keys declared in custom layouts",
    "key__fn": "Visible label of FN keys declared in custom layouts",
    "media__emoticons_toggle": "Accessibility label of the kaomoji/emoji palette toggle",
    "drs__unified__tool_fn": "Unified tools screen: name of the Fn tile",
    "drs__unified__tool_fn_desc": "Unified tools screen: description of the Fn tile",
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
