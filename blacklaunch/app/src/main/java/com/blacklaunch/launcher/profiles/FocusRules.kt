package com.blacklaunch.launcher.profiles

/**
 * Фокус-таймер (етап 16): режим [profileId] до [endsAt]; потім — назад у [previousId].
 * [attempts] — скільки разів за цей час пробували вийти чи відкрити налаштування (лише на телефоні).
 */
data class FocusSession(
    val profileId: String,
    val previousId: String?,
    val startedAt: Long,
    val endsAt: Long,
    val strict: Boolean,
    val attempts: Int = 0,
) {
    fun encode(): String = listOf(profileId, previousId.orEmpty(), startedAt, endsAt, if (strict) 1 else 0, attempts).joinToString(SEP)

    companion object {
        private const val SEP = "\u001F"

        fun decode(text: String?): FocusSession? {
            val parts = text?.split(SEP)?.takeIf { it.size == 6 } ?: return null
            return FocusSession(
                profileId = parts[0].ifEmpty { return null },
                previousId = parts[1].ifEmpty { null },
                startedAt = parts[2].toLongOrNull() ?: return null,
                endsAt = parts[3].toLongOrNull() ?: return null,
                strict = parts[4] == "1",
                attempts = parts[5].toIntOrNull() ?: 0,
            )
        }
    }
}

/** Підсумок після таймера: скільки тривало, скільки планували, скільки разів пробували вийти. */
data class FocusSummary(val profileName: String, val minutes: Int, val plannedMinutes: Int, val attempts: Int, val early: Boolean)

/** Що робити з перемиканням режиму чи відкриттям налаштувань зараз. */
sealed interface FocusGate {
    data object Allowed : FocusGate

    /** Йде фокус-таймер: нічого не перемикається до кінця (або дострокового завершення). */
    data class Locked(val remainingMs: Long) : FocusGate

    /** «Важкий вихід» режиму: спершу відлік [seconds]. */
    data class Delay(val seconds: Int) : FocusGate
}

/** Чисті правила «Зосередження» — перевіряються тестами. */
object FocusRules {
    /** «Важкий вихід»: 0 — вимкнено. */
    val EXIT_DELAYS = listOf(0, 10, 30, 60)
    val DURATIONS = listOf(15, 30, 45, 60, 90, 120)
    const val DEFAULT_MINUTES = 30

    /** Достроково — довге утримання, потім цей відлік. */
    const val EARLY_EXIT_S = 60
    const val HOLD_MS = 3_000L

    fun running(session: FocusSession?, now: Long): Boolean = session != null && now < session.endsAt

    /** Перемикання, яке робить сам користувач (екран, плитка, голос, пошук, власна команда). */
    fun switchGate(activeId: String?, targetId: String, session: FocusSession?, exitDelay: Int, now: Long): FocusGate = when {
        targetId == activeId -> FocusGate.Allowed
        running(session, now) -> if (targetId == session!!.profileId) FocusGate.Allowed else FocusGate.Locked(session.endsAt - now)
        exitDelay > 0 -> FocusGate.Delay(exitDelay)
        else -> FocusGate.Allowed
    }

    /** Налаштування Black Glass: інакше «Важкий вихід» вимикався б за секунду. */
    fun settingsGate(session: FocusSession?, exitDelay: Int, now: Long): FocusGate = when {
        running(session, now) -> FocusGate.Locked(session!!.endsAt - now)
        exitDelay > 0 -> FocusGate.Delay(exitDelay)
        else -> FocusGate.Allowed
    }

    /**
     * Автоматизації (подія, час): ви їх налаштували наперед, тож «Важкий вихід» їх не зупиняє,
     * а фокус-таймер — так: він обіцянка до кінця.
     */
    fun automationMaySwitch(activeId: String?, targetId: String, session: FocusSession?, now: Long): Boolean =
        targetId == activeId || !running(session, now) || targetId == session!!.profileId

    /** Хвилини, що лишилися, — з округленням угору («ще 1 хв» до самого кінця). */
    fun minutesLeft(remainingMs: Long): Int = ((remainingMs.coerceAtLeast(0) + 59_999) / 60_000).toInt()

    /** «23:41» або «1:05:00». */
    fun clock(remainingMs: Long): String {
        val total = (remainingMs.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    fun summary(session: FocusSession, profileName: String, endedAt: Long): FocusSummary {
        val early = endedAt < session.endsAt
        val minutes = ((minOf(endedAt, session.endsAt) - session.startedAt).coerceAtLeast(0) / 60_000).toInt()
        val planned = ((session.endsAt - session.startedAt) / 60_000).toInt()
        return FocusSummary(profileName, minutes, planned, session.attempts, early)
    }
}
