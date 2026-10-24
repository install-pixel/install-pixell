package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo

/**
 * Пошук за темою без ШІ: «квитки на маршрутку» → транспорт, «гроші» → банки.
 * Тема збігається з програмою за категорією Android або за відомим пакетом.
 * Працює лише під час пошуку, жодних фонових процесів.
 */
object Topics {
    // Значення ApplicationInfo.CATEGORY_* (стабільні константи Android).
    const val CATEGORY_GAME = 0
    const val CATEGORY_AUDIO = 1
    const val CATEGORY_VIDEO = 2
    const val CATEGORY_IMAGE = 3
    const val CATEGORY_SOCIAL = 4
    const val CATEGORY_NEWS = 5
    const val CATEGORY_MAPS = 6
    const val CATEGORY_PRODUCTIVITY = 7

    data class Topic(val keywords: List<String>, val categories: Set<Int> = emptySet(), val packages: Set<String> = emptySet())

    val all: List<Topic> = listOf(
        Topic(
            keywords = listOf(
                "квиток", "квитки", "маршрутка", "автобус", "таксі", "поїзд", "потяг", "транспорт", "метро", "трамвай", "проїзд",
                "bilet", "autobus", "pociąg", "taksówka", "tramwaj", "przejazd",
                "ticket", "bus", "train", "taxi", "transport", "tram", "ride",
            ),
            packages = setOf("ee.mtakso.client", "com.ubercab", "com.waze", "com.google.android.apps.maps"),
        ),
        Topic(
            keywords = listOf("карта", "карти", "мапа", "навігація", "маршрут", "дорога", "mapa", "nawigacja", "trasa", "map", "maps", "navigation", "route"),
            categories = setOf(CATEGORY_MAPS),
        ),
        Topic(
            keywords = listOf(
                "банк", "гроші", "картка", "оплата", "переказ", "рахунок", "гаманець",
                "bank", "pieniądze", "karta", "płatność", "przelew", "konto", "portfel",
                "money", "card", "payment", "transfer", "wallet", "account",
            ),
            packages = setOf(
                "com.ftband.mono", "ua.privatbank.ap24", "pl.mbank", "pl.pkobp.iko", "com.revolut.revolut",
                "com.paypal.android.p2pmobile", "com.transferwise.android", "com.google.android.apps.walletnfcrel",
            ),
        ),
        Topic(
            keywords = listOf(
                "покупки", "магазин", "купити", "замовити", "доставка", "посилка",
                "zakupy", "sklep", "kupić", "zamówić", "dostawa", "paczka",
                "shopping", "shop", "buy", "order", "delivery", "parcel",
            ),
            packages = setOf(
                "ua.com.rozetka.shop", "ua.slando", "pl.allegro", "com.einnovation.temu", "com.alibaba.aliexpresshd",
                "com.amazon.mShop.android.shopping", "com.zzkko",
            ),
        ),
        Topic(
            keywords = listOf(
                "чат", "месенджер", "переписка", "написати", "повідомлення",
                "czat", "komunikator", "napisać", "wiadomość",
                "chat", "messenger", "message", "text",
            ),
            packages = setOf(
                "org.telegram.messenger", "com.viber.voip", "com.whatsapp", "com.facebook.orca", "org.thoughtcrime.securesms",
                "com.discord", "com.google.android.apps.messaging",
            ),
            categories = setOf(CATEGORY_SOCIAL),
        ),
        Topic(keywords = listOf("музика", "пісня", "плеєр", "muzyka", "piosenka", "music", "song", "player"), categories = setOf(CATEGORY_AUDIO)),
        Topic(keywords = listOf("відео", "фільм", "серіал", "кіно", "wideo", "film", "serial", "kino", "video", "movie", "series"), categories = setOf(CATEGORY_VIDEO)),
        Topic(keywords = listOf("фото", "фотографії", "зображення", "zdjęcia", "obraz", "photo", "photos", "image", "picture"), categories = setOf(CATEGORY_IMAGE)),
        Topic(keywords = listOf("соцмережа", "соцмережі", "społeczność", "social"), categories = setOf(CATEGORY_SOCIAL)),
        Topic(keywords = listOf("новини", "wiadomości", "news"), categories = setOf(CATEGORY_NEWS)),
        Topic(keywords = listOf("гра", "ігри", "gra", "gry", "game", "games"), categories = setOf(CATEGORY_GAME)),
        Topic(
            keywords = listOf("робота", "документ", "документи", "таблиця", "офіс", "praca", "dokument", "tabela", "biuro", "work", "document", "office", "sheet"),
            categories = setOf(CATEGORY_PRODUCTIVITY),
        ),
    )

    /** Теми, до яких підходить запит. */
    fun matching(normalizedQuery: String): List<Topic> {
        val words = SearchNormalizer.words(normalizedQuery).filter { it.length >= MIN_WORD }
        if (words.isEmpty()) return emptyList()
        return all.filter { topic ->
            topic.keywords.any { keyword ->
                val k = SearchNormalizer.normalize(keyword)
                words.any { w -> k.startsWith(w) || w.startsWith(stem(k)) }
            }
        }
    }

    fun fits(topic: Topic, app: AppInfo): Boolean = app.packageName in topic.packages || app.category in topic.categories

    // Груба основа слова: «квиток» ≈ «квитки», «автобус» ≈ «автобуса».
    private fun stem(word: String): String = word.take(maxOf(MIN_WORD + 1, word.length - 2))

    private const val MIN_WORD = 3
}
