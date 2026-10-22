package com.offlinekeyboard.core.layout

/**
 * The keyboard's own emoji panel: a short, hand-picked set of the emoji people actually send, in pages of
 * 32, plus a page of text smileys that work everywhere (also in SMS and on a computer over Bluetooth).
 */
object EmojiSet {

    class Page(val title: String, val items: List<String>, val columns: Int = 8)

    private fun split(s: String) = s.trim().split(Regex("\\s+"))

    /**
     * The panel's pages: the user's most used emoji first ("🕘"), then their own emoji and smileys ("★",
     * from settings), then the built-in pages. Empty extra pages are left out.
     */
    fun pagesWith(frequent: List<String>, own: List<String>): List<Page> {
        val extra = ArrayList<Page>()
        if (frequent.isNotEmpty()) extra += Page(FREQUENT_TITLE, frequent.take(PAGE_SIZE), columnsFor(frequent))
        own.distinct().chunked(PAGE_SIZE).forEach { extra += Page(OWN_TITLE, it, columnsFor(it)) }
        return extra + pages
    }

    /** Splits the user's own emoji text (spaces or new lines between items). */
    fun parseOwn(text: String): List<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

    /** Text smileys such as ":)" or "(╯°□°)╯" are wider than emoji, so their pages have 4 columns. */
    private fun columnsFor(items: List<String>) =
        if (items.all { item -> item.none { it.code < 0x2000 } }) 8 else 4

    const val PAGE_SIZE = 32
    const val FREQUENT_TITLE = "🕘"
    const val OWN_TITLE = "★"

    val pages: List<Page> = listOf(
        Page(
            "😀",
            split(
                "😀 😃 😄 😁 😆 😅 😂 🤣 😊 🙂 😉 😍 🥰 😘 😋 😜 🤪 😎 🤩 🥳 😏 😌 🤔 🤨 " +
                    "😐 🙄 😬 😴 🤤 😷 🤒 🤯",
            ),
        ),
        Page(
            "😢",
            split(
                "😕 😟 🙁 😮 😯 😲 😳 🥺 😢 😭 😤 😠 😡 🤬 😱 😨 😰 😥 😓 🤗 🤭 🤫 🤐 😶 " +
                    "😇 🤠 🤓 🧐 😈 👻 💀 🤖",
            ),
        ),
        Page(
            "👍",
            split(
                "👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👋 🙏 👏 🙌 🤝 💪 👊 ✊ 🤛 🤜 👈 👉 👆 👇 ☝️ ✋ " +
                    "🖐️ 🤚 🫶 👀 🧠 🙋 🤷 🤦",
            ),
        ),
        Page(
            "❤️",
            split(
                "❤️ 🧡 💛 💚 💙 💜 🤎 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 💋 🌹 💐 ✨ ⭐ 🌟 " +
                    "🔥 💯 ✅ ❌ ❗ ❓ ⚠️ 🎉",
            ),
        ),
        Page(
            "🐶",
            split(
                "🐶 🐱 🐭 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🦋 🐝 🐞 🐢 🐟 🐬 🐳 " +
                    "🌸 🌻 🌲 🍀 ☀️ 🌧️ ❄️ 🌈",
            ),
        ),
        Page(
            "🍕",
            split(
                "☕ 🍵 🥤 🍺 🍷 🥂 🍎 🍌 🍓 🍒 🍉 🍇 🥕 🥔 🍞 🧀 🥚 🍳 🥗 🍕 🍔 🌭 🍟 🍝 " +
                    "🍜 🍣 🥟 🍰 🎂 🍫 🍦 🍪",
            ),
        ),
        Page(
            "🏠",
            split(
                "🏠 🏢 🏭 🏥 🏪 🚗 🚕 🚌 🚚 🚛 🚲 🚆 ✈️ ⛽ 🅿️ 📦 📋 📄 📌 📎 ✏️ 📅 ⏰ ⌛ " +
                    "💼 💻 📱 ☎️ 🔑 💡 🔧 💰",
            ),
        ),
        Page(
            ":)",
            listOf(
                ":)", ";)", ":D", ":(", ":P", "xD", "<3", "^_^",
                "¯\\_(ツ)_/¯", "(╯°□°)╯︵ ┻━┻", "ʕ•ᴥ•ʔ", "(◕‿◕)",
                "(ಥ﹏ಥ)", "(•̀ᴗ•́)و", "( ˘ ³˘)♥", "(ᵔᴥᵔ)",
                "(¬‿¬)", "(⌐■_■)", "┬─┬ノ( º _ ºノ)", "ヽ(•‿•)ノ",
            ),
            columns = 4,
        ),
    )
}
