# ADR 0002 — خريطة نقل الإصلاحات الأمنية الأحد عشر إلى قاعدة drs-smart

- **الحالة:** منفّذ
- **التاريخ:** 2026-09-29
- **المرجع:** ADR 0001، التزام الرقعة الأمنية 38ec3a1 على main (الأصل dc3c7d5)
- **الطريقة:** `git apply --3way` لرقعة `dc3c7d5..38ec3a1` على aeb9cd2، ثم حل 10 تعارضات

## قاعدة الحسم

«إن نفّذ drs-smart الإصلاحَ أصلًا تبقى نسخته (وهي غالبًا أوفى توثيقًا وأوسع تغطية)،
وإلا دُمج إصلاح main كما هو».

## الخريطة (الإصلاح → الملف → القرار)

| # | الإصلاح | الملف | القرار |
|---|---|---|---|
| 1 | كتابة حافظة النظام عند المشاركة خلف موافقة (زر OK) | DrsCopyToClipboardActivity.kt | نسخة drs: بنية Compose غير المتزامنة (snapshot states + decode على IO) — أُسقطت بقايا `copyAndFinish` و`sharedImageUri` من النسخة المينية |
| 2 | عزل شاشات Devtools عن الروابط العميقة الخارجية | DrsAppActivity.kt | نسخة drs (نفس البوابة `isDevtoolsRouteDeeplink` بـKDoc أوفى) |
| 3 | إزالة `profileable shell` من الإطلاق | AndroidManifest.xml | تعليق موحد يذكر المرجعين (p8 C-3 / security-port E1-03) |
| 4 | بوابة كتالوج الحزم: معرّف + نمط إصدار + sha256 سداسي + HTTPS | DrsPackageManager.kt | نسخة drs: نفس البوابات بتعليقات أوفى (`META_ID_REGEX` + `PACKAGE_VERSION_REGEX`) |
| 5 | تطهير أسماء أصول التحديث (قطاع واحد + اسم SHA-256 عند الفساد) | DrsUpdateCenter.kt | نسخة drs (نفس `sanitizeAssetFileName`) |
| 6 | استنساخ الوسائط خلف بوابات التخفي/كلمة السر/الحساسية + إغلاق اليتامى | ClipboardManager.kt | نسخة drs: الإغلاق عبر `closeRemovedItems` الشاملة للـVIDEO (نسخة main كانت `item.close` المقتصرة على IMAGE) |
| 7 | إغلاق مساحة الاسترجاع عند النجاح والفشل | RestoreScreen.kt | من main: تتبّع `opened` وإغلاق مسار الفشل (drs كان يغلق النجاح والإلغاء فقط) — من drs: تعليق النجاح |
| 8 | حصر قنبلة zip بالبايتات المكتوبة فعلًا (100MB/مدخل + 300MB/أرشيف) | ZipUtils.kt | نسخة drs (نفس التنفيذ المحدود بتعليقات أوفى) |
| 9 | تطهير وسائط الاسترجاع + احتواء كانوني + قص النص المستعاد 50k | RestoreScreen.kt + ClipboardFileStorage.kt | `ClipboardFileStorage` طُبّق نظيفًا (تحصين الاحتواء على طرفي النسخ)؛ قص 50k عبر `ClipboardTextPolicy.truncateForStorage` على النص المستعاد (من main)؛ تعليم drs في فرعي الصورة/الفيديو |
| 10 | قراءة DrsBackup محدودة 64KiB بعد بوابة SIZE المعلن | DrsBackup.kt | نسخة drs (نفس `readBounded` + `queryDeclaredSize` — انطبق نظيفة، استُعيد الأصل) |
| 11 | فرض وضع الصوت على الجهاز وإطفاء Devtools وإشعار التحرير بعد الاسترجاع | RestoreScreen.kt | نسخة drs (نفس المفاتيح الثلاثة بـ`getOrThrow` الصادق) |

## ملفات الاختبار

فئات الاختبار الأمني الثلاث موجودة في قاعدة drs بنسخة أشمل (ZipUtilsTest يضم
`publishAtomic` ومعاملات seam) — **بقيت بنسخ drs الأصلية** ولم تُستبدل بنسخ الرقعة:
ZipUtilsTest.kt، DrsBackupBoundedReadTest.kt، DrsP8UpdateSurfaceTests.kt.

## النتيجة الصافية عن قاعدة drs-smart (aeb9cd2)

| الملف | الفرق |
|---|---|
| ClipboardFileStorage.kt | + تحصين الاحتواء الكانوني (نظيف) |
| RestoreScreen.kt | + قص 50k للنص المستعاد، + إغلاق الفشل، + مستوردات |
| AndroidManifest.xml | تعليق موحد (لا فرق دلالي) |
| بقي الملفات | مطابقة للأساس — drs يفّذ الإصلاحات أصلًا |

## حصيلة التنظيف أثناء الحل

أُسقط أثناء حل التعارضات: استيراد `tryOrNull` مكرر في DrsBackup، بقايا `copyAndFinish`
في نشاط المشاركة، وإعلان `prefs` مزدوج في RestoreScreen.
