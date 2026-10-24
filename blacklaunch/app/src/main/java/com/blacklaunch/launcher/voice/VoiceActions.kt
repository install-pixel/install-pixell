package com.blacklaunch.launcher.voice

import com.blacklaunch.launcher.search.SearchNormalizer

/** Групи голосових дій — кожну можна вимкнути в налаштуваннях окремо. */
enum class VoiceActionKind { ALARM, FLASHLIGHT, MEDIA, PANELS, DISPLAY, NAVIGATION, CALLS, SMS, MESSENGERS, CALENDAR, TIME }

enum class MediaCommand { PLAY_PAUSE, PLAY, PAUSE, NEXT, PREVIOUS }

enum class VolumeChange { UP, DOWN, MUTE }

enum class SystemPanel { WIFI, BLUETOOTH, INTERNET }

/** Дія, яку BlackLaunch виконує сам або відкриває з готовими даними (надсилає завжди користувач). */
sealed interface VoiceAction {
    val kind: VoiceActionKind

    data class Alarm(val hour: Int, val minute: Int) : VoiceAction {
        override val kind get() = VoiceActionKind.ALARM
    }

    data class Timer(val seconds: Int) : VoiceAction {
        override val kind get() = VoiceActionKind.ALARM
    }

    /** [on] = null — перемкнути. */
    data class Flashlight(val on: Boolean?) : VoiceAction {
        override val kind get() = VoiceActionKind.FLASHLIGHT
    }

    data class Media(val command: MediaCommand) : VoiceAction {
        override val kind get() = VoiceActionKind.MEDIA
    }

    data class Volume(val change: VolumeChange) : VoiceAction {
        override val kind get() = VoiceActionKind.MEDIA
    }

    data class Panel(val panel: SystemPanel) : VoiceAction {
        override val kind get() = VoiceActionKind.PANELS
    }

    data class Navigate(val destination: String) : VoiceAction {
        override val kind get() = VoiceActionKind.NAVIGATION
    }

    data class Call(val name: String) : VoiceAction {
        override val kind get() = VoiceActionKind.CALLS
    }

    data class Sms(val name: String, val body: String) : VoiceAction {
        override val kind get() = VoiceActionKind.SMS
    }

    data object Time : VoiceAction {
        override val kind get() = VoiceActionKind.TIME
    }

    /** Яскравість у відсотках (0–100). */
    data class BrightnessLevel(val percent: Int) : VoiceAction {
        override val kind get() = VoiceActionKind.DISPLAY
    }

    /** «Яскравіше» / «темніше». */
    data class BrightnessStep(val up: Boolean) : VoiceAction {
        override val kind get() = VoiceActionKind.DISPLAY
    }

    /** Чат WhatsApp з контактом і готовим текстом — надсилає користувач. */
    data class WhatsApp(val name: String, val body: String) : VoiceAction {
        override val kind get() = VoiceActionKind.MESSENGERS
    }

    /**
     * Нова подія в календарі — відкривається вікно календаря для підтвердження.
     * [dayOffset] — 0 сьогодні, 1 завтра…; [hour] = null — на весь день.
     */
    data class CalendarEvent(val title: String, val dayOffset: Int, val hour: Int?, val minute: Int) : VoiceAction {
        override val kind get() = VoiceActionKind.CALENDAR
    }
}

/**
 * Локальний розбір голосових дій українською, польською та англійською, без ШІ.
 * Працює з нормалізованими словами; тексти SMS і назви місць беруться з оригінальної фрази.
 */
object VoiceActionParser {
    fun parse(original: String, words: List<String>): VoiceAction? {
        if (words.isEmpty()) return null
        val phrase = words.joinToString(" ")
        return time(phrase)
            ?: media(phrase)
            ?: volume(phrase)
            ?: alarm(words)
            ?: timer(words)
            ?: flashlight(words)
            ?: panel(words)
            ?: brightness(words)
            ?: navigate(original, words)
            ?: whatsApp(original, words)
            ?: calendar(words)
            ?: sms(original, words)
            ?: call(words)
    }

