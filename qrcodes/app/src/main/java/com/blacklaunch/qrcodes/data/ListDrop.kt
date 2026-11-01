package com.blacklaunch.qrcodes.data

/**
 * Куди лягає рядок, який тягнуть у списку збережених: над іншим рядком, під ним чи «в нього» (на код — нова папка з
 * обох, на папку — у неї). Середина рядка — «в нього», верхня й нижня чверті — між рядками.
 */
object ListDrop {
    enum class Kind { Before, Into, After }

    /** [canInto] — чи можна класти «в» цей рядок (у відкритій папці — ні, лише міняти порядок). */
    fun decide(fingerY: Float, top: Float, height: Float, canInto: Boolean): Kind {
        val t = ((fingerY - top) / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return when {
            canInto && t in INTO_FROM..INTO_TO -> Kind.Into
            t < 0.5f -> Kind.Before
            else -> Kind.After
        }
    }

    /** Новий порядок ключів: [dragged] переїжджає перед [target] (або після, якщо [after]). */
    fun move(keys: List<String>, dragged: String, target: String, after: Boolean): List<String> {
        if (dragged == target || dragged !in keys || target !in keys) return keys
        val rest = keys.filter { it != dragged }.toMutableList()
        val at = rest.indexOf(target) + if (after) 1 else 0
        rest.add(at, dragged)
        return rest
    }

    /** Ключ рядка: «f<id>» — папка, «c<id>» — код. */
    fun folderKey(id: Long) = "f$id"
    fun codeKey(id: Long) = "c$id"

    private const val INTO_FROM = 0.28f
    private const val INTO_TO = 0.72f
}
