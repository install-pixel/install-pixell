package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.SearchNormalizer

/**
 * Голосовий калькулятор: «двісті п'ятдесят помножити на чотири», «2 плюс 2», «скільки буде сто поділити на вісім»,
 * «what is twelve times three», «ile to dwa razy dwa» → вираз для [Calculator]. Будь-яке незнайоме слово — не вираз (null),
 * щоб звичайні запити на кшталт «2 кімнати» не ставали обчисленням.
 */
object SpokenMath {
    private val units = mapOf(
        "нуль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "одне" to 1, "два" to 2, "дві" to 2, "три" to 3, "чотири" to 4,
        "пять" to 5, "шість" to 6, "сім" to 7, "вісім" to 8, "девять" to 9,
        "zero" to 0, "jeden" to 1, "jedna" to 1, "dwa" to 2, "dwie" to 2, "trzy" to 3, "cztery" to 4,
        "piec" to 5, "szesc" to 6, "siedem" to 7, "osiem" to 8, "dziewiec" to 9,
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9,
        "десять" to 10, "одинадцять" to 11, "дванадцять" to 12, "тринадцять" to 13, "чотирнадцять" to 14,
        "пятнадцять" to 15, "шістнадцять" to 16, "сімнадцять" to 17, "вісімнадцять" to 18, "девятнадцять" to 19,
        "dziesiec" to 10, "jedenascie" to 11, "dwanascie" to 12, "trzynascie" to 13, "czternascie" to 14,
        "pietnascie" to 15, "szesnascie" to 16, "siedemnascie" to 17, "osiemnascie" to 18, "dziewietnascie" to 19,
        "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf(
        "двадцять" to 20, "тридцять" to 30, "сорок" to 40, "пятдесят" to 50, "шістдесят" to 60, "сімдесят" to 70,
        "вісімдесят" to 80, "девяносто" to 90,
        "dwadziescia" to 20, "trzydziesci" to 30, "czterdziesci" to 40, "piecdziesiat" to 50, "szescdziesiat" to 60,
        "siedemdziesiat" to 70, "osiemdziesiat" to 80, "dziewiecdziesiat" to 90,
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val hundreds = mapOf(
        "сто" to 100, "двісті" to 200, "триста" to 300, "чотириста" to 400, "пятсот" to 500, "шістсот" to 600,
        "сімсот" to 700, "вісімсот" to 800, "девятсот" to 900,
        "sto" to 100, "dwiescie" to 200, "trzysta" to 300, "czterysta" to 400, "piecset" to 500, "szescset" to 600,
        "siedemset" to 700, "osiemset" to 800, "dziewiecset" to 900,
    )
    private val hundredWords = setOf("hundred", "hundreds")
    private val thousandWords = setOf("тисяча", "тисячі", "тисяч", "thousand", "thousands", "tysiac", "tysiace", "tysiecy")
    private val millionWords = setOf("мільйон", "мільйони", "мільйонів", "million", "millions", "milion", "miliony", "milionow")
    private val pointWords = setOf("кома", "цілих", "point", "przecinek")

    private val operators = mapOf(
        "+" to setOf("плюс", "додати", "plus", "add", "dodac", "dodaj"),
        "-" to setOf("мінус", "відняти", "minus", "subtract", "odjac", "odejmij"),
        "*" to setOf("помножити", "помножене", "помнож", "множити", "разів", "times", "multiplied", "multiply", "razy", "pomnozyc", "pomnoz", "х", "x"),
        "/" to setOf("поділити", "поділене", "поділи", "ділити", "divided", "divide", "over", "podzielic", "podziel", "dzielone"),
        "^" to setOf("степені", "степінь", "power", "potegi", "potega"),
    )
    private val percentWords = setOf("відсоток", "відсотки", "відсотків", "percent", "procent", "procenty", "procentow")
    // Слова-зв'язки після дії («помножити НА», «divided BY», «podzielić PRZEZ», «В степені»), і питання на початку.
    private val linkWords = setOf("на", "by", "przez", "в", "у", "to", "the", "of", "do")
    private val fillerWords = setOf(
        "скільки", "буде", "це", "дорівнює", "рівно", "порахуй", "обчисли",
        "what", "whats", "is", "how", "much", "calculate", "equals",
        "ile", "jest", "to", "rowna", "sie", "policz", "oblicz",
    )

    fun toExpression(text: String): String? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null
        val out = StringBuilder()
        var operatorsFound = 0
        var numbersFound = 0
        var i = 0
        var lastWasOperator = true
        while (i < tokens.size) {
            val token = tokens[i]
            val symbol = token.singleOrNull()?.takeIf { it in "+-*/×÷^%()" }
            val number = readNumber(tokens, i)
            val op = operators.entries.firstOrNull { token in it.value }?.key
            when {
                symbol != null -> {
                    out.append(symbol)
                    if (symbol !in "()%") operatorsFound++
                    lastWasOperator = symbol !in ")%"
                    i++
                }
                number != null -> {
                    out.append(number.first)
                    numbersFound++
                    lastWasOperator = false
                    i = number.second
                }
                token in percentWords && !lastWasOperator -> {
                    out.append('%')
                    operatorsFound++
                    i++
                }
                op != null -> {
                    out.append(op)
                    operatorsFound++
                    lastWasOperator = true
                    i++
                    // «помножити на», «divided by», «to the power of»…
                    while (i < tokens.size && tokens[i] in linkWords) i++
                }
                // Зв'язка перед дією («два В степені десять») чи після неї.
                token in fillerWords || (token in linkWords && (lastWasOperator || isOperator(tokens.getOrNull(i + 1)))) -> i++
                else -> return null
            }
        }
        if (operatorsFound == 0 || numbersFound == 0) return null
        return out.toString()
    }

