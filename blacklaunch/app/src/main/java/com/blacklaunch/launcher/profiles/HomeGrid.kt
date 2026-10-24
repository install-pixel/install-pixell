package com.blacklaunch.launcher.profiles

/**
 * Вільна сітка головного екрана: 4 стовпці, рядки рахуються знизу (0 — нижній рядок),
 * тож розкладка не ламається на екранах різної висоти. Клітинка = рядЗнизу * 4 + стовпець.
 */
object HomeGrid {
    const val COLUMNS = 4

    fun cell(column: Int, rowFromBottom: Int) = rowFromBottom * COLUMNS + column

    fun column(cell: Int) = cell % COLUMNS

    fun rowFromBottom(cell: Int) = cell / COLUMNS

    /**
     * Збережені клітинки лишаються (без дублікатів); нові програми стають у перші вільні
     * клітинки знизу, зліва направо — як звичайний ряд унизу.
     */
    fun assign(keys: List<String>, saved: Map<String, Int>): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        val taken = mutableSetOf<Int>()
        keys.forEach { key ->
            val cell = saved[key]
            if (cell != null && cell >= 0 && taken.add(cell)) result[key] = cell
        }
        var next = 0
        keys.filter { it !in result }.forEach { key ->
            while (next in taken) next++
            result[key] = next
            taken += next
        }
        return keys.associateWith { result.getValue(it) }
    }

    /** Перетягнули програму в клітинку [target]: якщо там інша — міняються місцями. */
    fun move(cells: Map<String, Int>, key: String, target: Int): Map<String, Int> {
        val from = cells[key] ?: return cells
        if (target < 0 || target == from) return cells
        val occupant = cells.entries.firstOrNull { it.value == target }?.key
        return cells.toMutableMap().apply {
            this[key] = target
            if (occupant != null) this[occupant] = from
        }
    }
}

/** Де які програми стоять на головному екрані — окремо для кожного профілю. */
interface HomeLayoutStore {
    val layouts: kotlinx.coroutines.flow.StateFlow<Map<String, Map<String, Int>>>
    fun save(profileId: String, cells: Map<String, Int>)
}

class InMemoryHomeLayoutStore : HomeLayoutStore {
    private val _layouts = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    override val layouts: kotlinx.coroutines.flow.StateFlow<Map<String, Map<String, Int>>> = _layouts

    override fun save(profileId: String, cells: Map<String, Int>) {
        _layouts.value = _layouts.value + (profileId to cells)
    }

    internal fun restore(saved: Map<String, Map<String, Int>>) {
        _layouts.value = saved
    }

    companion object {
        fun encode(layouts: Map<String, Map<String, Int>>): String =
            layouts.flatMap { (profile, cells) -> cells.map { (key, cell) -> "$profile\t$key\t$cell" } }.joinToString("\n")

        fun decode(text: String?): Map<String, Map<String, Int>> {
            val result = linkedMapOf<String, MutableMap<String, Int>>()
            text.orEmpty().lineSequence().forEach { line ->
                val parts = line.split('\t')
                val cell = parts.getOrNull(2)?.toIntOrNull()
                if (parts.size == 3 && cell != null) result.getOrPut(parts[0]) { linkedMapOf() }[parts[1]] = cell
            }
            return result
        }
    }
}
