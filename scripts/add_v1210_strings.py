#!/usr/bin/env python3
"""DRS v1.21.0 — adds the new AR/EN string keys idempotently.
Arabic IS the default values/strings.xml; values-en/strings.xml is English.
Run from repo root: python3 scripts/add_v1210_strings.py
"""
import re, sys, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# key -> (arabic, english)
NEW = {
    # --- the notification-style edit window (the v1.11 promise, shipped now) ---
    "clip__notification_channel": (
        "نافذة التعديل المنبثقة",
        "Floating edit window",
    ),
    "clip__notification_title": (
        "تعديل النص المنسوخ",
        "Edit the copied text",
    ),
    "pref__clipboard__edit_notification__label": (
        "إشعار «تعديل» عند النسخ",
        "«Edit» notification on copy",
    ),
    "pref__clipboard__edit_notification__summary": (
        "عند نسخ نص جديد يظهر إشعار فوري بزر «تعديل» يفتح نافذة التعديل العائمة فوق الشاشة — خارج لوحة المفاتيح. النصوص الحساسة لا تظهر في الإشعارات أبدًا.",
        "When new text is copied a heads-up notification with an «Edit» action opens the floating editor above the screen — outside the keyboard panel. Sensitive text never appears in notifications.",
    ),
    # --- the directional/joining mark insertion tools ---
    "drs__text_tools__section_marks": (
        "علامات الاتجاه والوصل",
        "Direction & join marks",
    ),
    "drs__text_tools__tool_insert_rlm": (
        "إدراج RLM",
        "Insert RLM",
    ),
    "drs__text_tools__desc_insert_rlm": (
        "يضيف علامة يمين-إلى-يسار غير مرئية عند المؤشر لتثبيت اتجاه النص المختلط",
        "Adds an invisible right-to-left mark at the cursor to pin mixed-text direction",
    ),
    "drs__text_tools__tool_insert_lrm": (
        "إدراج LRM",
        "Insert LRM",
    ),
    "drs__text_tools__desc_insert_lrm": (
        "يضيف علامة يسار-إلى-يمين غير مرئية عند المؤشر لتثبيت اتجاه النص المختلط",
        "Adds an invisible left-to-right mark at the cursor to pin mixed-text direction",
    ),
    "drs__text_tools__tool_insert_zwj": (
        "إدراج ZWJ (واصل)",
        "Insert ZWJ (joiner)",
    ),
    "drs__text_tools__desc_insert_zwj": (
        "يضيف واصلاً غير مرئيًا عند المؤشر — يصل الحروف والإيموجي في تسلسلات مركبة",
        "Adds an invisible joiner at the cursor — joins letters and emoji into compound sequences",
    ),
    "drs__text_tools__tool_insert_zwnj": (
        "إدراج نصف المسافة (ZWNJ)",
        "Insert ZWNJ (non-joiner)",
    ),
    "drs__text_tools__desc_insert_zwnj": (
        "يضيف نصف مسافة غير مرئي عند المؤشر — يفصل اتصال الحروف دون مسافة مرئية",
        "Adds an invisible non-joiner at the cursor — breaks the cursive join with no visible space",
    ),
    # --- backup: the user dictionary rides the archive ---
    "backup_and_restore__back_up__files_user_dictionary": (
        "قاموس المستخدم (الكلمات المتعلمة)",
        "User dictionary (learned words)",
    ),
    # --- emoji palette: skin tone selector + clear-search label ---
    "emoji__skin_tone__cd": (
        "اختيار لون البشرة",
        "Choose skin tone",
    ),
    "emoji__search__clear": (
        "مسح البحث",
        "Clear search",
    ),
}

PATHS = {
    "ar": os.path.join(ROOT, "app/src/main/res/values/strings.xml"),
    "en": os.path.join(ROOT, "app/src/main/res/values-en/strings.xml"),
}

COMMENTS = {
    "clip__notification_channel": "Notification channel of the floating edit window",
    "clip__notification_title": "Title of the edit notification",
    "pref__clipboard__edit_notification__label": "Preference title",
    "pref__clipboard__edit_notification__summary": "Preference summary",
    "drs__text_tools__section_marks": "Text tools panel section title",
    "drs__text_tools__tool_insert_rlm": "Text tool label",
    "drs__text_tools__desc_insert_rlm": "Text tool description",
    "drs__text_tools__tool_insert_lrm": "Text tool label",
    "drs__text_tools__desc_insert_lrm": "Text tool description",
    "drs__text_tools__tool_insert_zwj": "Text tool label",
    "drs__text_tools__desc_insert_zwj": "Text tool description",
    "drs__text_tools__tool_insert_zwnj": "Text tool label",
    "drs__text_tools__desc_insert_zwnj": "Text tool description",
    "backup_and_restore__back_up__files_user_dictionary": "Backup files selector item",
    "emoji__skin_tone__cd": "Accessibility label of the skin tone selector",
    "emoji__search__clear": "Accessibility label of the emoji search clear button",
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
