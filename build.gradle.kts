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
