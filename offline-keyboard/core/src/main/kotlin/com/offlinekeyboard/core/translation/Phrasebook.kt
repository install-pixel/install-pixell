package com.offlinekeyboard.core.translation

import com.offlinekeyboard.core.layout.Language

/**
 * Everyday phrases with a fixed, checked translation. Small offline models are weakest exactly on such
 * short phrases ("Як справи?"), so a sentence that is one of these is never sent to the model.
 * Matching ignores case, surrounding punctuation and apostrophe style; the result keeps the sentence's
 * end punctuation and capital letter.
 */
object Phrasebook {

    // Ukrainian | Polish | English, written as inside a sentence. When a phrase appears twice in a
    // column, the first row wins.
    private val rows: List<Triple<String, String, String>> = listOf(
        Triple("привіт", "cześć", "hi"),
        Triple("як справи", "jak się masz", "how are you"),
        Triple("як у тебе справи", "jak się masz", "how are you doing"),
        Triple("добрий день", "dzień dobry", "good afternoon"),
        Triple("доброго ранку", "dzień dobry", "good morning"),
        Triple("добрий вечір", "dobry wieczór", "good evening"),
        Triple("на добраніч", "dobranoc", "good night"),
        Triple("до побачення", "do widzenia", "goodbye"),
        Triple("дякую", "dziękuję", "thank you"),
        Triple("дуже дякую", "bardzo dziękuję", "thank you very much"),
        Triple("будь ласка", "proszę", "please"),
        Triple("вибачте", "przepraszam", "sorry"),
        Triple("так", "tak", "yes"),
        Triple("ні", "nie", "no"),
        Triple("добре", "dobrze", "okay"),
        Triple("все добре", "wszystko w porządku", "everything is fine"),
        Triple("у мене все добре", "u mnie wszystko dobrze", "I'm fine"),
        Triple("я не розумію", "nie rozumiem", "I don't understand"),
        Triple("я не знаю", "nie wiem", "I don't know"),
        Triple("скільки це коштує", "ile to kosztuje", "how much does it cost"),
        Triple("де туалет", "gdzie jest toaleta", "where is the toilet"),
        Triple("приємно познайомитися", "miło mi cię poznać", "nice to meet you"),
        Triple("що ти робиш", "co robisz", "what are you doing"),
        Triple("де ти", "gdzie jesteś", "where are you"),
        Triple("я тебе кохаю", "kocham cię", "I love you"),
        Triple("з днем народження", "wszystkiego najlepszego z okazji urodzin", "happy birthday"),
        Triple("вітаю", "gratulacje", "congratulations"),
        Triple("до зустрічі", "do zobaczenia", "see you"),
        Triple("до завтра", "do jutra", "see you tomorrow"),
        Triple("гарного дня", "miłego dnia", "have a nice day"),
        Triple("смачного", "smacznego", "enjoy your meal"),
        Triple("я зайнятий", "jestem zajęty", "I'm busy"),
        Triple("я запізнюся", "spóźnię się", "I'll be late"),
        Triple("зателефонуй мені", "zadzwoń do mnie", "call me"),
        Triple("я скоро буду", "zaraz będę", "I'll be there soon"),
        Triple("що сталося", "co się stało", "what happened"),
        Triple("звісно", "oczywiście", "of course"),
        Triple("без проблем", "nie ma problemu", "no problem"),
        Triple("не хвилюйся", "nie martw się", "don't worry"),
        Triple("зачекай", "poczekaj", "wait"),
        Triple("я згоден", "zgadzam się", "I agree"),
        Triple("котра година", "która godzina", "what time is it"),
        Triple("як тебе звати", "jak masz na imię", "what's your name"),
        Triple("звідки ти", "skąd jesteś", "where are you from"),
        Triple("я з України", "jestem z Ukrainy", "I'm from Ukraine"),
        Triple("я не говорю польською", "nie mówię po polsku", "I don't speak Polish"),
        Triple("ти говориш англійською", "czy mówisz po angielsku", "do you speak English"),
    )

    private fun column(row: Triple<String, String, String>, language: Language) = when (language) {
        Language.UK -> row.first
        Language.PL -> row.second
        Language.EN -> row.third
    }

    private val index: Map<Language, Map<String, Int>> = Language.entries.associateWith { language ->
        val map = HashMap<String, Int>()
        rows.forEachIndexed { i, row -> map.putIfAbsent(normalize(column(row, language)), i) }
        map
    }

    private const val EDGE = " \t.!?…,;:\"«»„”“()"

    private fun normalize(text: String) = text.lowercase()
        .replace('’', '\'').replace('ʼ', '\'')
        .trim { it in EDGE }
        .replace(Regex("\\s+"), " ")

    /** The fixed translation of [sentence], or null when it is not an everyday phrase. */
    fun lookup(sentence: String, direction: TranslationDirection): String? {
        val row = index.getValue(direction.source)[normalize(sentence)] ?: return null
        // Rows keep their own capitals ("I", "Україна"); the first letter follows the source sentence.
        var result = column(rows[row], direction.target)
        val firstLetter = sentence.firstOrNull { it.isLetter() }
        if (firstLetter?.isUpperCase() == true) result = result.replaceFirstChar { it.uppercase(direction.target.locale) }
        val end = sentence.trimEnd().takeLastWhile { it in ".!?…" }
        return result + end
    }
}
