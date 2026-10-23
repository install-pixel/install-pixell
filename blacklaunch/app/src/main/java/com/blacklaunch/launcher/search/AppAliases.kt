package com.blacklaunch.launcher.search

import com.blacklaunch.launcher.apps.AppInfo

/**
 * Альтернативні назви програм. Три джерела:
 * - за пакетом (бренди: «ютуб» → YouTube);
 * - групи синонімів за назвою (системні програми різних виробників: «Камера» = «Camera»);
 * - власні псевдоніми користувача (за пакетом).
 */
class AppAliases(
    private val byPackage: Map<String, List<String>> = BuiltInAliases.byPackage,
    groups: List<List<String>> = BuiltInAliases.groups,
) {
    private val groupByName: Map<String, List<String>> = buildMap {
        groups.forEach { group -> group.forEach { put(SearchNormalizer.normalize(it), group) } }
    }

    fun aliasesFor(app: AppInfo, user: Map<String, Set<String>> = emptyMap()): List<String> =
        buildList {
            byPackage[app.packageName]?.let(::addAll)
            groupByName[SearchNormalizer.normalize(app.label)]?.let(::addAll)
            user[app.packageName]?.let(::addAll)
        }.distinct()
}

object BuiltInAliases {
    val byPackage: Map<String, List<String>> = mapOf(
        "com.google.android.youtube" to listOf("ютуб", "ютюб", "ютубчик"),
        "com.google.android.apps.youtube.music" to listOf("ютуб музика", "youtube music"),
        "org.telegram.messenger" to listOf("телеграм", "телега", "тг", "tg"),
        "com.viber.voip" to listOf("вайбер", "вібер"),
        "com.whatsapp" to listOf("вотсап", "ватсап", "вацап"),
        "com.instagram.android" to listOf("інстаграм", "інста", "insta"),
        "com.facebook.katana" to listOf("фейсбук", "фб", "fb"),
        "com.facebook.orca" to listOf("месенджер"),
        "com.zhiliaoapp.musically" to listOf("тікток", "тiкток"),
        "com.android.chrome" to listOf("хром", "браузер", "browser", "przeglądarka"),
        "com.google.android.gm" to listOf("джимейл", "гмейл", "пошта", "mail"),
        "com.google.android.apps.maps" to listOf("гугл карти", "карти", "мапи", "maps"),
        "com.android.vending" to listOf("плей маркет", "плей стор", "play market", "маркет", "sklep play"),
        "com.google.android.googlequicksearchbox" to listOf("гугл", "google"),
        "com.google.android.apps.translate" to listOf("перекладач", "translator", "tłumacz"),
        "com.google.android.apps.photos" to listOf("гугл фото", "фото"),
        "com.google.android.calendar" to listOf("календар"),
        "com.google.android.keep" to listOf("кіп", "нотатки"),
        "com.google.android.apps.docs" to listOf("гугл диск", "диск", "drive"),
        "com.spotify.music" to listOf("спотіфай", "спотифай"),
        "com.netflix.mediaclient" to listOf("нетфлікс"),
        "com.ftband.mono" to listOf("монобанк", "моно", "mono"),
        "ua.privatbank.ap24" to listOf("приват", "приват24", "privat", "privat24"),
        "ua.gov.diia.app" to listOf("дія", "diia", "diya"),
        "ee.mtakso.client" to listOf("болт", "таксі", "taxi", "taksówka"),
        "com.ubercab" to listOf("убер", "юбер", "таксі", "taxi", "taksówka"),
        "ua.com.rozetka.shop" to listOf("розетка"),
        "ua.slando" to listOf("олх", "olx"),
        "com.discord" to listOf("дискорд"),
        "com.twitter.android" to listOf("твітер", "твіттер", "x"),
        "com.microsoft.teams" to listOf("тімс", "teams"),
        "us.zoom.videomeetings" to listOf("зум"),
        "com.whatsapp.w4b" to listOf("вотсап бізнес", "ватсап бізнес"),
        "com.instagram.barcelona" to listOf("тредс", "threads"),
        "com.snapchat.android" to listOf("снепчат", "снапчат"),
        "com.pinterest" to listOf("пінтерест"),
        "com.reddit.frontpage" to listOf("редіт", "реддіт"),
        "tv.twitch.android.app" to listOf("твіч"),
        "org.thoughtcrime.securesms" to listOf("сигнал", "сігнал"),
        "com.skype.raider" to listOf("скайп"),
        "com.lemon.lvoverseas" to listOf("капкат", "кепкат"),
        "com.shazam.android" to listOf("шазам"),
        "com.soundcloud.android" to listOf("саундклауд"),
        "org.videolan.vlc" to listOf("влс", "плеєр", "player"),
        "com.openai.chatgpt" to listOf("чатгпт", "чат гпт", "gpt"),
        "com.duolingo" to listOf("дуолінго"),
        "com.waze" to listOf("вейз", "навігатор", "navigator"),
        "com.google.android.apps.tachyon" to listOf("гугл міт", "meet"),
        "com.google.android.apps.walletnfcrel" to listOf("гугл гаманець", "гугл пей", "google pay", "гаманець"),
        "com.google.android.apps.authenticator2" to listOf("аутентифікатор", "автентифікатор", "2fa"),
        "com.google.android.apps.docs.editors.docs" to listOf("гугл документи", "документи"),
        "com.google.android.apps.docs.editors.sheets" to listOf("гугл таблиці", "таблиці"),
        "com.microsoft.office.word" to listOf("ворд"),
        "com.microsoft.office.excel" to listOf("ексель"),
        "com.microsoft.office.outlook" to listOf("аутлук", "пошта"),
        "com.microsoft.skydrive" to listOf("ван драйв", "onedrive"),
        "com.alibaba.aliexpresshd" to listOf("аліекспрес", "аліекспресс", "алі"),
        "com.einnovation.temu" to listOf("тему"),
        "com.zzkko" to listOf("шейн"),
        "com.amazon.mShop.android.shopping" to listOf("амазон"),
        "com.paypal.android.p2pmobile" to listOf("пейпал"),
        "com.revolut.revolut" to listOf("револют"),
        "com.transferwise.android" to listOf("вайз"),
        "com.booking" to listOf("букінг"),
        "com.airbnb.android" to listOf("ейрбнб", "аірбнб"),
        // Популярні в Польщі.
        "pl.allegro" to listOf("алегро", "allegro"),
        "pl.mbank" to listOf("мбанк", "mbank"),
        "pl.pkobp.iko" to listOf("іко", "iko", "pko"),
    )

