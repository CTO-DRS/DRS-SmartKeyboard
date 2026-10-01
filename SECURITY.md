# الأمان — Security Policy

> EN below — سياسة الإبلاغ عن الثغرات، نطاق البرنامج، ومستويات الخطورة
> والمكافآت (برنامج مكافأة الثغرات، مهمة المرحلة 3 م1).

## العربية

### الإبلاغ عن ثغرة

لا تفتح Issue عامة أبدًا لثغرة أمنية. القناة الرسمية:

1. **تقرير ثغرة خاص عبر GitHub** — من صفحة المستودع:
   «Security» → «Report a vulnerability» (تقارير خاصة مشفرة تصل فريق
   الأمان مباشرة دون إشعار عام).
2. إن تعذّر ذلك، راسلنا عبر نموذج الدعم في SUPPORT.md وسننتقل إلى
   القناة الخاصة فورًا.

### ما نلتزم به (SLA)

| الخطوة | الالتزام |
|---|---|
| إقرار الاستلام | خلال **72 ساعة** |
| تقييم أولي وخطورة | خلال **7 أيام** |
| إصلاح الثغرات الحرجة/العالية | خلال **30 يومًا** من التقييم |
| إصدار أمني + شكر عام (إن رغبت) | مع إصدار الرقعة |

### النطاق

- تطبيق DRS Smart Keyboard (كل الفروع الرسمية والوسوم الموقعة).
- سلسلة التوزيع: أصول Releases وSHA256SUMS وقناة CI.
- **خارج النطاق:** مواقع الطرف الثالث، وكلمات سر حسابات GitHub
  نفسها، وأي هجوم يتطلب جهازًا مفكوك قفله (root) مع ثغرات النظام
  الأساسي.

### Safe Harbor

نعد بعدم اتخاذ إجراء قانوني ضد الباحث الذي يتصرف بحسن نية: بلا
استغلال بعد الإبلاغ، بلا وصول لبيانات الآخرين، بلا تعطيل خدمة،
وبلاغ كامل التفاصيل لنا قبل أي نشر. النشر المنسق بعد 90 يومًا من
الإصلاح (أو موافقتنا المسبقة) مرحب به.

### مستويات الخطورة والمكافآت

البرنامج يعتمد مبالغ اسمية رمزية/شارات اعتماد في هذه المرحلة
(الميزانية الرسمية مع المرحلة 5)، والأثر الحقيقي اليوم: الإدراج في
قائمة الشكر في كل إصدار أمني + شارة «المساهم الأمني» في التوثيق.

| الخطورة | التعريف (مختصر) | المكافأة |
|---|---|---|
| Critical | تجاوز عزل تطبيق / قراءة بيانات مستخدم خارج الجهاز / RCE | 500$ + شارة |
| High | تسريب بيانات محلية عبر مسار غير مقصود، تجاوز الوضع المطلق | 250$ + شارة |
| Medium | تخزين غير متوقع لمحتوى مستخدم، إساءة إذن مُوثقة | 100$ + شارة |
| Low | مشكلات تعزيز (hardening) بلا أثر مباشر | شارة + شكر |

> قاعدة ذهبية: أي شيء يثبت أن «ما نعلنه في PRIVACY_POLICY.md» غير صحيح
> يُصنف High فأعلى تلقائيًا.

---

## English

### Reporting a vulnerability

Never open a public issue for a security bug. Official channel: GitHub
**private vulnerability reporting** on this repository (Security →
Report a vulnerability). If that is impossible, use the SUPPORT.md
contact and we will move to the private channel immediately.

### Our SLA

| Step | Commitment |
|---|---|
| Acknowledgement | within **72 hours** |
| Triage & severity | within **7 days** |
| Fix for Critical/High | within **30 days** of triage |
| Security release + public credit (opt-in) | with the patch release |

### Scope

- The DRS Smart Keyboard app (official branches, signed tags).
- The distribution chain: Release assets, SHA256SUMS, the CI pipeline.
- **Out of scope:** third-party sites, GitHub account credentials,
  attacks requiring an unlocked/rooted device plus platform bugs.

### Safe Harbor

Good-faith research is welcome: no exploitation after the report, no
access to other users' data, no service disruption, full details to us
before any publication. Coordinated disclosure 90 days after the fix
(or earlier with our consent).

### Severity & rewards

While the program budget formalizes with roadmap phase 5, today's
rewards are nominal bounties + permanent credits in every security
release and the "Security Contributor" badge in the docs.

| Severity | Definition (abridged) | Reward |
|---|---|---|
| Critical | App-sandbox escape / off-device user data access / RCE | $500 + badge |
| High | Local data leak through an unintended path, bypass of Absolute Privacy Mode | $250 + badge |
| Medium | Unexpected user-content storage, a documented permission misused | $100 + badge |
| Low | Hardening issues with no direct impact | Badge + credit |

> Golden rule: anything proving a PRIVACY_POLICY.md claim wrong is
> automatically High or above.
