package com.blacklaunch.supps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.blacklaunch.supps.R
import com.blacklaunch.supps.data.AltGroup
import com.blacklaunch.supps.data.DayStatus
import com.blacklaunch.supps.data.DayType
import com.blacklaunch.supps.data.Form
import com.blacklaunch.supps.data.Phase
import com.blacklaunch.supps.data.Plan
import com.blacklaunch.supps.data.Slot
import com.blacklaunch.supps.data.Supplement
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/*
 * Підписи й числа для інтерфейсу. Тексти — лише з ресурсів (uk / pl / en).
 */

@Composable
@ReadOnlyComposable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** Число без зайвих нулів: 2 → «2», 1.5 → «1,5» (за мовою). */
fun num(value: Double, locale: Locale = Locale.getDefault()): String {
    if (abs(value - value.roundToLong()) < 1e-6) return value.roundToLong().toString()
    return String.format(locale, "%.2f", value).trimEnd('0').trimEnd(',', '.')
}

fun money(value: Double, currency: String, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.2f", value) + if (currency.isBlank()) "" else " $currency"

fun dayOf(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

/** «3 січ.» */
fun shortDate(epochDay: Long, locale: Locale): String = dayOf(epochDay).format(DateTimeFormatter.ofPattern("d MMM", locale))

/** «середа, 25 листопада» */
fun longDate(epochDay: Long, locale: Locale): String = dayOf(epochDay).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))

/** «листопад 2026» */
fun monthTitle(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }

/** Множина з ресурсів (дні, капсули): правильні форми для української й польської. */
@Composable
fun plural(id: Int, count: Int): String = LocalContext.current.resources.getQuantityString(id, count, count)

@Composable
fun formName(f: Form): String = stringResource(
    when (f) {
        Form.Capsule -> R.string.form_capsule
        Form.Tablet -> R.string.form_tablet
        Form.Powder -> R.string.form_powder
        Form.Drops -> R.string.form_drops
        Form.Other -> R.string.form_other
    },
)

/** Коротка одиниця: «капс.», «табл.», «г». */
@Composable
fun unitShort(f: Form): String = stringResource(
    when (f) {
        Form.Capsule -> R.string.unit_capsule
        Form.Tablet -> R.string.unit_tablet
        Form.Powder -> R.string.unit_gram
        Form.Drops -> R.string.unit_drop
        Form.Other -> R.string.unit_piece
    },
)

@Composable
fun slotName(s: Slot): String = stringResource(
    when (s) {
        Slot.Wake -> R.string.slot_wake
        Slot.Day -> R.string.slot_day
        Slot.Dinner -> R.string.slot_dinner
        Slot.Bed -> R.string.slot_bed
    },
)

@Composable
fun planName(p: Plan): String = stringResource(
    when (p) {
        Plan.Daily -> R.string.plan_daily
        Plan.Cycle -> R.string.plan_cycle
        Plan.Group -> R.string.plan_group
        Plan.AsNeeded -> R.string.plan_as_needed
    },
)

@Composable
fun dayTypeName(t: DayType): String = stringResource(
    when (t) {
        DayType.Shift1 -> R.string.day_shift1
        DayType.Shift2 -> R.string.day_shift2
        DayType.Off -> R.string.day_off
        DayType.Other -> R.string.day_other
    },
)

/** «2 капс. · 1000 мг» */
@Composable
fun doseText(s: Supplement): String {
    val locale = appLocale()
    val unit = unitShort(s.formE)
    val base = "${num(s.unitsPerDose, locale)} $unit"
    val mg = s.mgPerUnit ?: return base
    return base + " · " + stringResource(R.string.mg_value, num(mg * s.unitsPerDose, locale))
}

/** Схема коротко: «Щодня», «Цикл 84 / 28 дн.», «Чергування · Адаптогени», «За потреби». */
@Composable
fun planText(s: Supplement, groups: List<AltGroup>): String = when (s.planE) {
    Plan.Daily -> stringResource(R.string.plan_daily)
    Plan.AsNeeded -> stringResource(R.string.plan_as_needed)
    Plan.Cycle -> stringResource(R.string.plan_cycle_short, s.onDays, s.offDays)
    Plan.Group -> stringResource(R.string.plan_group_short, groups.firstOrNull { it.id == s.groupId }?.name.orEmpty())
}

/**
 * Стан сьогодні одним рядком: «день 23 з 84 · далі перерва з 5 січ.», «перерва до 12 січ.», «далі — Ашваганда з 7 січ.».
 * Порожньо — нема що сказати (щоденна).
 */
@Composable
fun statusText(st: DayStatus, all: List<Supplement>): String {
    val locale = appLocale()
    return when (st.phase) {
        Phase.Active -> {
            if (st.length <= 0) return ""
            val day = stringResource(R.string.status_day_of, st.dayNo, st.length)
            val change = st.changeOn
            val next = all.firstOrNull { it.id == st.nextId }
            when {
                change == null -> day
                next != null -> day + " · " + stringResource(R.string.status_next_item, next.name, shortDate(change, locale))
                st.nextIsBreak -> day + " · " + stringResource(R.string.status_next_break, shortDate(change, locale))
                else -> day
            }
        }
        Phase.Off -> st.changeOn?.let { stringResource(R.string.status_off_until, shortDate(it, locale)) } ?: stringResource(R.string.status_off)
        Phase.NotStarted -> st.changeOn?.let { stringResource(R.string.status_starts, shortDate(it, locale)) }.orEmpty()
        Phase.AsNeeded -> stringResource(R.string.plan_as_needed)
    }
}
