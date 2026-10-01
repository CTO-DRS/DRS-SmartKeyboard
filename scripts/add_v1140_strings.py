#!/usr/bin/env python3
# DRS v1.14.0: idempotent AR/EN string keys appender (appends before </resources>).
# The comprehensive settings list in the app: the edit popup window
# (surface/size/dimming), the direct line jump behavior, the code
# detection details, and the panel organization toggles.
import sys, re

AR = {
    # Group: the popup edit window.
    "pref__clipboard__group_popup_editor__label": "نافذة التعديل المنبثقة",
    "pref__clipboard__edit_route__label": "مكان فتح المحرر",
    "pref__clipboard__edit_route__summary": "هل يفتح «تعديل النص» نافذة منبثقة فوق الشاشة أم داخل لوحة الحافظة",
    "enum__clip_edit_route__popup": "نافذة منبثقة فوق الشاشة",
    "enum__clip_edit_route__panel": "داخل لوحة الحافظة",
    "pref__clipboard__popup_size__label": "حجم النافذة المنبثقة",
    "enum__clip_popup_size__compact": "مدمجة",
    "enum__clip_popup_size__normal": "عادية",
    "enum__clip_popup_size__nearly_full": "تملأ الشاشة تقريبًا",
    "pref__clipboard__popup_scrim__label": "تعتيم خلفية النافذة",
    "enum__clip_popup_scrim__none": "بلا تعتيم",
    "enum__clip_popup_scrim__light": "خفيف",
    "enum__clip_popup_scrim__normal": "متوسط",
    "enum__clip_popup_scrim__strong": "قوي",
    # Group: search & results.
    "pref__clipboard__group_search_results__label": "البحث والنتائج",
    "pref__clipboard__jump_to_line__label": "القفز إلى سطر المطابقة",
    "pref__clipboard__jump_to_line__summary": "الضغط على بطاقة النتيجة يمرر المحرر مباشرة إلى سطر المطابقة",
    "pref__clipboard__jump_center__label": "تمركز السطر عند القفز",
    "pref__clipboard__jump_center__summary": "يتمركز السطر الهدف في منتصف نافذة العرض بدل تثبيته أعلاها",
    "pref__clipboard__follow_active_card__label": "تتبع البطاقة النشطة",
    "pref__clipboard__follow_active_card__summary": "تتمرر قائمة البطاقات تلقائيًا لتبقي البطاقة النشطة ظاهرة",
    # Group: code-line detection.
    "pref__clipboard__group_code_detection__label": "كشف الأسطر البرمجية",
    "pref__clipboard__code_badge__label": "شارة الكود",
    "pref__clipboard__code_badge__summary": "إظهار لغة الكود وعدد أسطره البرمجية فوق حقل التحرير",
    "pref__clipboard__code_auto_monospace__label": "خط أحادي المسافة تلقائيًا للكود",
    "pref__clipboard__code_auto_monospace__summary": "يتبدل خط المحرر إلى الأحادي المسافة عند فتح نص برمجي",
    # Group: history organization.
    "pref__clipboard__group_history_organization__label": "تنظيم السجل",
    "pref__clipboard__calendar_sections__label": "الأقسام التقويمية",
    "pref__clipboard__calendar_sections__summary": "تقسيم السجل إلى مثبت/اليوم/أمس/هذا الأسبوع/هذا الشهر/أقدم",
    "pref__clipboard__category_badges__label": "شارات الفئات الذكية",
    "pref__clipboard__category_badges__summary": "شارة رابط/بريد/هاتف/كود على بلاطات النص",
    # Editor group addition.
    "pref__clipboard__large_text_warning__label": "تحذير النص الضخم",
    "pref__clipboard__large_text_warning__summary": "تنبيه عند وصول النص إلى الأحجام التي قد تبطئ التحرير",
    # Flat grid header when the calendar sections are off.
    "clipboard__group_flat": "الكل",
}

EN = {
    # Group: the popup edit window.
    "pref__clipboard__group_popup_editor__label": "Edit popup window",
    "pref__clipboard__edit_route__label": "Where the editor opens",
    "pref__clipboard__edit_route__summary": "Whether «Edit text» opens the floating popup window above the screen or inside the clipboard panel",
    "enum__clip_edit_route__popup": "Floating window above the screen",
    "enum__clip_edit_route__panel": "Inside the clipboard panel",
    "pref__clipboard__popup_size__label": "Popup window size",
    "enum__clip_popup_size__compact": "Compact",
    "enum__clip_popup_size__normal": "Normal",
    "enum__clip_popup_size__nearly_full": "Nearly full screen",
    "pref__clipboard__popup_scrim__label": "Window background dimming",
    "enum__clip_popup_scrim__none": "No dimming",
    "enum__clip_popup_scrim__light": "Light",
    "enum__clip_popup_scrim__normal": "Normal",
    "enum__clip_popup_scrim__strong": "Strong",
    # Group: search & results.
    "pref__clipboard__group_search_results__label": "Search & results",
    "pref__clipboard__jump_to_line__label": "Jump to the match's line",
    "pref__clipboard__jump_to_line__summary": "Tapping a result card scrolls the editor straight to the match's line",
    "pref__clipboard__jump_center__label": "Center the line on jump",
    "pref__clipboard__jump_center__summary": "The target row is centered in the viewport instead of pinned under the top edge",
    "pref__clipboard__follow_active_card__label": "Follow the active card",
    "pref__clipboard__follow_active_card__summary": "The cards list scrolls to keep the active card in sight",
    # Group: code-line detection.
    "pref__clipboard__group_code_detection__label": "Code-line detection",
    "pref__clipboard__code_badge__label": "Code badge",
    "pref__clipboard__code_badge__summary": "Show the code language and its code-line share above the editor field",
    "pref__clipboard__code_auto_monospace__label": "Automatic monospace for code",
    "pref__clipboard__code_auto_monospace__summary": "The editor font switches to monospace when a code text opens",
    # Group: history organization.
    "pref__clipboard__group_history_organization__label": "History organization",
    "pref__clipboard__calendar_sections__label": "Calendar sections",
    "pref__clipboard__calendar_sections__summary": "Split the history into pinned/today/yesterday/this week/this month/older",
    "pref__clipboard__category_badges__label": "Smart category badges",
    "pref__clipboard__category_badges__summary": "A link/email/phone/code badge on text tiles",
    # Editor group addition.
    "pref__clipboard__large_text_warning__label": "Huge-text warning",
    "pref__clipboard__large_text_warning__summary": "Warn when the text reaches sizes that may slow editing down",
    # Flat grid header when the calendar sections are off.
    "clipboard__group_flat": "All",
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
