# سياسة الخصوصية — Privacy Policy — Politique de confidentialité

> يصلح هذا الملف مع الإصدار v2.2.0 وما بعده. الخلاصة بسطر واحد
> بكل اللغات: **لوحة المفاتيح لا ترسل كتابتك أو صوتك أو بياناتك إلى
> أي مكان — والحوسبة كلها على جهازك.**
>
> This policy applies from v2.2.0. One-line summary in every language:
> **the keyboard never sends your typing, voice, or data anywhere —
> all computation happens on your device.**

---

## العربية

### المبدأ الحاكم

كل ما تكتبه يبقى على جهازك. نحن — فريق التطوير — **لا نستطيع**
قراءة كتابتك حتى لو أردنا: لا يوجد خادم يستقبلها، ولا قناة شبكية
في قلب لوحة المفاتيح تصل إليها، والبنية موثقة ومثبتة بفحص آلي في
CI (`ci/privacy_canary.sh`) يكسر البناء لو أضاف أي مساهم مكدس شبكة
لمحرك الكتابة.

### البيانات المخزنة على جهازك (ولمكانها)

1. **جدول تعلم شخصي:** كلماتك متكررة الاستخدام وتراكيبها (بلا محتوى
   جمل كامل، بلا أرقام) — ملف واحد محدود الحجم في مجلد خاص لا تقرأه
   النسخ الاحتياطية السحابية.
2. **سجل أعطال مطهر:** عند انهيار نادر، يُحفظ اسم صنف الاستثناء
   وبصمة مجردة فقط — لا رسالة الاستثناء ولا أي نص كتبته. تصديره
   يدوي بنقرتك.
3. **إعداداتك ووسائط حافظتك وملفات تحديث مؤقتة:** مواضع معلنة
   ومفصلة في docs/GDPR_COMPLIANCE.md.

عرض هذه البيانات وأحجامها: **شاشة الخصوصية والأمان** داخل التطبيق.

### ما لا نجمعه (بأي شكل)

لا أسماء، لا إيميلات، لا موقع، لا معرفات إعلانية أو تثبيت، لا
تحليلات، لا تتبع، لا إعلانات، ولا أي طلب شبكي يخرج من التطبيق
افتراضيًا.

### القنوات الشبكية الوحيدة (كلها اختيارية ومغلقة افتراضيًا)

| القناة | متى تعمل | ماذا ترسل |
|---|---|---|
| فاحص التحديث | فقط حين تفتح مركز التحديث وتضغط فحصًا | طلب HTTPS مجهول لـGitHub Releases |
| كتالوج حزم flex | فقط حين تفتح مركز الحزم | طلب HTTPS مجهول لبيان الحزم |
| الإدخال الصوتي (AUTO/STANDARD فقط) | فقط حين تضغط الميكروفون | الصوت يذهب إلى خدمة الكلام في النظام (Google) — افتراضيتنا ON_DEVICE_ONLY تمنع هذا كليًا |

### وضع الخصوصية المطلق

مفتاح واحد في شاشة الخصوصية يغلق الجدول أعلاه كله: يرفض فحص
التحديثات وتنزيلها وكتالوج الحزم، ويُلزم المُعرِّف بالعمل دون اتصال.
وتحته **إثبات حي**: عدّادات بايتات الشبكة لتطبيقك منذ بدء الجلسة —
لو تحركت عن الصفر فذلك دليل على مكالمة ما؛ وهو ما لا يحدث مع
الوضع المطلق. عدّاداتك لعملية التطبيق حصرًا — صوت خدمة النظام لا
يمر عبرها، ولذلك الإغلاق المسبق (المطلق) هو الضمانة.

### حقوقك (GDPR)

تصدير مشفر (DRSYNC1 بعبارة سرّك)، حذف شامل فوري، تعطيل أي ميزة —
كلها أزرار في شاشة الخصوصية. التفصيل القانوني في
docs/GDPR_COMPLIANCE.md.

### الإفصاح المنسق

اكتشفت ثغرة؟ SECURITY.md (تقارير خاصة عبر GitHub، Safe Harbor كامل).

---

## English

### Governing principle

Everything you type stays on your device. We — the developers —
**cannot** read your typing even if we wanted to: no server receives
it, and the keyboard core has no network channel to it, proven by an
automated CI check (`ci/privacy_canary.sh`) that breaks the build if
any contributor ever introduces a network stack into the engine.

### What is stored on your device (and where)

1. **Personal learning table:** your frequently used words, pairs and
   two-word continuations (never full sentences, never digits) — one
   size-capped file in a private folder excluded from cloud backups.
2. **Sanitized crash registry:** on a rare crash we keep the exception
   class name and a fingerprint only — never the exception message,
   never any text you typed. Export is manual, one tap.
3. **Your settings, clipboard media, temporary update files:** all
   documented in docs/GDPR_COMPLIANCE.md.

