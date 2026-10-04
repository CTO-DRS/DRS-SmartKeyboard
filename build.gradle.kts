/*
 * Copyright (C) 2022-2025 The DRS Smart Keyboard Project
 */

plugins {
    alias(libs.plugins.agp.application) apply false
    alias(libs.plugins.agp.library) apply false
    alias(libs.plugins.agp.test) apply false
    alias(libs.plugins.kotest) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.kover) apply false
    alias(libs.plugins.ksp) apply false
}

// ---------------------------------------------------------------------------
// DRS — تحصين مسار البناء (build-time classpath hardening).
//
// مكتبات إضافات Gradle (AGP/bundletool، jetifier، kover/intellij-coverage)
// تجرّ تبعيات ثغرة عبريًا تعمل على آلة المطور/CI فقط — لا شيء منها يدخل APK.
// هذه القوة الإجبارية ترفعها إلى أول إصدار مرقّع وفق تقرير Dependabot:
//   bcprov/bcpkix 1.85   — يطفئ critical GHSA-574f-3g2m-x479 + high GHSA-qp49-qgx5-5m26
//                          + medium GHSA-c3fc-8qff-9hwx (+ bcpkix GHSA-wg6q-6289-32hp)
//   freemarker 2.3.35    — يطفئ critical GHSA-27j2-h3m2-8237
//   jose4j 0.9.6         — يطفئ high GHSA-3677-xxcr-wjqv
//   jdom2 2.0.6.1        — يطفئ high GHSA-2363-cqg2-863c
//   commons-lang3 3.18.0 — يطفئ medium GHSA-j288-q9x7-2f5v
// الأثر وقت البناء حصرًا: :app:releaseRuntimeClasspath خالٍ من هذه الحزم
// (تحقق: ./gradlew :app:dependencyInsight --configuration releaseRuntimeClasspath).
// ---------------------------------------------------------------------------
buildscript {
    configurations.classpath {
        resolutionStrategy {
            force(
                "org.bouncycastle:bcprov-jdk18on:1.85",
                "org.bouncycastle:bcpkix-jdk18on:1.85",
                "org.bouncycastle:bcutil-jdk18on:1.85",
                "org.freemarker:freemarker:2.3.35",
                "org.bitbucket.b_c:jose4j:0.9.6",
                "org.jdom:jdom2:2.0.6.1",
                "org.apache.commons:commons-lang3:3.18.0",
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DRS — تحصين رسم تبعيات المشاريع (project-graph hardening, v2.1.1).
//
// خريطة الحل الكاملة (كل قابلة للحل × كل مشروع، أداة deps_map.init.gradle)
// أظهرت أن الثغرات الـ54 المفتوحة في Dependabot تتركز في مصدرين، كلاهما
// خارج APK المستخدم:
//   1) قوابل AGP الداخلية لمنصة الاختبار الموحدة (_internal-unified-test-
//      platform-*) في كل الوحدات: netty 4.1.93/4.1.110، bouncycastle 1.79،
//      protobuf 3.24.4، httpclient 4.5.6، commons-lang3 3.16.0 — وقت بناء
//      واختبار آلة المطور/CI حصرًا.
//   2) وحدة benchmark: wire-runtime-jvm 5.2.1 (عبريًا من androidx.benchmark)
//      في قوابل التشغيل/الترجمة الخاصة بها — APK قياس مستقل لا يوزَّع.
// القوى أدناه ترفع كل عائلة إلى أول إصدار مرقّع وفق تقرير Dependabot:
//   netty-*        4.1.137.Final — يطفئ critical (handler <= 4.1.136) + high/medium/low
//   bcprov/bcpkix/bcutil 1.85     — يطفئ critical×2 + high + medium (اتساقًا مع classpath أعلاه)
//   protobuf-*     3.25.5         — يطفئ high (java/kotlin < 3.25.5)
//   httpclient     4.5.13         — يطفئ medium
//   commons-lang3  3.18.0         — يطفئ medium (اتساقًا مع classpath أعلاه)
//   wire-runtime-jvm 6.4.5        — يطفئ high×3 (wire-runtime <= 6.4.4 / <= 6.2.0)
// التنبيه الأخير (kotlin-gradle-plugin، medium، GHSA-r937-wjx7-w2jp /
// CVE-2026-53914) أُطفئ من مصدره لا بقوة إجبارية: الرقعة المستقرة 2.4.20
// صدرت على Maven Central (نافذة الإصلاح تبدأ من 2.4.20-Beta1 وفق OSV) فرُقّيت
// أداة البناء نفسها في كتالوج الإصدارات libs.versions.toml، وتتبعها إضافات
// Kotlin كلها (android/jvm/compose/serialization) بمرجع واحد — ترقية أدوات
// بناء مستقرة بدل إجبارها على بيتا، قبولُ المخاطرة السابق انتفى بصدورها.
// تحقق لاحق: ./gradlew -I deps_map.init.gradle drsDepMap — صفر مطابقات
// ضمن النطاقات الثغرة، و:app:releaseRuntimeClasspath خالٍ من العائلات كلها.
// ---------------------------------------------------------------------------
subprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "io.netty:netty-buffer:4.1.137.Final",
            "io.netty:netty-codec:4.1.137.Final",
            "io.netty:netty-codec-http:4.1.137.Final",
            "io.netty:netty-codec-http2:4.1.137.Final",
            "io.netty:netty-codec-socks:4.1.137.Final",
            "io.netty:netty-common:4.1.137.Final",
            "io.netty:netty-handler:4.1.137.Final",
            "io.netty:netty-handler-proxy:4.1.137.Final",
            "io.netty:netty-resolver:4.1.137.Final",
            "io.netty:netty-transport:4.1.137.Final",
            "io.netty:netty-transport-native-unix-common:4.1.137.Final",
            "org.bouncycastle:bcprov-jdk18on:1.85",
            "org.bouncycastle:bcpkix-jdk18on:1.85",
            "org.bouncycastle:bcutil-jdk18on:1.85",
            "com.google.protobuf:protobuf-java:3.25.5",
            "com.google.protobuf:protobuf-kotlin:3.25.5",
            "com.google.protobuf:protobuf-java-util:3.25.5",
            "org.apache.httpcomponents:httpclient:4.5.13",
            "org.apache.commons:commons-lang3:3.18.0",
            "com.squareup.wire:wire-runtime-jvm:6.4.5",
        )
    }
}
