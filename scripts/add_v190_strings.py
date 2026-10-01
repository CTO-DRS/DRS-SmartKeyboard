#!/usr/bin/env python3
# DRS v1.9.0: idempotent AR/EN string keys appender (appends before </resources>).
# The integrated smart clipboard system: in-panel text editor with live
# stats and the 50k storage policy, save-as-file with a user-named file,
# and the settings-screen history JSON export.
import sys, re

AR = {
    "clip__edit_item": "تعديل النص",
    "clip__save_item_as_file": "حفظ كملف",
    "clip__edit_title": "تعديل نص الحافظة",
    "clip__stats_label": "{chars} حرف · {words} كلمة · {lines} أسطر",
    "clip__char_limit_counter": "{used} / {max}",
    "clip__save_file_title": "حفظ العنصر كملف",
    "clip__save_file_name_hint": "اسم الملف",
    "clip__save_file_note": "يُكتب النص في «التنزيلات/DRS Keyboard» على أندرويد 10 وما بعده، وفي مساحة التطبيق الخاصة على الإصدارات الأقدم. الامتداد txt يُضاف تلقائيًا إن لم يوجد",
    "clip__saved_to_downloads": "تم الحفظ في مجلد التنزيلات",
    "clip__saved_to_app_files": "تم الحفظ في مساحة التطبيق الخاصة",
    "clip__save_failed": "فشل حفظ الملف",
    "pref__clipboard__group_export__label": "التصدير",
    "clipboard__export_history__label": "تصدير سجل الحافظة",
    "clipboard__export_history__summary": "حفظ كل نصوص السجل في ملف JSON واحد عبر منتقي ملفات النظام — النصوص فقط، الوسائط لا تُصَدَّر",
    "clipboard__export_history__done": "تم تصدير سجل الحافظة",
    "clipboard__export_history__failed": "فشل تصدير سجل الحافظة",
}

EN = {
    "clip__edit_item": "Edit text",
    "clip__save_item_as_file": "Save as file",
    "clip__edit_title": "Edit clipboard text",
    "clip__stats_label": "{chars} chars · {words} words · {lines} lines",
    "clip__char_limit_counter": "{used} / {max}",
    "clip__save_file_title": "Save item as file",
    "clip__save_file_name_hint": "File name",
    "clip__save_file_note": "Written to Downloads/DRS Keyboard on Android 10+, and to the app's private storage on older versions. The txt extension is appended when missing",
    "clip__saved_to_downloads": "Saved to the Downloads folder",
    "clip__saved_to_app_files": "Saved to the app's private storage",
    "clip__save_failed": "Failed to save the file",
    "pref__clipboard__group_export__label": "Export",
    "clipboard__export_history__label": "Export clipboard history",
    "clipboard__export_history__summary": "Save the whole history's texts into one JSON file via the system file picker — texts only, media is not exported",
    "clipboard__export_history__done": "Clipboard history exported",
    "clipboard__export_history__failed": "Failed to export the clipboard history",
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
