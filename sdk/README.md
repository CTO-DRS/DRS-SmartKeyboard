# DRS Keyboard SDK — دليل بناء حزم الامتدادات

> الحزمة = ملف `.flex` (أرشيف zip) يحمل `extension.json` وأصوله. كل الحزم
> بيانات صرفة (سمات، تخطيطات، حزم لغات) — لا كود قابل للتنفيذ، لذلك لا
> يستطيع أي حزم — حتى الموقّعة من مفتاحنا — أن تنفّذ شيئًا على جهاز المستخدم.

## 1) أنواع الحزم الثلاثة

| النوع | القيمة `"$"` | المحتوى |
|---|---|---|
| سمة | `ime.extension.theme` | `themes[]` + `stylesheets/*.json` |
| تخطيطات لوحة | `ime.extension.keyboard` | `layouts` + ملفات JSON للترتيبات |
| حزمة لغة | `ime.extension.languagepack` | `items[]` + جداول اللغة |

## 2) عقد الـmanifest (إلزامي بلا استثناء)

```json
{
  "$": "ime.extension.theme",
  "meta": {
    "id": "com.example.mytheme",
    "version": "1.0.0",
    "title": "اسم السمة",
    "maintainers": ["اسمك"],
    "license": "proprietary"
  },
  "themes": [
    { "id": "my_day", "label": "سمتي النهارية", "authors": ["اسمك"], "isNight": false }
  ]
}
```

- `id`: نطاق معكوس صغير (regex الموثق: `^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$`).
- `version`: رقمي منقّط `X.Y.Z` (مع لاحقة اختيارية `-beta1`).
- `license`: إلزامي — حزمة بلا ترخيص تُرفض عند التحقق.
- `stylesheetPath`: اختياري — غيابه يُشتق تلقائيًا إلى
  `stylesheets/<id>.json` (المشتق هو المسار الرسمي، ووجود الملف يتحقق منه
  `drs_sdk_validate.py` قبل أي توقيع).

## 3) دورة الحزمة الرسمية (الموقّعة)

```
عدّل الحزمة → drs_sdk_validate.py → drs_package_sign.py sign-catalog → أرشِف التزامك → نشر GitHub Release
```

1. **تحقّق** `python3 tools/drs_sdk_validate.py` — يفحص الـmanifest والأصول
   وتوقيع كل حزم الكتالوج الثماني مقابل المفتاح المثبّت.
2. **وقّع** `python3 tools/drs_package_sign.py sign-catalog --seed-file
   .local-secrets/cto-drs-release-1.seed` — الأداة تشغّل اختبار ذاتها على
   متجهات RFC 8032 §7.1 قبل كل توقيع، وتكتب `signature` + `pubkey_id`
   في `packages/manifest.json`.
3. **التحقق مزدوج في التطبيق**: عند التثبيت يفحص `DrsPackageManager`
   (الحجم → SHA-256 → توقيع ed25519) قبل أي فك ضغط — الكتالوج غير
   الموقّع يُسقَط تمامًا.

> البذرة (`.local-secrets/cto-drs-release-1.seed`) خارج git عمدًا.
> من يملك البذرة يملك قناة التوزيع الرسمية — تخزين بارد إلزامي.

## 4) عينة رسمية

`sdk/drs-sample-keyboard-pack/` — ثيم «نعناع» نهاري/ليلي بصيغة صحيحة،
مستخدم كمدخل اختباري دائم في `drs_sdk_validate.py`. انسخه وعدّله ثم
أعد التحقق.

## 5) توليد حزمة لغة جديدة

```bash
python3 tools/drs_language_pack_scaffold.py --code ckb --name "کوردیی ناوەندی"
```

يولّد هيكلًا كاملًا (manifest + هيكل التخطيطات) ويحقق فورًا ما ولّده —
ثم عدّل التخطيطات وأعد التحقق.

## 6) الخصوصية، بلا استثناءات

حزم الامتدادات لا تجلب أي شبكة معها: الحزم بيانات، والتنزيل يمر عبر
بوابات الحجم/الهاش/التوقيع في `DrsPackageManager`، وفاحص الخصوصية
(`ci/privacy_canary.sh`) يمنح أي استيراد شبكي في شجرة الامتدادات فشل
بناء. هذه ليست سياسة قابلة للتفاوض.
