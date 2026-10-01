#!/usr/bin/env python3
# DRS v1.10.0: idempotent AR/EN string keys appender (appends before </resources>).
# The popup smart clipboard editor: find/replace with match navigation,
# sixteen smart transform/extract algorithms, font customization, and
# sharing from the item ladder.
import sys, re

AR = {
    "clip__share_item": "مشاركة النص",
    "clip__editor_title": "محرر النص الذكي",
    "clip__editor_find_hint": "ابحث في النص…",
    "clip__editor_replace_hint": "الاستبدال بـ…",
    "clip__editor_match_case": "مطابقة الحالة",
    "clip__editor_matches_counter": "{active} من {count}",
    "clip__editor_no_matches": "لا نتائج",
    "clip__editor_replace_one": "استبدال",
    "clip__editor_replace_all": "استبدال الكل",
    "clip__editor_replace_done": "تم استبدال {count} موضعًا",
    "clip__transform_trim": "تشذيب الأسطر",
    "clip__transform_collapse": "ضغط المسافات",
    "clip__transform_remove_empty": "حذف الأسطر الفارغة",
    "clip__transform_dedupe": "إزالة التكرار",
    "clip__transform_sort_asc": "ترتيب تصاعدي",
    "clip__transform_sort_desc": "ترتيب تنازلي",
    "clip__transform_reverse": "عكس الأسطر",
    "clip__transform_no_diacritics": "إزالة التشكيل",
    "clip__transform_normalize": "توحيد الحروف",
    "clip__extract_urls": "استخراج الروابط",
    "clip__extract_emails": "استخراج الإيميلات",
    "clip__extract_phones": "استخراج الأرقام",
    "clip__extract_done": "تم استخراج {count}",
    "clip__extract_none": "لا يوجد ما يُستخرج",
    "clip__font_default": "افتراضي",
    "clip__font_sans": "سنسريف",
    "clip__font_serif": "سريف",
    "clip__font_mono": "أحادي المسافة",
    "clip__font_cursive": "مخطوط",
}

EN = {
    "clip__share_item": "Share text",
    "clip__editor_title": "Smart text editor",
    "clip__editor_find_hint": "Find in text…",
    "clip__editor_replace_hint": "Replace with…",
    "clip__editor_match_case": "Case sensitive",
    "clip__editor_matches_counter": "{active} of {count}",
    "clip__editor_no_matches": "No matches",
    "clip__editor_replace_one": "Replace",
    "clip__editor_replace_all": "Replace all",
    "clip__editor_replace_done": "Replaced {count} occurrence(s)",
    "clip__transform_trim": "Trim lines",
    "clip__transform_collapse": "Collapse spaces",
    "clip__transform_remove_empty": "Remove empty lines",
    "clip__transform_dedupe": "Dedupe lines",
    "clip__transform_sort_asc": "Sort A→Z",
    "clip__transform_sort_desc": "Sort Z→A",
    "clip__transform_reverse": "Reverse lines",
    "clip__transform_no_diacritics": "Remove diacritics",
    "clip__transform_normalize": "Normalize letters",
    "clip__extract_urls": "Extract links",
    "clip__extract_emails": "Extract emails",
    "clip__extract_phones": "Extract phones",
    "clip__extract_done": "Extracted {count}",
    "clip__extract_none": "Nothing to extract",
    "clip__font_default": "Default",
    "clip__font_sans": "Sans",
    "clip__font_serif": "Serif",
    "clip__font_mono": "Mono",
    "clip__font_cursive": "Cursive",
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
