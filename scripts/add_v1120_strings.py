#!/usr/bin/env python3
# DRS v1.12.0: idempotent AR/EN string keys appender (appends before </resources>).
# The complete smart clipboard system: colored search-result cards with
# navigation, code-line detection, the 500,000-character support, the
# in-app clipboard settings, and the comprehensive panel organization
# (sort orders, calendar sections, category badges).
import sys, re

AR = {
    # Panel calendar sections.
    "clipboard__group_today": "اليوم",
    "clipboard__group_yesterday": "أمس",
    "clipboard__group_this_week": "هذا الأسبوع",
    "clipboard__group_this_month": "هذا الشهر",
    "clipboard__group_older": "أقدم",
    # Editor results panel.
    "clip__results_title": "النتائج ({count})",
    "clip__results_show": "إظهار النتائج",
    "clip__results_hide": "إخفاء النتائج",
    "clip__result_line": "سطر {line}",
    "clipboard__search_results_count": "{count} نتيجة",
    # Code detection.
    "clip__code_badge": "كود {lang} — {code} من {total} سطرًا برمجيًا",
    "clip__lang_kotlin_java": "Kotlin/Java",
    "clip__lang_python": "Python",
    "clip__lang_javascript": "JavaScript",
    "clip__lang_json": "JSON",
    "clip__lang_html_xml": "HTML/XML",
    "clip__lang_css": "CSS",
    "clip__lang_sql": "SQL",
    "clip__lang_c_cpp": "C/C++",
    "clip__lang_bash": "Bash",
    "clip__lang_unknown": "غير معروفة",
    "clip__transform_number_lines": "ترقيم الأسطر",
    "clip__transform_extract_code": "استخراج الكود",
    "clip__transform_remove_code": "حذف الأسطر البرمجية",
    "clip__large_text_warning": "نص ضخم — قد يتباطأ التحرير قليلًا",
    # Category badges.
    "clip__category_url": "رابط",
    "clip__category_email": "بريد",
    "clip__category_phone": "رقم هاتف",
    "clip__category_code": "كود",
    "clip__category_text": "نص",
    # Panel sort chips.
    "clip__sort_newest": "الأحدث",
    "clip__sort_oldest": "الأقدم",
    "clip__sort_longest": "الأطول",
    "clip__sort_shortest": "الأقصر",
    # Settings — the smart editor & results group.
    "pref__clipboard__group_smart_editor__label": "المحرر الذكي والنتائج",
    "pref__clipboard__editor_char_limit__label": "سقف أحرف المحرر",
    "pref__clipboard__editor_char_limit__summary": "الحد الأقصى لعدد أحرف النص الواحد أثناء التحرير",
    "enum__clip_editor_char_limit__fifty_k": "50 ألف حرف",
    "enum__clip_editor_char_limit__hundred_k": "100 ألف حرف",
    "enum__clip_editor_char_limit__two_fifty_k": "250 ألف حرف",
    "enum__clip_editor_char_limit__five_hundred_k": "500 ألف حرف",
    "pref__clipboard__search_result_cards__label": "بطاقات نتائج البحث الملونة",
    "pref__clipboard__search_result_cards__summary": "إظهار نتائج البحث كبطاقات ملونة قابلة للتنقل",
    "pref__clipboard__auto_results_panel__label": "فتح لوحة النتائج تلقائيًا",
    "pref__clipboard__auto_results_panel__summary": "إظهار بطاقات النتائج فور بدء البحث داخل المحرر",
    "pref__clipboard__code_detection__label": "كشف الأسطر البرمجية",
    "pref__clipboard__code_detection__summary": "شارة الكود وخط أحادي المسافة تلقائيًا ورقاقات استخراج الكود",
    "pref__clipboard__editor_font__label": "خط المحرر الافتراضي",
    "pref__clipboard__editor_font_size__label": "حجم خط المحرر الافتراضي",
    "pref__clipboard__match_case_by_default__label": "مطابقة حالة الأحرف افتراضيًا",
    "pref__clipboard__match_case_by_default__summary": "يبدأ البحث في المحرر بمطابقة الحالة مفعّلة",
    "pref__clipboard__history_sort__label": "ترتيب سجل الحافظة",
    "pref__clipboard__history_sort__summary": "ترتيب العناصر داخل لوحة الحافظة",
    "enum__clip_history_sort__newest": "الأحدث أولًا",
    "enum__clip_history_sort__oldest": "الأقدم أولًا",
    "enum__clip_history_sort__longest": "الأطول أولًا",
    "enum__clip_history_sort__shortest": "الأقصر أولًا",
}

