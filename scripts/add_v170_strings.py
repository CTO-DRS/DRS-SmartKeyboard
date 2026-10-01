#!/usr/bin/env python3
# DRS v1.7.0: idempotent AR/EN string keys appender (appends before </resources>).
import sys, re

AR = {
    "quick_action__show_subtype_picker": "اختيار اللغة",
    "quick_action__show_subtype_picker__tooltip": "يفتح منتقي لغات التخطيط المكوّنة داخل اللوحة",
    "quick_action__toggle_actions_editor": "تعديل الإجراءات",
    "quick_action__toggle_actions_editor__tooltip": "يبدّل وضع تعديل أزرار الإجراءات في الشريط الذكي",
    "quick_action__clipboard_pin_active": "تثبيت المقصوصة النشطة",
    "quick_action__clipboard_pin_active__tooltip": "يثبّت المقصوصة الحالية أو يفك تثبيتها — المثبتة تنجو من مسح السجل",
    "drs__unified__tool_clipboard_pin": "تثبيت المقصوصة النشطة",
    "drs__unified__tool_clipboard_pin_desc": "يثبّت المقصوصة الحالية أو يفك تثبيتها عبر مسار pinClip/unpin نفسه الذي تستخدمه لوحة الحافظة — المثبتة تنجو من مسح السجل",
    "drs__text_tools__tool_normalize_arabic_forms": "إصلاح أشكال العرض العربية",
    "drs__text_tools__desc_normalize_arabic_forms": "يعيد أحرف العرض العربية المنفصلة (من نسخ PDF والويب) إلى حروفها الأساسية مع رباط لا — ما كان يكسر البحث ولا تشمله توحيد الحروف",
    "drs__text_tools__tool_split_to_lines": "قائمة إلى أسطر",
    "drs__text_tools__desc_split_to_lines": "يحوّل قائمة مفصولة بفواصل لاتينية أو عربية إلى عنصر في كل سطر مع تنظيف المسافات",
    "drs__text_tools__tool_join_lines": "أسطر إلى قائمة",
    "drs__text_tools__desc_join_lines": "يدمج الأسطر في قائمة واحدة بفاصلة عربية (،) للعربية ولاتينية (,) لغيرها — النقيض الحقيقي لقائمة إلى أسطر",
    "drs__text_tools__tool_remove_all_spaces": "إزالة كل المسافات",
    "drs__text_tools__desc_remove_all_spaces": "يحذف كل مسافة أفقية (أسلوب الوسوم #وسم_عربي) ويبقي فواصل الأسطر كما هي — الشقيق الأشد من تنظيف المسافات",
    "drs__text_tools__tool_strip_emoji": "تجريد الإيموجي",
    "drs__text_tools__desc_strip_emoji": "يحذف الإيموجي والرموز التعبيرية ومحدداتها من النص الملصوق فلا تشوّش أدوات العد والترتيب والبحث",
    "drs__text_tools__tool_url_encode": "ترميز رابط",
    "drs__text_tools__desc_url_encode": "يرمّز النص إلى صيغة روابط UTF-8 المئوية (المسافة %20) فتصبح الروابط العربية قابلة للمشاركة في كل التطبيقات",
    "drs__text_tools__tool_url_decode": "فك ترميز رابط",
    "drs__text_tools__desc_url_decode": "يفك الترميز المئوي إلى نص UTF-8 المقروء — التسلسلات التالفة تعود كما هي دون إتلاف النص",
    "pref__glide__trail_width__label": "عرض أثر التمرير",
    "drs__unified__basics_trail_summary": "يضبط سماكة شريط الأثر الذي يظهر أثناء الكتابة بالتمرير وبعده",
    "pref__emoji__size__label": "حجم الإيموجي",
    "drs__unified__basics_emoji_summary": "يضبط حجم شبكة الإيموجي وحجم الرمز نفسه معًا",
    "clipboard__pinned_active": "تم تثبيت المقصوصة النشطة",
    "clipboard__unpinned_active": "تم فك تثبيت المقصوصة النشطة",
    "drs__diagnostics__events_export": "تصدير التقرير",
    "drs__diagnostics__report_export_done": "تم تصدير التقرير التشخيصي",
    "drs__diagnostics__report_export_failed": "فشل تصدير التقرير التشخيصي",
    "drs__diagnostics__backup_preview_title": "معاينة النسخة الاحتياطية",
    "drs__diagnostics__backup_preview_shortcuts": "الاختصارات: {count}",
    "drs__diagnostics__backup_preview_profiles": "الأنظمة الشخصية: {count}",
    "drs__diagnostics__backup_preview_wallet": "رصيد المحفظة الإجمالي: {points}",
    "drs__diagnostics__backup_preview_days": "أيام الإحصاءات المسجلة: {days}",
    "drs__diagnostics__backup_preview_warn": "الاستيراد يستبدل كل حالة DRS الحالية (الأنظمة والاختصارات والمحفظة والإحصاءات). لا يمكن التراجع.",
    "drs__diagnostics__backup_preview_confirm": "استيراد",
    "drs__diagnostics__backup_preview_cancel": "إلغاء",
    "drs__diagnostics__check_shortcut_templates": "سلامة قوالب الاختصارات",
    "drs__diagnostics__hint_shortcut_templates": "أحد الاختصارات يحتوي متغيرًا غير معروف مثل {dat] أو {Datee} — يُدرج نصيًا حرفيًا عند كل توسيع. راجع شاشة الاختصارات وصحّح الاسم إلى أحد المتغيرات المدعومة",
    "drs__unified__stats_context_mix": "توزيع أنماط الإدخال: {mix}",
    "drs__context_mode__normal": "كتابة عادية",
    "drs__context_mode__chat": "محادثة",
    "drs__context_mode__writing": "كتابة",
    "drs__context_mode__coding": "برمجة",
    "drs__context_mode__numbers": "أرقام",
    "drs__context_mode__search": "بحث",
    "drs__context_mode__password": "كلمة مرور",
    "drs__context_mode__technical": "تقني",
}