    private fun isOperator(token: String?): Boolean = token != null && operators.values.any { token in it }

    /** Число словами чи цифрами (з десятковою частиною: «два кома п'ять», «2,5») — текст і індекс наступного слова. */
    private fun readNumber(tokens: List<String>, start: Int): Pair<String, Int>? {
        val first = tokens.getOrNull(start) ?: return null
        if (first.first().isDigit()) {
            if (!first.all { it.isDigit() || it == '.' || it == ',' }) return null
            return first.replace(',', '.') to start + 1
        }
        var total = 0L
        var current = 0L
        var i = start
        var any = false
        while (i < tokens.size) {
            val w = tokens[i]
            when {
                w in units -> { current += units.getValue(w); any = true }
                w in tens -> { current += tens.getValue(w); any = true }
                w in hundreds -> { current += hundreds.getValue(w); any = true }
                w in hundredWords && any -> current = (if (current == 0L) 1 else current) * 100
                w in thousandWords -> { total += (if (current == 0L) 1 else current) * 1_000; current = 0; any = true }
                w in millionWords -> { total += (if (current == 0L) 1 else current) * 1_000_000; current = 0; any = true }
                w == "and" && any -> Unit
                else -> break
            }
            i++
        }
        if (!any) return null
        var text = (total + current).toString()
        // «два кома п'ять» → 2.5 (дробова частина — цифрами чи одним числом словами).
        if (i < tokens.size && tokens[i] in pointWords) {
            val fraction = readNumber(tokens, i + 1)
            if (fraction != null && '.' !in fraction.first) {
                text += "." + fraction.first
                i = fraction.second
            }
        }
        return text to i
    }

    /** Слова й знаки окремо: «2+2» → [2, +, 2]; «п'ять» → «пять» (як у пошуку). */
    private fun tokenize(text: String): List<String> {
        val spaced = text.lowercase().replace(Regex("([+\\-*/×÷^%()=])"), " $1 ").replace("=", " ")
        return spaced.split(Regex("\\s+")).filter { it.isNotBlank() }.map { raw ->
            if (raw.first().isDigit() || raw.length == 1) raw.trim('?', '!', '.') else SearchNormalizer.normalize(raw)
        }.filter { it.isNotEmpty() }
    }
}