EN = {
    # Panel calendar sections.
    "clipboard__group_today": "Today",
    "clipboard__group_yesterday": "Yesterday",
    "clipboard__group_this_week": "This week",
    "clipboard__group_this_month": "This month",
    "clipboard__group_older": "Older",
    # Editor results panel.
    "clip__results_title": "Results ({count})",
    "clip__results_show": "Show results",
    "clip__results_hide": "Hide results",
    "clip__result_line": "Line {line}",
    "clipboard__search_results_count": "{count} results",
    # Code detection.
    "clip__code_badge": "{lang} code — {code} of {total} code lines",
    "clip__lang_kotlin_java": "Kotlin/Java",
    "clip__lang_python": "Python",
    "clip__lang_javascript": "JavaScript",
    "clip__lang_json": "JSON",
    "clip__lang_html_xml": "HTML/XML",
    "clip__lang_css": "CSS",
    "clip__lang_sql": "SQL",
    "clip__lang_c_cpp": "C/C++",
    "clip__lang_bash": "Bash",
    "clip__lang_unknown": "Unknown",
    "clip__transform_number_lines": "Number lines",
    "clip__transform_extract_code": "Extract code",
    "clip__transform_remove_code": "Remove code lines",
    "clip__large_text_warning": "Huge text — editing may slow down slightly",
    # Category badges.
    "clip__category_url": "Link",
    "clip__category_email": "Email",
    "clip__category_phone": "Phone number",
    "clip__category_code": "Code",
    "clip__category_text": "Text",
    # Panel sort chips.
    "clip__sort_newest": "Newest",
    "clip__sort_oldest": "Oldest",
    "clip__sort_longest": "Longest",
    "clip__sort_shortest": "Shortest",
    # Settings — the smart editor & results group.
    "pref__clipboard__group_smart_editor__label": "Smart editor & results",
    "pref__clipboard__editor_char_limit__label": "Editor character limit",
    "pref__clipboard__editor_char_limit__summary": "Maximum characters for a single text while editing",
    "enum__clip_editor_char_limit__fifty_k": "50K characters",
    "enum__clip_editor_char_limit__hundred_k": "100K characters",
    "enum__clip_editor_char_limit__two_fifty_k": "250K characters",
    "enum__clip_editor_char_limit__five_hundred_k": "500K characters",
    "pref__clipboard__search_result_cards__label": "Colored search result cards",
    "pref__clipboard__search_result_cards__summary": "Show search results as navigable colored cards",
    "pref__clipboard__auto_results_panel__label": "Auto-open results panel",
    "pref__clipboard__auto_results_panel__summary": "Show the result cards as soon as the editor search starts",
    "pref__clipboard__code_detection__label": "Detect programming lines",
    "pref__clipboard__code_detection__summary": "Code badge, automatic monospace font, and code extract chips",
    "pref__clipboard__editor_font__label": "Default editor font",
    "pref__clipboard__editor_font_size__label": "Default editor font size",
    "pref__clipboard__match_case_by_default__label": "Match case by default",
    "pref__clipboard__match_case_by_default__summary": "Editor search starts with case matching enabled",
    "pref__clipboard__history_sort__label": "Clipboard history sort",
    "pref__clipboard__history_sort__summary": "Order of items inside the clipboard panel",
    "enum__clip_history_sort__newest": "Newest first",
    "enum__clip_history_sort__oldest": "Oldest first",
    "enum__clip_history_sort__longest": "Longest first",
    "enum__clip_history_sort__shortest": "Shortest first",
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

# PARITY check.
counts = {}
for path in FILES:
    with open(path, "r", encoding="utf-8") as f:
        counts[path] = len(re.findall(r"<string name=", f.read()))
values = list(counts.values())
if len(set(values)) != 1:
    print(f"PARITY FAIL: {counts}")
    fail = True
else:
    print(f"PARITY OK: {values[0]} keys per language")

# ── DRS v1.25.0: unified AR/EN parity gate (بوابة التوازي الموحدة) ──
# The last two string scripts still on the old count-equality check —
# this gate is the same honest AR/EN set-difference every other script
# ends with, so a missing key in either language now fails loudly with
# the offending names printed.
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
