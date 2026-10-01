#!/usr/bin/env python3
# DRS v1.19.0: idempotent AR/EN string keys appender (appends before </resources>).
# The world-class audit round: real spell-check typo marking, emoji search
# un-broken with localized metadata, split/merge keyboard toggles, async
# media thumbnails, honest i18n sweep and the learned-words reset.
import sys, re

AR = {
    "quick_action__split_layout": "تقسيم لوحة المفاتيح",
    "quick_action__split_layout__tooltip": "تفعيل تقسيم لوحة الحروف نصفين بفجوة وسطى للإبهامين",
    "quick_action__merge_layout": "دمج لوحة المفاتيح",
    "quick_action__merge_layout__tooltip": "العودة إلى لوحة الحروف الكاملة المدمجة",
    "quick_action__insert_text_tooltip": "إدراج النص «{text}»",
    "keyboard__split_layout_toast_split": "تم تفعيل تقسيم لوحة المفاتيح",
    "keyboard__split_layout_toast_merge": "تم دمج لوحة المفاتيح",
    "drs__unified__tool_split_keyboard": "تقسيم اللوحة",
    "drs__unified__tool_split_keyboard_desc": "تقسيم لوحة الحروف نصفين بفجوة وسطى تريح الإبهامين على الشاشات العريضة",
    "drs__unified__tool_merge_keyboard": "دمج اللوحة",
    "drs__unified__tool_merge_keyboard_desc": "العودة إلى لوحة الحروف الكاملة المدمجة بلا فجوة",
    "pref__spelling__typo_flagging_enabled__label": "تحديد الأخطاء الإملائية",
    "pref__spelling__typo_flagging_enabled__summary": "وضع خط أحمر تحت الكلمة المكتوبة خطأً مع اقتراح التصحيح عند اللمس — يعمل بالعربية والإنجليزية بوابة محافظة، ويمكن إيقافه كليًا",
    "clipboard__filter_text": "نص",
    "clipboard__filter_images": "صور",
    "clipboard__filter_videos": "فيديو",
    "clipboard__media_unresolvable": "تعذر تحميل الوسائط",
    "clipboard__paste_failed": "فشل لصق العنصر.",
    "clip__a11y_close": "إغلاق",
    "clip__a11y_share": "مشاركة",
    "clip__a11y_save_file": "حفظ كملف",
    "clip__a11y_prev": "المطابقة السابقة",
    "clip__a11y_next": "المطابقة التالية",
    "clip__a11y_clear_find": "مسح البحث",
    "settings__udm__clear_dictionary": "مسح القاموس",
    "settings__udm__clear_dictionary_confirm": "سيُمسح كل كلمتك الشخصية المتعلمة نهائيًا من قاموس التطبيق الداخلي. لا يمكن التراجع — هل تريد المتابعة؟",
    "settings__udm__clear_dictionary_success": "تم مسح القاموس الداخلي",
    "enum__swipe_action__show_subtype_picker": "إظهار منتقي اللغات",
}

EN = {
    "quick_action__split_layout": "Split keyboard",
    "quick_action__split_layout__tooltip": "Split the character keyboard into thumb-friendly halves with a central gap",
    "quick_action__merge_layout": "Merge keyboard",
    "quick_action__merge_layout__tooltip": "Return to the classic full merged character keyboard",
    "quick_action__insert_text_tooltip": "Insert text \u2018{text}\u2019",
    "keyboard__split_layout_toast_split": "Keyboard split enabled",
    "keyboard__split_layout_toast_merge": "Keyboard merged",
    "drs__unified__tool_split_keyboard": "Split keyboard",
    "drs__unified__tool_split_keyboard_desc": "Split the character keyboard into thumb-friendly halves with a central gap on wide screens",
    "drs__unified__tool_merge_keyboard": "Merge keyboard",
    "drs__unified__tool_merge_keyboard_desc": "Return to the classic full merged keyboard without the gap",
    "pref__spelling__typo_flagging_enabled__label": "Flag spelling mistakes",
    "pref__spelling__typo_flagging_enabled__summary": "Underline misspelled words in red with tap-to-fix suggestions — conservative gates for Arabic and English, can be switched off entirely",
    "clipboard__filter_text": "Text",
    "clipboard__filter_images": "Images",
    "clipboard__filter_videos": "Videos",
    "clipboard__media_unresolvable": "Unable to load media",
    "clipboard__paste_failed": "Failed to paste item.",
    "clip__a11y_close": "Close",
    "clip__a11y_share": "Share",
    "clip__a11y_save_file": "Save as file",
    "clip__a11y_prev": "Previous match",
    "clip__a11y_next": "Next match",
    "clip__a11y_clear_find": "Clear find",
    "settings__udm__clear_dictionary": "Clear dictionary",
    "settings__udm__clear_dictionary_confirm": "Every personal learned word will be permanently wiped from the app's internal dictionary. This cannot be undone — continue?",
    "settings__udm__clear_dictionary_success": "Internal dictionary cleared",
    "enum__swipe_action__show_subtype_picker": "Show subtype picker",
}

FILES = {
    "app/src/main/res/values/strings.xml": AR,
    "app/src/main/res/values-en/strings.xml": EN,
}

def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")

fail = False
for path, table in FILES.items():
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    added = 0
    for key, value in table.items():
        if re.search(r'<string name="%s"' % re.escape(key), content):
            print(f"SKIP {key} (exists) in {path}")
            continue
        entry = f'    <string name="{key}">{esc(value)}</string>\n'
        idx = content.rfind("</resources>")
        if idx == -1:
            print(f"FATAL: no </resources> in {path}")
            fail = True
            break
        content = content[:idx] + entry + content[idx:]
        added += 1
    if not fail:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"OK {path}: +{added} keys")

# ── DRS v1.23.0: unified AR/EN parity gate (بوابة التوازي الموحدة) ──
# Every string script ends with the same honest gate: the AR and EN key
# sets must be identical or the run fails loudly. The older scripts
# shipped without it — appended here by the v1.23.0 round, replacing the
# old exit so the gate is the LAST word on the exit code.
_parity_paths = ("app/src/main/res/values/strings.xml", "app/src/main/res/values-en/strings.xml")
_parity_names = {}
for _path in _parity_paths:
    with open(_path, encoding="utf-8") as _fh:
        _parity_names[_path] = set(re.findall(r'<string name="([^"]+)"', _fh.read()))
_only_ar = _parity_names[_parity_paths[0]] - _parity_names[_parity_paths[1]]
_only_en = _parity_names[_parity_paths[1]] - _parity_names[_parity_paths[0]]
print(f"parity: ar-only={len(_only_ar)} en-only={len(_only_en)}")
if _only_ar or _only_en:
    print("AR-ONLY:", sorted(_only_ar)[:10])
    print("EN-ONLY:", sorted(_only_en)[:10])
    fail = True
sys.exit(1 if fail else 0)