EN = {
    "quick_action__show_subtype_picker": "Pick language",
    "quick_action__show_subtype_picker__tooltip": "Opens the in-keyboard picker of configured layout languages",
    "quick_action__toggle_actions_editor": "Edit actions",
    "quick_action__toggle_actions_editor__tooltip": "Toggles the smartbar actions-button editing mode",
    "quick_action__clipboard_pin_active": "Pin active clip",
    "quick_action__clipboard_pin_active__tooltip": "Pins or unpins the current clipboard entry — pinned entries survive history wipes",
    "drs__unified__tool_clipboard_pin": "Pin active clip",
    "drs__unified__tool_clipboard_pin_desc": "Pins or unpins the current clipboard entry through the exact pinClip/unpin path the clipboard panel uses — pinned entries survive history wipes",
    "drs__text_tools__tool_normalize_arabic_forms": "Fix Arabic presentation forms",
    "drs__text_tools__desc_normalize_arabic_forms": "Folds disconnected Arabic presentation-form glyphs (copied from PDFs and the web) back to base letters including the lam-alef ligature — search-breaking and invisible to letter unification",
    "drs__text_tools__tool_split_to_lines": "List to lines",
    "drs__text_tools__desc_split_to_lines": "Turns a comma or semicolon list (Latin or Arabic separators) into one item per line with surrounding spaces trimmed",
    "drs__text_tools__tool_join_lines": "Lines to list",
    "drs__text_tools__desc_join_lines": "Joins the lines into one list — Arabic comma (،) for Arabic locales, Latin comma elsewhere. The true inverse of list-to-lines",
    "drs__text_tools__tool_remove_all_spaces": "Remove all spaces",
    "drs__text_tools__desc_remove_all_spaces": "Deletes every horizontal space (hashtag style) while keeping newlines intact — the aggressive sibling of trim spaces",
    "drs__text_tools__tool_strip_emoji": "Strip emoji",
    "drs__text_tools__desc_strip_emoji": "Removes emoji, pictographs and their selectors from pasted text so the counting, sorting and search tools stay clean",
    "drs__text_tools__tool_url_encode": "URL encode",
    "drs__text_tools__desc_url_encode": "Percent-encodes the text as a UTF-8 URL component (space becomes %20) so Arabic links are shareable everywhere",
    "drs__text_tools__tool_url_decode": "URL decode",
    "drs__text_tools__desc_url_decode": "Decodes percent-encoded text back to readable UTF-8 — malformed sequences return untouched so typing is never destroyed",
    "pref__glide__trail_width__label": "Glide trail width",
    "drs__unified__basics_trail_summary": "Adjusts the thickness of the ribbon drawn while glide typing and during its fade-out",
    "pref__emoji__size__label": "Emoji size",
    "drs__unified__basics_emoji_summary": "Adjusts the emoji grid cell size and the glyph size together",
    "clipboard__pinned_active": "Active clip pinned",
    "clipboard__unpinned_active": "Active clip unpinned",
    "drs__diagnostics__events_export": "Export report",
    "drs__diagnostics__report_export_done": "Diagnostic report exported",
    "drs__diagnostics__report_export_failed": "Failed to export the diagnostic report",
    "drs__diagnostics__backup_preview_title": "Backup preview",
    "drs__diagnostics__backup_preview_shortcuts": "Shortcuts: {count}",
    "drs__diagnostics__backup_preview_profiles": "Profiles: {count}",
    "drs__diagnostics__backup_preview_wallet": "Total wallet balance: {points}",
    "drs__diagnostics__backup_preview_days": "Recorded stats days: {days}",
    "drs__diagnostics__backup_preview_warn": "Importing replaces the whole current DRS state (profiles, shortcuts, wallet, stats). This cannot be undone.",
    "drs__diagnostics__backup_preview_confirm": "Import",
    "drs__diagnostics__backup_preview_cancel": "Cancel",
    "drs__diagnostics__check_shortcut_templates": "Shortcut template validity",
    "drs__diagnostics__hint_shortcut_templates": "A shortcut expansion contains an unknown variable such as {dat] or {Datee} — it is committed literally on every expansion. Review the shortcuts screen and fix the name to one of the supported variables",
    "drs__unified__stats_context_mix": "Input mode mix: {mix}",
    "drs__context_mode__normal": "Normal typing",
    "drs__context_mode__chat": "Chat",
    "drs__context_mode__writing": "Writing",
    "drs__context_mode__coding": "Coding",
    "drs__context_mode__numbers": "Numbers",
    "drs__context_mode__search": "Search",
    "drs__context_mode__password": "Password",
    "drs__context_mode__technical": "Technical",
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