    val groups: List<List<String>> = listOf(
        listOf("Камера", "Camera", "Aparat"),
        listOf("Галерея", "Gallery", "Фото", "Photos", "Galeria", "Zdjęcia"),
        listOf("Налаштування", "Settings", "Настройки", "Ustawienia"),
        listOf("Телефон", "Phone", "Дзвінки", "Dialer", "Telefon"),
        listOf("Повідомлення", "Messages", "SMS", "СМС", "Wiadomości"),
        listOf("Контакти", "Contacts", "Kontakty"),
        listOf("Годинник", "Clock", "Будильник", "Alarm", "Zegar", "Budzik"),
        listOf("Калькулятор", "Calculator", "Kalkulator"),
        listOf("Календар", "Calendar", "Kalendarz"),
        listOf("Нотатки", "Notes", "Notatki"),
        listOf("Файли", "Files", "Файловий менеджер", "File Manager", "Мої файли", "My Files", "Pliki", "Moje pliki"),
        listOf("Погода", "Weather", "Pogoda"),
        listOf("Музика", "Music", "Muzyka"),
        listOf("Диктофон", "Recorder", "Voice Recorder", "Dyktafon"),
        listOf("Компас", "Compass", "Kompas"),
        listOf("Пошта", "Email", "Mail", "Poczta"),
        listOf("Браузер", "Browser", "Інтернет", "Internet", "Przeglądarka"),
        listOf("Карти", "Maps", "Мапи", "Mapy"),
    )
}