The privacy screen inside the app shows this inventory with real
sizes, live.

### What we never collect (in any form)

No names, no emails, no location, no advertising or install IDs, no
analytics, no tracking, no ads, and by default no network request
leaves the app at all.

### The only network channels (all optional, all off by default)

| Channel | When active | What it sends |
|---|---|---|
| Update checker | only when you open Update Center and tap Check | an anonymous HTTPS request to GitHub Releases |
| flex package catalog | only when you open Package Center | an anonymous HTTPS request for the catalog manifest |
| Voice input (AUTO/STANDARD only) | only when you press the mic | audio goes to the system speech service (Google) — our default ON_DEVICE_ONLY prevents this entirely |

### Absolute Privacy Mode

One switch in the privacy screen closes the whole table above: update
checks and downloads and the package catalog are refused, and the
recognizer is pinned to on-device only. Beneath it sits a **live
proof**: your app's network byte counters since session start. They
move only if our code moved bytes — which Absolute Mode prevents.

### Your rights (GDPR)

Encrypted export (DRSYNC1 with your own passphrase), one-tap full
deletion, disabling any feature — all buttons in the privacy screen.
Legal detail: docs/GDPR_COMPLIANCE.md.

### Coordinated disclosure

Found a vulnerability? SECURITY.md — GitHub private reports, full
Safe Harbor.

---

## Français — Résumé

**Principe:** tout ce que vous tapez reste sur votre appareil — aucun
serveur ne le reçoit, et le noyau du clavier n'a aucune voie réseau
(preuve automatisée dans notre CI).

**Données sur l'appareil:** table d'apprentissage personnelle (mots
fréquents), registre d'accidents anonymisé (nom de classe + empreinte
seulement), réglages, médias du presse-papiers. Tout est visible avec
ses tailles dans l'écran « Confidentialité et sécurité » de
l'application.

**Aucune collecte:** pas de noms, d'emails, de géolocalisation, d'IDs
publicitaires, d'analytique, de publicité.

**Canaux réseau (tous optionnels, désactivés par défaut):** vérificateur
de mises à jour (HTTPS anonyme vers GitHub), catalogue d'extensions,
dictée vocale — par défaut strictement hors-ligne (ON_DEVICE_ONLY).

**Mode de confidentialité absolue:** un seul interrupteur coupe tous
ces canaux et affiche la preuve en direct (compteurs d'octets réseau
de l'application). Droits RGPD: export chiffré, suppression totale en
un appui. Contact: SECURITY.md.

## Español — Resumen

**Principio:** todo lo que escribes se queda en tu dispositivo —
ningún servidor lo recibe y el núcleo del teclado no tiene canal de
red (prueba automatizada en nuestro CI).

**Datos en el dispositivo:** tabla de aprendizaje personal (palabras
frecuentes), registro de fallos anonimizado (solo clase + huella),
ajustes, medios del portapapeles. Todo visible con tamaños reales en
la pantalla «Privacidad y seguridad».

**No recopilamos:** nombres, correos, ubicación, IDs publicitarios,
analítica, publicidad.

**Canales de red (todos opcionales, desactivados por defecto):**
verificador de actualizaciones (HTTPS anónimo a GitHub), catálogo de
extensiones, dictado por voz — por defecto estrictamente sin conexión
(ON_DEVICE_ONLY).

**Modo de privacidad absoluta:** un solo interruptor cierra esos
canales y muestra la prueba en vivo (contadores de bytes de red de la
app). Derechos RGPD: exportación cifrada, borrado total con un toque.
Contacto: SECURITY.md.

## Deutsch — Zusammenfassung

**Grundsatz:** Alles, was Sie tippen, bleibt auf Ihrem Gerät — kein
Server empfängt es, und der Tastatur-Kern hat keinen Netzkanal
(automatisiert in unserer CI nachgewiesen).

**Daten auf dem Gerät:** persönliche Lernliste (häufige Wörter),
anonymisiertes Absturzregister (nur Klassenname + Fingerabdruck),
Einstellungen, Zwischenablage-Medien. Alles mit echten Größen im
Bildschirm „Privatsphäre und Sicherheit" sichtbar.

**Wir sammeln nichts:** keine Namen, E-Mails, Standort, Werbe-IDs,
Analytik, Werbung.

**Netzkanäle (alle optional, standardmäßig aus):**
Update-Prüfung (anonymes HTTPS zu GitHub), Erweiterungskatalog,
Sprachdiktat — standardmäßig strikt offline (ON_DEVICE_ONLY).

**Absoluter Privatsphäre-Modus:** ein Schalter schließt alle Kanäle
und zeigt den Live-Beweis (Netzwerk-Byte-Zähler der App). DSGVO-Rechte:
verschlüsselter Export, vollständige Löschung mit einem Tipp. Kontakt:
SECURITY.md.
