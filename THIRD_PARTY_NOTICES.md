# Third-Party Notices | إشعارات الطرف الثالث

<div dir="rtl">

يُبنى DRS Smart Keyboard فوق مكتبات مفتوحة المصدر محترمة، ونقدّر عمل مؤلفيها.
هذه الصفحة تسرد الاعتمادات الخارجية الرئيسية وتراخيصها. التفاصيل الكاملة لكل
اعتماد موجودة في `gradle/libs.versions.toml` داخل المستودع، وتُعرض داخل التطبيق
في شاشة «تراخيص الطرف الثالث».

</div>

---

## Runtime Dependencies | الاعتمادات التشغيلية

### Android Jetpack / AndroidX (The Android Open Source Project)
**License:** Apache License 2.0 — https://opensource.org/licenses/Apache-2.0

- `androidx.activity:activity-compose`, `androidx.activity:activity-ktx`
- `androidx.appcompat`, `androidx.autofill`, `androidx.collection`
- `androidx.core:core-ktx`, `androidx.core:core-splashscreen`
- `androidx.emoji2`, `androidx.exifinterface`
- `androidx.lifecycle`, `androidx.window`
- `androidx.navigation:navigation-compose`
- `androidx.room` (runtime + compiler)
- `androidx.profileinstaller`

### Jetpack Compose (The Android Open Source Project)
**License:** Apache License 2.0

- `androidx.compose:compose-bom`, `androidx.compose.ui:*`,
  `androidx.compose.foundation`, `androidx.compose.material3`,
  `androidx.compose.material:material-icons-extended`,
  `androidx.compose.runtime:runtime-livedata`

### Kotlin & JetBrains libraries (JetBrains)
**License:** Apache License 2.0

- `org.jetbrains.kotlin:kotlin-stdlib`, `kotlin-reflect`
- `org.jetbrains.kotlinx:kotlinx-coroutines-*`
- `org.jetbrains.kotlinx:kotlinx-serialization-json`

### Coil (Coil Kotlin — image loading)
**License:** Apache License 2.0 — https://github.com/coil-kt/coil

- `io.coil-kt.coil3:coil-compose`, `io.coil-kt.coil3:coil-gif`

### Material Kolor
**License:** Apache License 2.0 — https://github.com/jordond/materialkolor

- `com.materialkolor:material-kolor-android`

### cache4k
**License:** Apache License 2.0 — https://github.com/reactivecircus/cache4k

- `io.github.reactivecircus.cache4k`

### Google KSP & KotlinPoet (build-time tooling)
**License:** Apache License 2.0

- `com.google.devtools.ksp:symbol-processing-api`
- `com.squareup:kotlinpoet`, `com.squareup:kotlinpoet-ksp`

### ICU4J (International Components for Unicode — Hijri calendar support)
**License:** ICU License (ICU 1.8.1 and later) — bundled via the Android platform.

## Test & Benchmark tooling

- Kotest (`io.kotest`) — Apache License 2.0
- `androidx.benchmark:benchmark-macro-junit4`, `androidx.test.*`, `kotlin-test` — Apache License 2.0 / The Android Open Source Project

## Bundled Data | البيانات المدمجة

### Tashkeela corpus (excerpts in the vocalization lexicon — DRS v1.2.0)
**License:** GNU General Public License v2.0 — https://www.gnu.org/licenses/old-licenses/gpl-2.0.html

- `app/src/main/assets/drs/tashkeel_lexicon.txt` embeds top-frequency
  vocalized word forms extracted from the **Tashkeela** corpus
  (community-datasets/tashkeela on Hugging Face), in addition to the
  project's own hand-reviewed seed entries.
- Extraction was deterministic (frequency voting + a documented
  pause-form policy) at development time; the runtime does no corpus
  processing — it performs a local, offline dictionary lookup only.
- Attribution and license preserved here per GPL-2.0 requirements.

---

<div dir="rtl">

## ملاحظة

جميع التراخيص أعلاه تسمح بالاستخدام وإعادة التوزيع التجاري وغير التجاري مع
الالتزام بشروطها (الاحتفاظ بإشعارات الترخيص وحقوق المؤلفين الأصلية لدى مكتباتها
الخاصة داخل مستودعاتها). هذا المشروع يوزّع المكتبات بصيغة Gradle المعتمدة دون
تعديل على مصادرها.

</div>
