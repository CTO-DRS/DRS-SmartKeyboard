# سياسة الخصوصية — لوحة المفاتيح DRS
# Privacy Policy — DRS Smart Keyboard

**آخر تحديث | Last updated:** 2026-09-21

---

## العربية

لوحة المفاتيح DRS تحترم خصوصيتك بشكل كامل ومطلق. هذه السياسة تشرح بإيجاز
ماذا يحدث (وماذا لا يحدث) لبياناتك عند استخدام التطبيق.

### لا نجمع أي بيانات إطلاقاً

- كل ما تكتبه يبقى على جهازك فقط: لا حفظ سحابي، لا تحليلات، لا تتبع،
  ولا إعلانات.
- قاموس المستخدم، الملفات الشخصية، الثيمات، سجل الحافظة، والإعدادات كلها
  مخزنة محلياً داخل مساحة التطبيق الخاصة على جهازك، وتُحذف نهائياً عند
  إزالة التطبيق.
- الاتصال بالشبكة محصور حصرياً في ميزتين اختياريتين: فحص تحديثات التطبيق
  وتنزيل الحزم الرسمية من مركز حزم DRS — كلاهما يتم عبر HTTPS فقط إلى
  خوادم GitHub الرسمية، ولا يُرسل معه أي بيانات شخصية أو عن ما تكتبه.
  بدون فتح هذه الميزات لا يقوم التطبيق بأي اتصال شبكي على الإطلاق.

### ذاكرة الإيموجي

- سجل الإيموجي المؤخر والمثبت محفوظ محلياً فقط على جهازك، وتستطيع مسحه
  من إعدادات الوسائط.
- ما تكتبه في السياقات الخاصة لا يُلتقط في هذه الذاكرة إطلاقاً: جلسات
  التخفي، وحقول كلمات المرور، والمحررات الخام، والحقول ذات التكوين
  المعطّل، والجهاز والقفل الشاشي مرفوع — رفضٌ حتمي مثبت باختبارات، ويشمل
  مساري النقرة في لوحة الإيموجي وقبول الاقتراح. عرض الإيموجي في هذه
  الحقول يبقى ممكنًا (لا يُسجَّل شيئاً) وإدارةُ ما هو محفوظ سلفاً تعمل
  دائماً.

### الحافظة

- سجل الحافظة محفوظ محلياً فقط، ويُمسح تلقائياً بعد الفترة التي تحددها
  في الإعدادات.
- المحتوى المميز كحساس (كلمات المرور مثلاً) لا يُحفظ في السجل ويظهر
  مقنّعاً.

### النسخ الاحتياطي والنقل

- النسخ الاحتياطي السحابي ومعاينة نقل الجهاز معطّلان بالكامل
  (`allowBackup=false` وقواعد نسخ/استخراج فارغة): لا يمكن لأي جزء من
  بياناتك — قاموس المستخدم، سجل الحافظة، الملفات الشخصية أو الإعدادات —
  أن يُرفع إلى أي خدمة سحابية أو يُنقل إلى جهاز آخر عبر آليات النظام.

### الإملاء الصوتي

- تعريف الصوتي يعمل حصرياً عبر محرّك التعرّف العامل على الجهاز نفسه
  (ON_DEVICE_ONLY): لا يُرسل أي صوت أو نص إلى أي خادم، ولا يُخزَّن أي
  مقطع صوتي أو نص مُنسوخ داخل التطبيق، ويُرفض تماماً في حقول كلمات
  المرور ووضع التخفي.

### السجلات التشخيصية

- السجلات الداخلية للتشخيص لا تتضمن ما تكتبه أبداً: أي نص مستخدم يظهر
  فيها مجرد طول (عدد أحرف) فقط، ولا تُرسل السجلات لأي جهة — تبقى على
  الجهاز (إن وُجدت) وتُحذف مع بيانات التطبيق.

### التقارير والأعطال