    /**
     * Дії для набраного тексту в пошуку: повна команда («таймер 10 хвилин», «wifi») або
     * початок простої команди («ліхт» → ліхтарик, «гучн» → гучніше).
     */
    fun suggest(text: String): List<VoiceAction> {
        val words = SearchNormalizer.words(SearchNormalizer.normalize(text))
        if (words.isEmpty()) return emptyList()
        parse(text, words)?.let { return listOf(it) }
        val prefix = words.joinToString(" ")
        if (prefix.length < MIN_PREFIX) return emptyList()
        return simplePhrases.filter { (phrase, _) -> phrase.startsWith(prefix) }
            .map { it.second }
            .distinct()
            .take(MAX_SUGGESTIONS)
    }

    private const val MIN_PREFIX = 3
    private const val MAX_SUGGESTIONS = 2

    // Прості команди без параметрів — для підказок за початком слова.
    private val simplePhrases: List<Pair<String, VoiceAction>> by lazy {
        mediaPhrases.flatMap { (command, phrases) -> phrases.map { it to VoiceAction.Media(command) } } +
            volumePhrases.flatMap { (change, phrases) -> phrases.map { it to VoiceAction.Volume(change) } } +
            panelPhrases.flatMap { (panel, phrases) -> phrases.map { it to VoiceAction.Panel(panel) } } +
            torchWords.map { it to VoiceAction.Flashlight(null) } +
            brighterWords.map { it to VoiceAction.BrightnessStep(up = true) } +
            darkerWords.map { it to VoiceAction.BrightnessStep(up = false) } +
            timePhrases.filter { ' ' in it }.map { it to VoiceAction.Time }
    }

    // ---- Час ----
    private val timePhrases = setOf(
        "котра година", "яка година", "скільки часу", "котра зараз година", "скільки зараз часу",
        "what time is it", "whats the time", "what is the time", "time",
        "ktora godzina", "ktora jest godzina", "ktora teraz godzina",
    )

    private fun time(phrase: String) = if (phrase in timePhrases) VoiceAction.Time else null

    // ---- Плеєр ----
    private val mediaPhrases = mapOf(
        MediaCommand.PAUSE to setOf(
            "пауза", "на паузу", "постав на паузу", "зупини", "зупини музику", "стоп", "стоп музика",
            "pause", "pause music", "stop music",
            "pauza", "zatrzymaj", "zatrzymaj muzyke", "stop muzyka",
        ),
        MediaCommand.PLAY to setOf(
            "грай", "відтвори", "продовж", "увімкни музику", "грай музику", "відтворення",
            "play", "resume", "play music", "resume music",
            "graj", "wznow", "odtwarzaj", "wlacz muzyke", "graj muzyke",
        ),
        MediaCommand.NEXT to setOf(
            "далі", "наступна", "наступний", "наступна пісня", "наступний трек", "пропусти",
            "next", "next song", "next track", "skip", "skip song",
            "dalej", "nastepna", "nastepny", "nastepna piosenka", "nastepny utwor", "pomin",
        ),
        // «попередній» сам по собі — це «попередня програма», тому лише з «пісня/трек».
        MediaCommand.PREVIOUS to setOf(
            "попередня пісня", "попередній трек", "попередня композиція", "назад пісня",
            "previous song", "previous track", "last song",
            "poprzednia piosenka", "poprzedni utwor",
        ),
    )

    private fun media(phrase: String): VoiceAction? =
        mediaPhrases.entries.firstOrNull { phrase in it.value }?.let { VoiceAction.Media(it.key) }

    // ---- Гучність ----
    private val volumePhrases = mapOf(
        VolumeChange.UP to setOf(
            "гучніше", "голосніше", "зроби гучніше", "додай гучність", "збільш гучність",
            "louder", "volume up", "turn it up", "turn up the volume",
            "glosniej", "podglosnij", "zrob glosniej",
        ),
        VolumeChange.DOWN to setOf(
            "тихіше", "зроби тихіше", "зменш гучність", "менше гучність",
            "quieter", "volume down", "turn it down", "turn down the volume",
            "ciszej", "scisz", "zrob ciszej",
        ),
        VolumeChange.MUTE to setOf(
            "без звуку", "вимкни звук", "mute", "mute sound", "wycisz", "wylacz dzwiek",
        ),
    )

