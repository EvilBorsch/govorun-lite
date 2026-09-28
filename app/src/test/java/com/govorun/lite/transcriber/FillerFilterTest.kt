package com.govorun.lite.transcriber

import org.junit.Assert.assertEquals
import org.junit.Test

class FillerFilterTest {

    private fun check(input: String, expected: String) =
        assertEquals(expected, FillerFilter.apply(input))

    @Test fun userSample() = check(
        "Привет, э-э, как дела? Э-э-э, а как погода? Хмм... Я даже не знаю, что ещё сказать. " +
            "Ну, типа, вот конец предложения. Ну, это как бы пример такого текста, где много слов-паразитов.",
        "Привет, как дела? А как погода? Я даже не знаю, что ещё сказать. " +
            "Вот конец предложения. Это пример такого текста, где много слов-паразитов."
    )

    // Real GigaAM output captured on the phone.
    @Test fun realPhoneSample() = check(
        "Спички. Так. Вот я зажал. А, надо из кармана свечку. А-а-а! Понятно. Вот, и я беру, типа, подношу.",
        "Спички. Так. Вот я зажал. А, надо из кармана свечку. Понятно. И я беру подношу."
    )

    // --- hesitations ---
    @Test fun hesitationBetweenCommas() = check("Привет, э-э, как дела?", "Привет, как дела?")
    @Test fun hesitationBeforeQuestion() = check("Как дела, э-э?", "Как дела?")
    @Test fun hesitationMidSentence() = check("Я пришёл э-э и ушёл.", "Я пришёл и ушёл.")
    @Test fun hesitationAfterCommaNoTrailing() = check("Привет, эм как дела", "Привет, как дела")
    @Test fun hesitationSentence() = check("Привет. Хмм. Как дела?", "Привет. Как дела?")
    @Test fun hesitationOnly() = check("Э-э-э.", "")
    @Test fun hesitationAtEnd() = check("Я не знаю, ммм.", "Я не знаю.")
    @Test fun longAaa() = check("А-а-а, понятно.", "Понятно.")
    @Test fun ellipsisPauseInsideSentence() = check("Я, хмм... не знаю.", "Я, не знаю.")
    @Test fun doubleHesitation() = check("Э-э, эм, привет.", "Привет.")

    // --- filler words ---
    @Test fun nuAtStart() = check("Ну, давай.", "Давай.")
    @Test fun nuNoComma() = check("Ну давай завтра.", "Давай завтра.")
    @Test fun tipaCommaBounded() = check("Он, типа, пришёл.", "Он пришёл.")
    @Test fun tipaToGo() = check("Мы поели, типа того.", "Мы поели.")
    @Test fun kakBy() = check("Это как бы важно.", "Это важно.")
    @Test fun kakByCommas() = check("Он, как бы, не против.", "Он не против.")
    @Test fun koroche() = check("Короче, я пошёл.", "Я пошёл.")
    @Test fun voAtEnd() = check("Я всё сделал, вот.", "Я всё сделал.")
    @Test fun vObshem() = check("В общем, всё хорошо.", "Всё хорошо.")
    @Test fun takSkazat() = check("Это, так сказать, эксперимент.", "Это эксперимент.")
    @Test fun etoSamoe() = check("Я, это самое, забыл.", "Я забыл.")
    @Test fun chain() = check("Ну, короче, типа, я пошёл.", "Я пошёл.")

    // --- must be kept ---
    @Test fun keepTipaNoun() = check("Есть два типа файлов.", "Есть два типа файлов.")
    @Test fun keepKorocheComparative() = check("Этот путь короче.", "Этот путь короче.")
    @Test fun keepVotDemonstrative() = check("Вот дом, где я живу.", "Вот дом, где я живу.")
    @Test fun keepNuAlone() = check("Ну?", "Ну?")
    @Test fun keepVotAlone() = check("Вот!", "Вот!")
    @Test fun keepKakByConditional() = check("Как бы ты поступил?", "Как бы ты поступил?")
    @Test fun keepKakByPronoun() = check("Не знаю, как бы это сказать.", "Не знаю, как бы это сказать.")
    @Test fun keepKakByToNiBylo() = check("Но как бы то ни было, мы идём.", "Но как бы то ни было, мы идём.")
    @Test fun keepNuKa() = check("Ну-ка покажи.", "Ну-ка покажи.")
    @Test fun keepConjunctionA() = check("А вот и я.", "А вот и я.")
    @Test fun keepMetres() = check("До дома 5 м.", "До дома 5 м.")
    @Test fun keepVObshemICelom() = check("В общем и целом нормально.", "В общем и целом нормально.")
    @Test fun keepHyphenatedWord() = check("Много слов-паразитов.", "Много слов-паразитов.")
    @Test fun untouchedText() = check("Обычный текст без мусора.", "Обычный текст без мусора.")
    @Test fun blank() = check("   ", "   ")
}