- عند حدوث عطل، تُعرض نافذة محلية على جهازك لعرض تفاصيل العطل ونسخها
  إن أردت مشاركتها بنفسك. لا يُرسل أي تقرير تلقائياً إلى أي جهة.

### الأذونات

يطلب التطبيق الحد الأدنى من الأذونات فقط:

| الإذن | السبب |
|---|---|
| `VIBRATE` | اهتزاز عند ضغط المفاتيح (اختياري) |
| `POST_NOTIFICATIONS` | إشعارات الحافظة والتحديثات (اختياري) |
| `RECORD_AUDIO` | الإملاء الصوتي (اختياري، على الجهاز فقط، لا يُطلَب إلا عند أول ضغطة على مفتاح المايك) |
| `INTERNET` | فحص تحديثات التطبيق وتنزيل الحزم الرسمية عبر HTTPS فقط |
| `REQUEST_INSTALL_PACKAGES` | تثبيت ملف APK الرسمي بعد تحقق SHA-256 (مركز التحديثات) |

أي تغيير مستقبلي على هذه السياسة سيُنشر في هذا الملف.

---

## English

DRS Smart Keyboard fully and absolutely respects your privacy. This policy
briefly explains what happens (and what does not happen) to your data when
using the app.

### We collect no data at all

- Everything you type stays on your device only: no cloud storage, no
  analytics, no tracking, and no ads.
- The user dictionary, profiles, themes, clipboard history and settings
  are all stored locally in the app's private space on your device and
  are permanently deleted when the app is uninstalled.
- Network access is strictly limited to two opt-in features: app update
  checks and official package downloads from the DRS Package Center —
  both over HTTPS to official GitHub servers only, never sending any
  personal data or anything you type. Without enabling those features
  the app performs no network activity at all.

### Emoji memory

- The emoji recents and pinned history are stored locally on your device
  only, and can be cleared from the media settings.
- What you type in private contexts is never recorded into this memory:
  incognito sessions, password fields, raw input editors, fields with
  composing disabled, and while the screen lock is up — a deterministic
  refusal proven by tests, covering both the emoji-palette tap path and
  the suggestion-acceptance path. Viewing emoji in those fields stays
  possible (nothing is recorded) and managing previously saved entries
  always works.

### Clipboard

- Clipboard history is stored locally only and is automatically cleared
  after the period you configure in settings.
- Content marked as sensitive (such as passwords) is never stored in
  history and is displayed masked.

### Backup & transfer

- Cloud backup and device-transfer extraction are fully disabled
  (`allowBackup=false` with empty backup/extraction rules): no part of
  your data — user dictionary, clipboard history, profiles or settings —
  can ever be uploaded to a cloud service or migrated to another device
  by the platform backup machinery.

### Voice dictation

- Voice dictation runs exclusively through the on-device recognizer
  (ON_DEVICE_ONLY): no audio or text is ever sent to any server, no audio
  or transcript is stored by the app, and dictation is hard-refused in
  password fields and incognito mode.

### Diagnostic logs

- Internal diagnostic logs never contain what you type: any user text
  appears there as a length only (character count), and logs are never
  sent anywhere — they stay on the device (if kept at all) and are
  deleted with the app's data.

### Crash reports

- When a crash occurs, a local window on your device shows the crash
  details so you can copy them yourself if you wish to share them.
  No report is ever sent automatically to anyone.

### Permissions

The app requests only the minimum set of permissions:

| Permission | Purpose |
|---|---|
| `VIBRATE` | Key press vibration (optional) |
| `POST_NOTIFICATIONS` | Clipboard and update notifications (optional) |
| `RECORD_AUDIO` | Voice dictation (optional, on-device only, requested lazily on the first mic press) |
| `INTERNET` | App update checks and official package downloads over HTTPS only |
| `REQUEST_INSTALL_PACKAGES` | Installing the SHA-256-verified official APK (Update Center) |

Any future change to this policy will be published in this file.
