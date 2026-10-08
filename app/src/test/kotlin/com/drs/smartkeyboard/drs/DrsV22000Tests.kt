/*
 * Copyright (C) 2026 The DRS Smart Keyboard Project
 */

package com.drs.smartkeyboard.drs

import com.drs.smartkeyboard.drs.ai.DrsArabicLetters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * DRS v2.20.0 «شهادة الجلالة» — the CLOSED hand-pinned family of لفظ
 * الجلالة tokens (لله والله بالله فالله كالله تالله ولله وبالله فلله)
 * takes the one mark its law demands: the shadda after the second lam.
 *
 * The legal story this spec pins:
 *
 *  1. **The majesty family is CERTIFIED** — each of the nine members is
 *     an exact enumerated string whose only reading is the majesty name
 *     (its tail لله is FIXED, so no والد-style open-stem ambiguity can
 *     reach it). The engine's [DrsArabicLetters.majestyShaddaForm]
 *     inserts the shadda and nothing else — the same partial style the
 *     bare (v2.17.0) and glued (v2.19.0) layers speak — and the law
 *     layer slots it after the glued layer: seed → glued → majesty →
 *     bare.
 *  2. **Ownership stays honest** — الله واللهم belong to the bare
 *     article law (no letter before ال), the seed lexicon keeps its
 *     hand-reviewed full harakat for لله والله بالله («البذرة تفوز»),
 *     and the majesty layer owns only the six unseeded prefixed members
 *     plus the family as a fallback for the three seeded ones.
 *  3. **R27's proof is untouched** — the OPEN shape [حرف + ال + شمسية]
 *     (والد، بالش، بالطو) stays byte-identical: none of its members
 *     ends in لله. A closed table is not a pattern rule over an open
 *     class — carving the closed family out of the reasoned silence is
 *     a documented decision, and the old pins must keep passing.
 *  4. **The cursor stays honest** — the advisor adds NO cursor rule for
 *     the family: the majesty shadda lands mid-word (واللَّه), and the
 *     cursor cannot know whether والل is heading for والله or واللسان —
 *     so the certification travels as a word-REPLACEMENT chip on the
 *     harakat board, never as a cursor-insert pick.
 */
