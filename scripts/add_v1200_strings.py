#!/usr/bin/env python3
# DRS v1.20.0: idempotent AR/EN string keys appender AND remover (appends before
# </resources>). The precision-and-mastery round: proper localized toasts for the
# cut/copy failures (the old English literals even contained the typo "Eiter"),
# the voice-IME-not-found toast, plus removal of the five string keys belonging
# to the dead prefs deleted this round (emoji hair style + spelling contacts/udm
# stubs) so AR/EN parity stays exact on both sides.
import sys
import re

ADD_AR = {
    "editor__cut_failed": "تعذر قص النص المحدد: إما أن حالة التحديد غير صالحة أو حدث خطأ في اتصال الإدخال.",
    "editor__copy_failed": "تعذر نسخ النص المحدد: إما أن حالة التحديد غير صالحة أو حدث خطأ في اتصال الإدخال.",
    "ime__voice_ime_not_found": "تعذر العثور على لوحة مفاتيح صوتية، هل لديك واحدة مثبتة؟",
}

ADD_EN = {
    "editor__cut_failed": "Failed to cut the selected text: either the selection state is invalid or an error occurred within the input connection.",
    "editor__copy_failed": "Failed to copy the selected text: either the selection state is invalid or an error occurred within the input connection.",
    "ime__voice_ime_not_found": "Failed to find a voice keyboard, do you have one installed?",
}

REMOVE = [
    "prefs__media__emoji_preferred_hair_style",
    "pref__spelling__use_contacts__label",
    "pref__spelling__use_contacts__summary",
    "pref__spelling__use_udm_entries__label",
    "pref__spelling__use_udm_entries__summary",
]

FILES = {
    "app/src/main/res/values/strings.xml": ADD_AR,
    "app/src/main/res/values-en/strings.xml": ADD_EN,
}

# Dead-pref keys are removed from EVERY locale, not just AR/EN — a translation of
# a key that no longer exists in the default locale would be an orphan.
import glob

ALL_FILES = sorted(glob.glob("app/src/main/res/values*/strings.xml"))


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "\\'")


fail = False
for path in ALL_FILES:
    table = FILES.get(path, {})
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
    removed = 0
    for key in REMOVE:
        # Remove the whole <string ...>...</string> line for dead keys (idempotent).
        pattern = re.compile(r'^\s*<string name="%s"[^>]*>.*?</string>\s*\n' % re.escape(key), re.MULTILINE)
        new_content, n = pattern.subn("", content)
        if n:
            content = new_content
            removed += n
            print(f"DEL {key} in {path}")
    if not fail:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"OK {path}: +{added} -{removed} keys")

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
