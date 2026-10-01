#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""DRS v1.15.0 — append the round's AR/EN string keys with full parity."""

AR = '''    <!-- DRS v1.15.0: المهام المثبتة 10 فقط + اللوحات الذكية الثلاث -->
    <string name="drs__unified__tool_diacritics_panel">لوحة الحركات</string>
    <string name="drs__unified__tool_diacritics_panel_desc">تفتح لوحة الحركات الذكية: حركة فوق حركة تستبدلها بدل التراكم، مع التشكيل المزدوج والأكثر استخدامًا</string>
    <string name="drs__unified__tool_smart_symbols">لوحة الرموز الذكية</string>
    <string name="drs__unified__tool_smart_symbols_desc">تفتح لوحة رموز تقترح ما يناسب النص قبل المؤشر: أرقام تُوصلك إلى النسب والعملات، وحروف عربية إلى الترقيم العربي</string>
    <string name="drs__unified__tool_arabic_letters">لوحة الحروف الموسعة</string>
    <string name="drs__unified__tool_arabic_letters_desc">تفتح همزات ومشتقات وحروف الفارسية والأردية والكردية التي لا يتسع لها التخطيط الأساسي</string>
    <string name="drs__tools_drawer__pinned_counter">المثبتة: {count} من {max}</string>
    <string name="drs__tools_drawer__pin_cap_toast">الحد الأقصى للمهام المثبتة هو 10 — أزل تثبيت مهمة أولًا لتثبيت غيرها</string>
    <string name="quick_action__ime_ui_mode_diacritics">لوحة الحركات</string>
    <string name="quick_action__ime_ui_mode_diacritics__tooltip">تفتح لوحة الحركات الذكية بالدمج الذكي والتشكيل المزدوج</string>
    <string name="quick_action__ime_ui_mode_smart_symbols">لوحة الرموز الذكية</string>
    <string name="quick_action__ime_ui_mode_smart_symbols__tooltip">تفتح لوحة رموز تقترح ما يناسب النص قبل المؤشر</string>
    <string name="quick_action__ime_ui_mode_arabic_letters">لوحة الحروف الموسعة</string>
    <string name="quick_action__ime_ui_mode_arabic_letters__tooltip">تفتح همزات ومشتقات وحروف الفارسية والأردية والكردية</string>
    <string name="panel__harakat__title">لوحة الحركات الذكية</string>
    <string name="panel__harakat__combos_title">التشكيل المزدوج (شدة + حركة)</string>
    <string name="panel__harakat__combo_name">شدّة + حركة</string>
    <string name="panel__harakat__grid_title">جميع الحركات</string>
    <string name="panel__harakat__name_fatha">فتحة</string>
    <string name="panel__harakat__name_damma">ضمة</string>
    <string name="panel__harakat__name_kasra">كسرة</string>
    <string name="panel__harakat__name_sukun">سكون</string>
    <string name="panel__harakat__name_shadda">شدة</string>
    <string name="panel__harakat__name_fathatan">تنوين فتح</string>
    <string name="panel__harakat__name_dammatan">تنوين ضم</string>
    <string name="panel__harakat__name_kasratan">تنوين كسر</string>
    <string name="panel__harakat__name_superscript_alef">ألف خنجرية</string>
    <string name="panel__harakat__name_tatweel">تطويل</string>
    <string name="panel__symbols__title">لوحة الرموز الذكية</string>
    <string name="panel__symbols__suggestions_title">مقترحة حسب النص</string>
    <string name="panel__symbols__math_title">رياضيات</string>
    <string name="panel__symbols__currency_title">عملات</string>
    <string name="panel__symbols__arrows_title">أسهم</string>
    <string name="panel__symbols__brackets_title">أقواس</string>
    <string name="panel__symbols__punct_title">علامات وترقيم</string>
    <string name="panel__letters__title">لوحة الحروف الموسعة</string>
    <string name="panel__letters__grid_title">همزات وحروف موسعة</string>
    <string name="panel__recents_title">الأكثر استخدامًا</string>
    <string name="panel__switcher_harakat">حركات</string>
    <string name="panel__switcher_symbols">رموز</string>
    <string name="panel__switcher_letters">حروف</string>
    <string name="pref__panels__group__label">اللوحات الذكية</string>
    <string name="pref__panels__harakat_smart_replace__label">الدمج الذكي للحركات</string>
    <string name="pref__panels__harakat_smart_replace__summary">حركة فوق حركة تستبدلها بدل أن تتراكم، والشدة تُلحق بالحركة</string>
    <string name="pref__panels__symbol_suggestions__label">اقتراحات الرموز الذكية</string>
    <string name="pref__panels__symbol_suggestions__summary">صف رموز يقترح ما يناسب النص قبل المؤشر تلقائيًا</string>
    <string name="pref__panels__recents__label">الأكثر استخدامًا في اللوحات</string>
    <string name="pref__panels__recents__summary">تذكر أكثر ما تستخدمه في لوحات الحركات والرموز والحروف محليًا فقط</string>
'''

