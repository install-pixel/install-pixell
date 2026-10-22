package com.offlinekeyboard.core.layout

/**
 * The keyboard's own emoji panel: a short, hand-picked set of the emoji people actually send, in pages of
 * 32, plus a page of text smileys that work everywhere (also in SMS and on a computer over Bluetooth).
 */
object EmojiSet {

    class Page(val title: String, val items: List<String>, val columns: Int = 8)

    private fun split(s: String) = s.trim().split(Regex("\\s+"))

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
