package com.blacklaunch.launcher.global

import com.blacklaunch.launcher.search.SearchNormalizer
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Конвертер одиниць у пошуку, завжди ввімкнений і офлайн: «10 км в милі», «30 c в f»,
 * «5 kg to lb», «100 км/год в mph». Довжина, вага, об'єм, швидкість, температура. Без валют.
 */
object UnitConverter {
    private enum class Kind { LENGTH, MASS, VOLUME, SPEED, TEMPERATURE }

    /** [factor] — скільки базових одиниць в одній (м, кг, л, м/с); температура — окремо. */
    private class Unit(val kind: Kind, val factor: Double, val latin: String, val cyrillic: String, vararg val names: String)

    private val units = listOf(
        Unit(Kind.LENGTH, 0.001, "mm", "мм", "мм", "mm", "міліметр*", "millimet*", "milimetr*"),
        Unit(Kind.LENGTH, 0.01, "cm", "см", "см", "cm", "сантиметр*", "centimet*", "centymetr*"),
        Unit(Kind.LENGTH, 1.0, "m", "м", "м", "m", "метр", "метри", "метрів", "метра", "meter*", "metre*", "metr", "metry", "metrow"),
        Unit(Kind.LENGTH, 1000.0, "km", "км", "км", "km", "кілометр*", "kilomet*", "kilometr*"),
        Unit(Kind.LENGTH, 0.0254, "in", "дюйм", "дюйм*", "inch*", "in", "cal", "cali"),
        Unit(Kind.LENGTH, 0.3048, "ft", "фут", "фут*", "ft", "foot", "feet", "stop*"),
        Unit(Kind.LENGTH, 0.9144, "yd", "ярд", "ярд*", "yd", "yard*", "jard*"),
        Unit(Kind.LENGTH, 1609.344, "mi", "миль", "мил*", "mi", "mile*", "mil", "mila", "mile"),
        Unit(Kind.MASS, 0.000001, "mg", "мг", "мг", "mg", "міліграм*", "milligram*", "miligram*"),
        Unit(Kind.MASS, 0.001, "g", "г", "г", "g", "грам*", "gram*"),
        Unit(Kind.MASS, 1.0, "kg", "кг", "кг", "kg", "кілограм*", "kilogram*", "kilo"),
        Unit(Kind.MASS, 1000.0, "t", "т", "т", "t", "тонн*", "тона", "ton*"),
        Unit(Kind.MASS, 0.028349523125, "oz", "унц", "унці*", "унц*", "oz", "ounce*", "uncj*", "uncja"),
        Unit(Kind.MASS, 0.45359237, "lb", "фунт", "фунт*", "lb", "lbs", "pound*", "funt*"),
        Unit(Kind.VOLUME, 0.001, "ml", "мл", "мл", "ml", "мілілітр*", "millilit*", "mililitr*"),
        Unit(Kind.VOLUME, 1.0, "l", "л", "л", "l", "літр*", "liter*", "litre*", "litr*"),
        Unit(Kind.VOLUME, 3.785411784, "gal", "гал", "галон*", "gal", "gallon*", "galon*"),
        Unit(Kind.VOLUME, 0.0295735295625, "fl oz", "рід унц", "fl oz", "fluid ounce*"),
        Unit(Kind.VOLUME, 0.2365882365, "cup", "чашк", "чашк*", "cup*", "szklank*"),
        Unit(Kind.SPEED, 1.0, "m/s", "м/с", "м с", "m s", "mps"),
        Unit(Kind.SPEED, 1 / 3.6, "km/h", "км/год", "км год", "км г", "km h", "kmh", "kph", "km godz", "kmh"),
        Unit(Kind.SPEED, 0.44704, "mph", "миль/год", "mph", "mi h", "миль год", "мил год"),
        Unit(Kind.SPEED, 0.514444, "kn", "вуз", "вузол", "вузл*", "knot*", "kn", "wezel", "wezl*"),
        Unit(Kind.TEMPERATURE, 0.0, "°C", "°C", "c", "с", "цельсі*", "celsius*", "celsjusz*", "градус*"),
        Unit(Kind.TEMPERATURE, 1.0, "°F", "°F", "f", "ф", "фаренгейт*", "fahrenheit*", "farenheit*"),
        Unit(Kind.TEMPERATURE, 2.0, "K", "K", "k", "к", "кельвін*", "kelvin*", "kelwin*"),
    )

    private val pattern = Regex("""^\s*(-?\d+(?:[.,]\d+)?)\s*(.+?)\s+(?:в|у|до|to|in|na|w|->|=)\s+(.+?)\s*$""")

    /** «10 км в милі» → «6.21371 миль»; null — не конвертація. */
    fun convert(input: String): String? {
        val match = pattern.matchEntire(input.lowercase()) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = find(match.groupValues[2]) ?: return null
        val toText = match.groupValues[3]
        val to = find(toText) ?: return null
        if (from.kind != to.kind || from === to) return null
        val value = if (from.kind == Kind.TEMPERATURE) temperature(amount, from.factor, to.factor) else amount * from.factor / to.factor
        val symbol = if (toText.any { it in 'а'..'я' || it in "іїєґ" }) to.cyrillic else to.latin
        return "${format(value)} $symbol"
    }

    private fun find(text: String): Unit? {
        val name = SearchNormalizer.normalize(text)
        if (name.isEmpty()) return null
        return units.firstOrNull { unit ->
            unit.names.any { alias ->
                if (alias.endsWith('*')) name.startsWith(alias.dropLast(1)) else name == alias
            }
        }
    }

    // Температура: 0 — Цельсій, 1 — Фаренгейт, 2 — Кельвін.
    private fun temperature(value: Double, from: Double, to: Double): Double {
        val celsius = when (from) {
            1.0 -> (value - 32) * 5 / 9
            2.0 -> value - 273.15
            else -> value
        }
        return when (to) {
            1.0 -> celsius * 9 / 5 + 32
            2.0 -> celsius + 273.15
            else -> celsius
        }
    }

    private fun format(value: Double): String {
        val rounded = BigDecimal(value).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros()
        return if (rounded.scale() < 0) rounded.setScale(0).toPlainString() else rounded.toPlainString()
    }
}