EN = '''    <!-- DRS v1.15.0: the pinned-tasks cap of 10 + the three smart panels -->
    <string name="drs__unified__tool_diacritics_panel">Harakat panel</string>
    <string name="drs__unified__tool_diacritics_panel_desc">Opens the smart harakat panel: a mark over a mark replaces it instead of stacking, with shadda combos and most-used rows</string>
    <string name="drs__unified__tool_smart_symbols">Smart symbols panel</string>
    <string name="drs__unified__tool_smart_symbols_desc">Opens a symbols panel that suggests what fits the text before the cursor: digits lead to percent and currency, Arabic letters to Arabic punctuation</string>
    <string name="drs__unified__tool_arabic_letters">Extended letters panel</string>
    <string name="drs__unified__tool_arabic_letters_desc">Opens the hamza variants and the Persian, Urdu and Kurdish letters the base layout has no room for</string>
    <string name="drs__tools_drawer__pinned_counter">Pinned: {count} of {max}</string>
    <string name="drs__tools_drawer__pin_cap_toast">The pinned-tasks cap is 10 — unpin a task first to pin another</string>
    <string name="quick_action__ime_ui_mode_diacritics">Harakat panel</string>
    <string name="quick_action__ime_ui_mode_diacritics__tooltip">Opens the smart harakat panel with smart stacking and shadda combos</string>
    <string name="quick_action__ime_ui_mode_smart_symbols">Smart symbols panel</string>
    <string name="quick_action__ime_ui_mode_smart_symbols__tooltip">Opens a symbols panel that suggests what fits the text before the cursor</string>
    <string name="quick_action__ime_ui_mode_arabic_letters">Extended letters panel</string>
    <string name="quick_action__ime_ui_mode_arabic_letters__tooltip">Opens the hamza variants and the Persian, Urdu and Kurdish letters</string>
    <string name="panel__harakat__title">Smart Harakat Panel</string>
    <string name="panel__harakat__combos_title">Double diacritics (shadda + mark)</string>
    <string name="panel__harakat__combo_name">Shadda + mark</string>
    <string name="panel__harakat__grid_title">All harakat</string>
    <string name="panel__harakat__name_fatha">Fatha</string>
    <string name="panel__harakat__name_damma">Damma</string>
    <string name="panel__harakat__name_kasra">Kasra</string>
    <string name="panel__harakat__name_sukun">Sukun</string>
    <string name="panel__harakat__name_shadda">Shadda</string>
    <string name="panel__harakat__name_fathatan">Fathatan</string>
    <string name="panel__harakat__name_dammatan">Dammatan</string>
    <string name="panel__harakat__name_kasratan">Kasratan</string>
    <string name="panel__harakat__name_superscript_alef">Dagger alef</string>
    <string name="panel__harakat__name_tatweel">Tatweel</string>
    <string name="panel__symbols__title">Smart Symbols Panel</string>
    <string name="panel__symbols__suggestions_title">Suggested by context</string>
    <string name="panel__symbols__math_title">Math</string>
    <string name="panel__symbols__currency_title">Currencies</string>
    <string name="panel__symbols__arrows_title">Arrows</string>
    <string name="panel__symbols__brackets_title">Brackets</string>
    <string name="panel__symbols__punct_title">Punctuation &amp; marks</string>
    <string name="panel__letters__title">Extended Letters Panel</string>
    <string name="panel__letters__grid_title">Hamza variants &amp; extended letters</string>
    <string name="panel__recents_title">Most used</string>
    <string name="panel__switcher_harakat">Harakat</string>
    <string name="panel__switcher_symbols">Symbols</string>
    <string name="panel__switcher_letters">Letters</string>
    <string name="pref__panels__group__label">Smart panels</string>
    <string name="pref__panels__harakat_smart_replace__label">Smart harakat stacking</string>
    <string name="pref__panels__harakat_smart_replace__summary">A new mark replaces the previous one instead of stacking; shadda pairs with marks</string>
    <string name="pref__panels__symbol_suggestions__label">Smart symbol suggestions</string>
    <string name="pref__panels__symbol_suggestions__summary">A symbols row that fits the text before the cursor automatically</string>
    <string name="pref__panels__recents__label">Panel most-used rows</string>
    <string name="pref__panels__recents__summary">Remembers your most-used tiles across the harakat, symbols and letters panels — local only</string>
'''

import re

BASE = "app/src/main/res/values/strings.xml"
EN_PATH = "app/src/main/res/values-en/strings.xml"

# DRS v1.23.0: the insertion is now idempotent — the original blind
# `content.replace("</resources>", block + "</resources>")` duplicated the
# whole block on every re-run. Keys that already exist are skipped.
for path, block in ((BASE, AR), (EN_PATH, EN)):
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()
    assert content.rstrip().endswith("</resources>"), path
    block_keys = set(re.findall(r'<string name="([^"]+)"', block))
    existing = set(re.findall(r'<string name="([^"]+)"', content))
    pending = block_keys - existing
    added = 0
    if pending:
        kept_lines = []
        for line in block.splitlines(keepends=True):
            m = re.search(r'<string name="([^"]+)"', line)
            if m is not None and m.group(1) not in pending:
                continue
            kept_lines.append(line)
        kept = "".join(kept_lines)
        idx = content.rstrip().rfind("</resources>")
        content = content[:idx] + kept + "\n" + content[idx:]
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        added = len(pending)
    print(f"OK {path}: +{added} skipped={len(block_keys) - added}")

for path in (BASE, EN_PATH):
    with open(path, "r", encoding="utf-8") as f:
        n = len(re.findall(r"<string name=", f.read()))
    print(f"{path}: {n} keys")

# ── DRS v1.23.0: unified AR/EN parity gate (بوابة التوازي الموحدة) ──
# Every string script ends with the same honest gate: the AR and EN key
# sets must be identical or the run fails loudly. The older scripts
# shipped without it — appended here by the v1.23.0 round.
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
    raise SystemExit(1)