class DrsV22000Tests : FunSpec({

    fun advise(text: String) = DrsHarakatAdvisor.advise(text)
    fun f(text: String) = DrsTextTools.apply(DrsTextTool.TASHKEEL_TEXT, text)

    // ------------------------------------------------------------------
    // 1) المحرك — majestyShaddaForm: العائلة المغلقة التسع
    // ------------------------------------------------------------------

    test("every one of the nine majesty tokens takes its shadda") {
        // the shadda lands after the SECOND lam of the fixed لله tail —
        // and nothing else is added (partial style):
        DrsArabicLetters.majestyShaddaForm("لله").shouldNotBeNull()
        DrsArabicLetters.majestyShaddaForm("والله") shouldBe "والل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("بالله") shouldBe "بالل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("فالله") shouldBe "فالل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("كالله") shouldBe "كالل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("تالله") shouldBe "تالل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("ولله") shouldBe "ولل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("وبالله") shouldBe "وبالل\u0651ه"
        DrsArabicLetters.majestyShaddaForm("فلله") shouldBe "فلل\u0651ه"
    }

    test("the bare jar ل token لله carries the shadda on its own second lam") {
        // لله is three letters: the shadda sits between the double lam
        // and the ha — ل ل َّ ه:
        DrsArabicLetters.majestyShaddaForm("لله") shouldBe "لل\u0651ه"
    }

    test("the majesty table refuses every token outside the closed nine") {
        // the R27 evidence tokens — open-stem ambiguity — stay null:
        DrsArabicLetters.majestyShaddaForm("والد").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("والدين").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("بالش").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("بالطو").shouldBeNull()
        // real words that merely END near the family's tail:
        DrsArabicLetters.majestyShaddaForm("واللسان").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("مهله").shouldBeNull()
        // prefixes the family never pinned — closed means closed:
        DrsArabicLetters.majestyShaddaForm("للله").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("آلله").shouldBeNull()
        // إله is a DIFFERENT word (ilāh — no shadda) and starts with a
        // hamza carrier, not the majesty skeleton:
        DrsArabicLetters.majestyShaddaForm("إله").shouldBeNull()
        // bare-law territory: الله واللهم have no letter before ال —
        // the v2.17.0 layer already owns them, the majesty table does
        // not (one law per shape):
        DrsArabicLetters.majestyShaddaForm("الله").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("اللهم").shouldBeNull()
        // glued-layer territory: للش opens with the double-lam collapse:
        DrsArabicLetters.majestyShaddaForm("للش").shouldBeNull()
        // partial prefixes are not members:
        DrsArabicLetters.majestyShaddaForm("وال").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("لل").shouldBeNull()
    }

    test("the majesty layer refuses already-marked tokens (idempotent)") {
        // the law output carries a mark — a second call must refuse it:
        val once = DrsArabicLetters.majestyShaddaForm("والله").shouldNotBeNull()
        DrsArabicLetters.majestyShaddaForm(once).shouldBeNull()
        // a partially-marked token is not the engine's to touch:
        DrsArabicLetters.majestyShaddaForm("وَالله").shouldBeNull()
        DrsArabicLetters.majestyShaddaForm("تالل\u0651ه").shouldBeNull()
    }

    // ------------------------------------------------------------------
    // 2) طبقة القانون — الشلال: البذرة ثم الملتصقة ثم الجلالة ثم المجردة
    // ------------------------------------------------------------------

    test("the law layer certifies the six unseeded majesty members") {
        f("تالله") shouldBe "تالل\u0651ه"
        f("فالله") shouldBe "فالل\u0651ه"
        f("كالله") shouldBe "كالل\u0651ه"
        f("ولله") shouldBe "ولل\u0651ه"
        f("وبالله") shouldBe "وبالل\u0651ه"
        f("فلله") shouldBe "فلل\u0651ه"
        // punctuation and spacing survive verbatim — and BOTH members
        // of a single text take their shadda (فالله والله، كلاهما
        // مغلقان لا تملكهما البذرة المدمجة ولا المنصّبة):
        f("تالله!") shouldBe "تالل\u0651ه!"
        f("فالله تالله") shouldBe "فالل\u0651ه تالل\u0651ه"
    }

    test("the seed lexicon wins over the majesty law (البذرة تفوز)") {
        // In pure-JVM tests the lexicon starts empty (the asset loads on
        // a background thread in the app process) — so the seed
        // precedence is proven by installing the REAL seed rows for the
        // three seeded members, parsed through the production parser
        // (format contract honored). The majesty layer must not steal
        // the hand-reviewed full harakat:
        DrsTashkeelLexicon.install(
            DrsTashkeelLexicon.parse(
                listOf(
                    // verbatim rows from app/src/main/assets/drs/tashkeel_lexicon.txt:
                    "لله\tلِلَّه",
                    "والله\tوَاَللَّه",
                    "بالله\tبِاَللَّه",
                ),
            ).entries,
        )
        // hand-reviewed forms stay untouched by the law:
        f("لله") shouldBe "لِلَّه"
        f("والله") shouldBe "وَاَللَّه"
        f("بالله") shouldBe "بِاَللَّه"
        // ...while an unknown majesty member right next to them still
        // takes the law shadda (the lexicon first, the law after):
        f("تالله") shouldBe "تالل\u0651ه"
        // and the stripped-form matching (v1.2.0, documented) sends even
        // a partially-marked seeded token back to the hand-reviewed
        // canonical form:
        f("وَالله") shouldBe "وَاَللَّه"
    }

    test("the built-in seed owns الله and the open R27 proof stays silent") {
        // الله is in the hand-reviewed BUILT-IN seed (DrsWordTashkeel —
        // the most-frequent map that answers first): البذرة تفوز —
        // its canonical form carries shadda AND fatha, richer than any
        // law layer may add; the typing surface keeps R3b's shadda chip.
        // BYTE NOTE (honest pin): the built-in seed writes fatha before
        // shadda (U+064E U+0651) while the 3000-word asset writes shadda
        // before fatha (U+0651 U+064E) — canonically EQUIVALENT orders,
        // byte-different strings; this pin freezes the built-in seed's
        // own bytes, the layer that actually answers first.
        f("الله") shouldBe "اللَّه"
        // the open [حرف + ال + شمسية] shape keeps its v2.19.0 reasoned
        // silence — the closed majesty table carved NOTHING out of it:
        f("والد") shouldBe "والد"
        f("والدين") shouldBe "والدين"
        f("بالش") shouldBe "بالش"
        f("بالطو") shouldBe "بالطو"
        f("واللسان") shouldBe "واللسان"
    }

    test("the majesty layer is idempotent inside the whole-text tool") {
        val once = f("تالله")
        f(once) shouldBe once
    }

    test("a partially-marked majesty token passes through byte-identical") {
        // honest rule: the law never touches a token that already
        // carries a mark anywhere — تالله is unseeded, so its marked
        // forms fall through every layer untouched:
        f("تاللهِ") shouldBe "تاللهِ"
        f("تالل\u0651ه") shouldBe "تالل\u0651ه"
    }

    // ------------------------------------------------------------------
    // 3) المؤشر — المستشار يبقى صامتًا عن العائلة، والشلال لا يُسرق
    // ------------------------------------------------------------------

    test("the advisor stays silent on majesty tokens by design") {
        // the majesty shadda lands MID-WORD (واللَّه) — a cursor-insert
        // pick cannot place it, and mid-typing والل is genuinely
        // ambiguous (والله vs واللسان). The certification travels as
        // the word-replacement chip on the harakat board instead:
        advise("والله").shouldBeEmpty()
        advise("تالله").shouldBeEmpty()
        advise("وبالله").shouldBeEmpty()
        // and the ambiguous prefix stays silent exactly as R27 pinned:
        advise("والد").shouldBeEmpty()
        advise("بالش").shouldBeEmpty()
    }

    test("the majesty law steals no rule before or after it") {
        // R3 still owns the bare article's lam sukun:
        advise("ال").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_LAM
        // R3b still owns the bare-article sun form:
        advise("الش").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
        // R3c still owns the collapsed لل form:
        advise("للش").first().reason shouldBe DrsHarakatAdvisor.AdviceReason.DEFINITE_SUN
        // the bare law layer still owns الشرق in the whole-text tool:
        f("الشرق") shouldBe "الش\u0651رق"
        // and the glued layer still owns للصبر:
        f("للصبر") shouldBe "للص\u0651بر"
    }
})