    private fun volume(phrase: String): VoiceAction? =
        volumePhrases.entries.firstOrNull { phrase in it.value }?.let { VoiceAction.Volume(it.key) }

    // ---- Будильник ----
    private val alarmWords = setOf("будильник", "будильника", "будильнік", "alarm", "budzik", "budzika")
    private val eveningWords = setOf("вечора", "вечір", "вечером", "дня", "pm", "wieczorem", "wieczor", "popoludniu")
    private val morningWords = setOf("ранку", "зранку", "ранок", "am", "rano")

    private fun alarm(words: List<String>): VoiceAction? {
        if (words.none { it in alarmWords }) return null
        val numbers = NumberWords.numbers(words)
        if (numbers.isEmpty()) return null
        var hour: Int
        var minute: Int
        val first = numbers[0]
        if (numbers.size == 1 && first in 100..2359) {
            hour = first / 100
            minute = first % 100
        } else {
            hour = first
            minute = numbers.getOrNull(1)?.takeIf { it in 0..59 } ?: 0
        }
        if (words.any { it in eveningWords } && hour in 1..11) hour += 12
        if (words.any { it in morningWords } && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return VoiceAction.Alarm(hour, minute)
    }

    // ---- Таймер ----
    private val timerWords = setOf("таймер", "таймера", "timer", "minutnik", "minutnika")
    private val halfHour = setOf("півгодини", "пів години", "half an hour", "pol godziny", "polgodziny")

    private fun unitSeconds(word: String): Int? = when {
        word.startsWith("секунд") || word.startsWith("sec") || word.startsWith("sekund") -> 1
        word.startsWith("хвилин") || word.startsWith("min") -> 60
        word.startsWith("годин") || word.startsWith("hour") || word.startsWith("godzin") -> 3600
        else -> null
    }

    private fun timer(words: List<String>): VoiceAction? {
        if (words.none { it in timerWords }) return null
        val phrase = words.joinToString(" ")
        if (halfHour.any { phrase.contains(it) }) return VoiceAction.Timer(1800)
        var total = 0
        var pending: Int? = null
        var i = 0
        while (i < words.size) {
            val number = NumberWords.readNumber(words, i)
            if (number != null) {
                pending = number.first
                i = number.second
                continue
            }
            val unit = words[i].takeIf { it !in timerWords }?.let(::unitSeconds)
            if (unit != null) {
                total += (pending ?: 1) * unit
                pending = null
            }
            i++
        }
        // «таймер 10» — хвилини.
        if (pending != null) total += pending * 60
        return if (total > 0) VoiceAction.Timer(total) else null
    }

    // ---- Ліхтарик ----
    private val torchWords = setOf(
        "ліхтарик", "ліхтар", "ліхтарика", "фонарик", "фонарь", "flashlight", "torch", "latarka", "latarke", "latarki",
    )
    private val offWords = setOf("вимкни", "вимкнути", "виключи", "выключи", "off", "wylacz", "zgas")
    private val onWords = setOf("увімкни", "увімкнути", "включи", "ввімкни", "on", "wlacz", "zapal")

    private fun flashlight(words: List<String>): VoiceAction? {
        if (words.none { it in torchWords }) return null
        // Лише коротка команда: «увімкни ліхтарик», а не речення про ліхтарик.
        if (words.size > 3) return null
        val on = when {
            words.any { it in offWords } -> false
            words.any { it in onWords } -> true
            else -> null
        }
        return VoiceAction.Flashlight(on)
    }

    // ---- Панелі ----
    private val panelPhrases = mapOf(
        SystemPanel.WIFI to setOf("вайфай", "вай фай", "wifi", "wi fi", "вайфаю", "wlan"),
        SystemPanel.BLUETOOTH to setOf("блютуз", "блютус", "bluetooth", "блютуза"),
        SystemPanel.INTERNET to setOf(
            "інтернет", "мобільний інтернет", "мобільні дані", "internet", "mobile data", "dane mobilne", "mobilny internet",
        ),
    )
    private val panelFiller = onWords + offWords + setOf(
        "відкрий", "налаштування", "покажи", "open", "settings", "show", "otworz", "ustawienia", "pokaz", "turn",
    )

    private fun panel(words: List<String>): VoiceAction? {
        val core = words.filterNot { it in panelFiller }.joinToString(" ")
        return panelPhrases.entries.firstOrNull { core in it.value }?.let { VoiceAction.Panel(it.key) }
    }

    // ---- Яскравість ----
    private val brightnessWords = setOf("яскравість", "яскравості", "яскрависть", "brightness", "jasnosc", "jasnosci")
    private val brighterWords = setOf("яскравіше", "світліше", "brighter", "jasniej")
    private val darkerWords = setOf("темніше", "тьмяніше", "dimmer", "darker", "ciemniej")
    private val moreWords = setOf("більше", "збільш", "додай", "підніми", "increase", "up", "raise", "zwieksz", "wiecej")
    private val lessWords = setOf("менше", "зменш", "убав", "знизь", "decrease", "down", "lower", "zmniejsz", "mniej")
    private val maxWords = setOf("максимум", "максимальна", "максимальну", "max", "maximum", "maksimum", "maksymalna")
    private val minWords = setOf("мінімум", "мінімальна", "мінімальну", "min", "minimum", "minimalna")
    private val halfWords = setOf("половина", "половину", "half", "polowa", "polowe")

    private fun brightness(words: List<String>): VoiceAction? {
        if (words.size <= 2 && words.any { it in brighterWords }) return VoiceAction.BrightnessStep(up = true)
        if (words.size <= 2 && words.any { it in darkerWords }) return VoiceAction.BrightnessStep(up = false)
        if (words.none { it in brightnessWords }) return null
        NumberWords.numbers(words).firstOrNull()?.let { return VoiceAction.BrightnessLevel(it.coerceIn(0, 100)) }
        return when {
            words.any { it in maxWords } -> VoiceAction.BrightnessLevel(100)
            words.any { it in minWords } -> VoiceAction.BrightnessLevel(0)
            words.any { it in halfWords } -> VoiceAction.BrightnessLevel(50)
            words.any { it in moreWords } -> VoiceAction.BrightnessStep(up = true)
            words.any { it in lessWords } -> VoiceAction.BrightnessStep(up = false)
            else -> null
        }
    }

    // ---- WhatsApp ----
    private val whatsAppWords = setOf("whatsapp", "ватсап", "вотсап", "ватсапі", "ватсапу", "вацап", "вацапі", "wotsap", "watsap")

    private fun whatsApp(original: String, words: List<String>): VoiceAction? {
        // «whats app» розпізнавач часто пише двома словами.
        val joined = words.joinToString(" ").replace("whats app", "whatsapp").split(' ')
        val at = joined.indexOfFirst { it in whatsAppWords }
        if (at < 0) return null
        val rest = joined.drop(at + 1).dropWhile { it in callPrepositions }
        if (rest.isEmpty()) return null
        val marker = rest.indexOfFirst { it in bodyMarkers }
        val nameWords = if (marker > 0) rest.take(marker) else rest.take(1)
        val bodyCount = if (marker > 0) rest.size - marker - 1 else rest.size - 1
        val body = if (bodyCount > 0) originalTail(original, bodyCount) else ""
        return VoiceAction.WhatsApp(nameWords.joinToString(" "), body)
    }

    // ---- Календар ----
    private val calendarTriggers = setOf(
        "подія", "подію", "календар", "календаря", "event", "calendar", "wydarzenie", "kalendarz", "kalendarza", "kalendarzu",
    )
    private val calendarVerbs = setOf(
        "додай", "запиши", "створи", "постав", "нова", "нову", "add", "create", "new", "put", "dodaj", "zapisz", "utworz", "nowe",
    )
    private val calendarFiller = setOf("в", "у", "до", "на", "in", "to", "the", "a", "an", "w", "do")
    private val dayWords = mapOf(
        "сьогодні" to 0, "today" to 0, "dzis" to 0, "dzisiaj" to 0,
        "завтра" to 1, "tomorrow" to 1, "jutro" to 1,
        "післязавтра" to 2, "pojutrze" to 2,
    )
    private val openAppVerbs = setOf("відкрий", "запусти", "покажи", "open", "launch", "show", "otworz", "uruchom", "pokaz")
    private val timePrepositions = setOf("о", "об", "at", "o", "на", "na", "godzinie", "годині")

    private fun calendar(words: List<String>): VoiceAction? {
        val triggerAt = words.indexOfFirst { it in calendarTriggers }
        if (triggerAt < 0) return null
        // «календар» / «відкрий календар» — це програма «Календар», а не нова подія.
        if (words.size == 1 || words.first() in openAppVerbs) return null
        var rest = words.filterIndexed { i, w -> i != triggerAt && !(i < triggerAt && (w in calendarVerbs || w in calendarFiller)) }
        rest = rest.dropWhile { it in calendarVerbs || it in calendarFiller }
        var day = 0
        var dayFound = false
        // «day after tomorrow» — три слова.
        val phrase = rest.joinToString(" ")
        if (phrase.contains("day after tomorrow")) {
            day = 2
            dayFound = true
            rest = phrase.replace("day after tomorrow", " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        }
        rest.firstOrNull { it in dayWords }?.let {
            day = dayWords.getValue(it)
            dayFound = true
        }
        rest = rest.filterNot { it in dayWords }
        var hour: Int? = null
        var minute = 0
        val title = mutableListOf<String>()
        var i = 0
        while (i < rest.size) {
            val word = rest[i]
            val number = NumberWords.readNumber(rest, i)
            if (hour == null && number != null && number.first in 0..23) {
                hour = number.first
                i = number.second
                NumberWords.readNumber(rest, i)?.takeIf { it.first in 0..59 }?.let {
                    minute = it.first
                    i = it.second
                }
                if (title.lastOrNull() in timePrepositions) title.removeAt(title.lastIndex)
                continue
            }
            title += word
            i++
        }
        while (title.firstOrNull() in calendarFiller) title.removeAt(0)
        if (title.isEmpty() && hour == null && !dayFound) return null
        val text = title.joinToString(" ").replaceFirstChar { it.uppercase() }
        return VoiceAction.CalendarEvent(text, day, hour, minute)
    }

    // ---- Маршрут ----
    private val routePrefixes = listOf(
        "прокласти маршрут до", "проклади маршрут до", "маршрут до", "маршрут на", "як доїхати до", "як дійти до",
        "як доїхати на", "навігація до", "веди до", "поїхали до",
        "navigate to", "directions to", "route to", "take me to", "drive to",
        "nawiguj do", "trasa do", "jak dojechac do", "jak dojsc do", "prowadz do", "nawigacja do",
    ).map { it.split(' ') }

    private fun navigate(original: String, words: List<String>): VoiceAction? {
        val prefix = routePrefixes.firstOrNull { words.size > it.size && words.take(it.size) == it } ?: return null
        return VoiceAction.Navigate(originalTail(original, words.size - prefix.size))
    }

    // ---- Дзвінок ----
    private val callVerbs = setOf(
        "подзвони", "подзвонити", "зателефонуй", "зателефонувати", "набери", "дзвони", "дзвінок",
        "call", "phone", "dial", "ring",
        "zadzwon", "zadzwonic", "polacz", "dzwon",
    )
    private val callPrepositions = setOf("до", "to", "do", "z", "ze", "з", "із")

    private fun call(words: List<String>): VoiceAction? {
        if (words.first() !in callVerbs) return null
        val name = words.drop(1).dropWhile { it in callPrepositions }
        return if (name.isEmpty()) null else VoiceAction.Call(name.joinToString(" "))
    }

    // ---- SMS ----
    private val smsVerbs = setOf("напиши", "надішли", "відправ", "send", "write", "napisz", "wyslij")
    private val smsWords = setOf("смс", "sms", "эсэмэс", "есемес", "есемеску", "повідомлення", "message", "text", "wiadomosc", "esemesa")
    private val bodyMarkers = setOf("що", "текст", "текстом", "that", "saying", "ze", "tresc", "tekst")

    private fun sms(original: String, words: List<String>): VoiceAction? {
        var rest = words
        if (rest.first() in smsVerbs) rest = rest.drop(1)
        if (rest.isEmpty() || rest.first() !in smsWords) return null
        rest = rest.drop(1).dropWhile { it in callPrepositions }
        if (rest.isEmpty()) return null
        val marker = rest.indexOfFirst { it in bodyMarkers }
        // Без «що/текст»: ім'я — одне слово, решта — текст («смс Марії буду пізніше»).
        val nameWords = if (marker > 0) rest.take(marker) else rest.take(1)
        val bodyCount = if (marker > 0) rest.size - marker - 1 else rest.size - 1
        val body = if (bodyCount > 0) originalTail(original, bodyCount) else ""
        return VoiceAction.Sms(nameWords.joinToString(" "), body)
    }

    /** Останні [count] слів оригінальної фрази — з великими літерами й розділовими знаками. */
    internal fun originalTail(original: String, count: Int): String {
        val tokens = original.trim().split(Regex("\\s+"))
        for (start in tokens.indices) {
            val tail = tokens.drop(start).joinToString(" ")
            val n = SearchNormalizer.words(SearchNormalizer.normalize(tail)).size
            if (n == count) return tail
            if (n < count) break
        }
        val normalized = SearchNormalizer.words(SearchNormalizer.normalize(original))
        return normalized.takeLast(count).joinToString(" ")
    }
}

/** Числа цифрами й словами (0–59) трьома мовами: «7 30», «сім тридцять», «seven thirty». */
object NumberWords {
    private val units = mapOf(
        "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "дві" to 2, "три" to 3, "чотири" to 4,
        "пять" to 5, "шість" to 6, "сім" to 7, "вісім" to 8, "девять" to 9,
        "zero" to 0, "jeden" to 1, "jedna" to 1, "dwa" to 2, "dwie" to 2, "trzy" to 3, "cztery" to 4,
        "piec" to 5, "szesc" to 6, "siedem" to 7, "osiem" to 8, "dziewiec" to 9,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
    )
    private val teens = mapOf(
        "десять" to 10, "одинадцять" to 11, "дванадцять" to 12, "тринадцять" to 13, "чотирнадцять" to 14,
        "пятнадцять" to 15, "шістнадцять" to 16, "сімнадцять" to 17, "вісімнадцять" to 18, "девятнадцять" to 19,
        "dziesiec" to 10, "jedenascie" to 11, "dwanascie" to 12, "trzynascie" to 13, "czternascie" to 14,
        "pietnascie" to 15, "szesnascie" to 16, "siedemnascie" to 17, "osiemnascie" to 18, "dziewietnascie" to 19,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "двадцять" to 20, "тридцять" to 30, "сорок" to 40, "пятдесят" to 50,
        "dwadziescia" to 20, "trzydziesci" to 30, "czterdziesci" to 40, "piecdziesiat" to 50,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
    )

    /** Число, що починається з words[index], і індекс наступного слова; або null. */
    fun readNumber(words: List<String>, index: Int): Pair<Int, Int>? {
        val word = words.getOrNull(index) ?: return null
        word.toIntOrNull()?.let { return it to index + 1 }
        teens[word]?.let { return it to index + 1 }
        units[word]?.let { return it to index + 1 }
        val ten = tens[word] ?: return null
        val unit = words.getOrNull(index + 1)?.let { units[it] }
        return if (unit != null && unit > 0) (ten + unit) to index + 2 else ten to index + 1
    }

    fun numbers(words: List<String>): List<Int> {
        val result = mutableListOf<Int>()
        var i = 0
        while (i < words.size) {
            val number = readNumber(words, i)
            if (number != null) {
                result += number.first
                i = number.second
            } else {
                i++
            }
        }
        return result
    }
}

/** Форми імені для пошуку контакту: відмінки зводяться до початку імені. */
object ContactNames {
    fun variants(name: String): List<String> {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val result = linkedSetOf(words.joinToString(" "))
        for (drop in 1..2) {
            val stemmed = words.map { word -> if (word.length - drop >= MIN_STEM) word.dropLast(drop) else word }
            result += stemmed.joinToString(" ")
        }
        return result.toList()
    }

    private const val MIN_STEM = 3
}
