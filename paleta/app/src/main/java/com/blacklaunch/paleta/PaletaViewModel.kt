package com.blacklaunch.paleta

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blacklaunch.paleta.data.CardDatabase
import com.blacklaunch.paleta.data.ProductCard
import com.blacklaunch.paleta.pack.ADR_HEIGHT_MM
import com.blacklaunch.paleta.pack.BoxSize
import com.blacklaunch.paleta.pack.Layout
import com.blacklaunch.paleta.pack.LayoutCheck
import com.blacklaunch.paleta.pack.Limit
import com.blacklaunch.paleta.pack.Num
import com.blacklaunch.paleta.pack.Orient
import com.blacklaunch.paleta.pack.Packer
import com.blacklaunch.paleta.pack.Placed
import com.blacklaunch.paleta.pack.Stack
import com.blacklaunch.paleta.pack.Stacking
import com.blacklaunch.paleta.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Що сказати користувачу сповіщенням угорі. */
sealed interface UiEvent {
    data object Saved : UiEvent
    data object LayoutReset : UiEvent
    data object NoRoom : UiEvent
    data class Variant(val index: Int, val total: Int) : UiEvent
    data object OnlyVariant : UiEvent
    data object Duplicated : UiEvent
    data object OnlyOrientation : UiEvent
    data class Orientation(val index: Int, val l: Int, val w: Int, val h: Int) : UiEvent
    data class Deleted(val card: ProductCard) : UiEvent
}

/** Позначки з бланку, що рахуються самі. */
data class Flags(
    /** Штука більша за 50 × 30 см. */
    val oversize: Boolean,
    /** Якийсь розмір понад 1,2 м. */
    val awkward: Boolean,
    /** Картон понад 35 кг — піднімати вдвох. */
    val twoMen: Boolean,
    /** Картон понад 25 кг — міряти рулеткою, важити на картонній вазі. */
    val heavy: Boolean,
)

/** Усе, що треба показати картку-бланк (і для нового розрахунку, і для збереженого). */
data class CardView(
    val dateMs: Long,
    val sku: String,
    val box: Triple<Int, Int, Int>,
    val boxG: Long,
    val unitsPerBox: Int,
    val unit: Triple<Int, Int, Int>?,
    val unitG: Long?,
    val stack: Stack?,
    val palletG: Long,
    val adr: Boolean,
    val flags: Flags,
)

fun flagsOf(box: Triple<Int, Int, Int>, boxG: Long, unit: Triple<Int, Int, Int>?): Flags {
    // Габарит і «незручний» — за штукою, якщо її заповнили, інакше за коробкою.
    val item = unit ?: box
    val sides = listOf(item.first, item.second, item.third).sortedDescending()
    return Flags(
        // Габарит — коли штука більша за 50 × 30 см за обома сторонами (довша > 50 і друга > 30).
        oversize = sides[0] > 500 && sides[1] > 300,
        awkward = sides[0] > 1200,
        twoMen = boxG > 35_000,
        heavy = boxG > 25_000,
    )
}

fun ProductCard.view(): CardView {
    // Як у бланку: довжина — найдовший бік основи.
    val box = Triple(maxOf(boxL, boxW), minOf(boxL, boxW), boxH)
    val unit = if (unitL != null && unitW != null && unitH != null) Triple(unitL, unitW, unitH) else null
    return CardView(
        dateMs = createdAt,
        sku = sku,
        box = box,
        boxG = boxG,
        unitsPerBox = unitsPerBox,
        unit = unit,
        unitG = unitG,
        stack = Stack(perLayer, layers, boxes, partial, heightMm, weightG, if (limitedByWeight) Limit.Weight else Limit.Height, layersByHeight),
        palletG = palletG,
        adr = adr,
        flags = flagsOf(box, boxG, unit),
    )
}

/** Розмір коробки в плані для розкладки. */
val ProductCard.plan: BoxSize get() = BoxSize(boxL, boxW)

@OptIn(FlowPreview::class)
class PaletaViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = CardDatabase.get(app).cards()
    private val settings = AppSettings(app)

    /** Історія (null — ще завантажується). */
    val cards: StateFlow<List<ProductCard>?> = dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events

    // --- Поля вводу (текст як вписали) ---
    var sku by mutableStateOf("")
    var boxL by mutableStateOf("")
    var boxW by mutableStateOf("")
    var boxH by mutableStateOf("")
    var boxKg by mutableStateOf("")
    var units by mutableStateOf("")
    var unitL by mutableStateOf("")
    var unitW by mutableStateOf("")
    var unitH by mutableStateOf("")
    var unitKg by mutableStateOf("")
    var adr by mutableStateOf(false)
    /** Стрілки ↑↑ на коробці — не перевертати (висота лише як вписали). */
    var arrows by mutableStateOf(false)

    // --- Обмеження: запам'ятовуються ---
    var heightLimit by mutableIntStateOf(settings.heightLimit)
        private set
    var weightLimitG by mutableLongStateOf(settings.weightLimitG)
        private set
    var palletOn by mutableStateOf(settings.palletOn)
        private set
    var palletG by mutableLongStateOf(settings.palletG)
        private set
    var overhang by mutableIntStateOf(settings.overhang)
        private set

    fun setHeight(mm: Int) { heightLimit = mm; settings.heightLimit = mm }
    fun setWeight(g: Long) { weightLimitG = g; settings.weightLimitG = g }
    fun setPallet(on: Boolean, g: Long) { palletOn = on; palletG = g; settings.palletOn = on; settings.palletG = g }
    fun setOverhangMm(mm: Int) { overhang = mm; settings.overhang = mm }

    // --- Розкладка шару ---
    var layout by mutableStateOf<List<Placed>>(emptyList())
        private set
    /** Своя розкладка (рухали коробки) чи авто. */
    var custom by mutableStateOf(false)
        private set
    var variants by mutableStateOf<List<List<Placed>>>(emptyList())
        private set
    var variantIndex by mutableIntStateOf(0)
        private set
    var selected by mutableStateOf<Int?>(null)
    /** Для якої коробки зроблено поточну розкладку. */
    private var layoutBox: BoxSize? = null
    /** І для якого звису (щоб після «Дублювати» розкладка лишилась точно та сама). */
    private var layoutOverhang: Int = -1

    /** Як стоїть коробка (0 — як вписали, 1 — на боці, 2 — сторч); вибирається автоматично. */
    var orientation by mutableIntStateOf(0)
        private set
    /** Положення, у яких поміщається хоч одна, — найкраще першим (для «Інше положення»). */
    var orientations by mutableStateOf<List<Int>>(emptyList())
        private set
    /** Положення, вибране вручну, — для яких розмірів (змінили розміри — знову авто). */
    private var manualOrientation by mutableStateOf<Pair<Triple<Int, Int, Int>, Int>?>(null)

    /** Розміри, як вписали (довжина, ширина, висота), мм. */
    val dims: Triple<Int, Int, Int>?
        get() {
            val l = Num.lengthMm(boxL) ?: return null
            val w = Num.lengthMm(boxW) ?: return null
            val h = Num.lengthMm(boxH) ?: return null
            return Triple(l, w, h)
        }

    /** Основа коробки на палеті (з урахуванням положення). */
    val box: BoxSize?
        get() {
            val d = dims ?: run {
                val l = Num.lengthMm(boxL) ?: return null
                val w = Num.lengthMm(boxW) ?: return null
                return BoxSize(l, w)
            }
            return Orient.of(d.first, d.second, d.third, orientation).plan
        }

    /** Висота коробки так, як вона стоїть на палеті. */
    val boxHmm: Int? get() = dims?.let { Orient.of(it.first, it.second, it.third, orientation).height }
    val boxG: Long? get() = Num.grams(boxKg)
    val unitsPerBox: Int get() = Num.count(units) ?: 1
    val unitTriple: Triple<Int, Int, Int>?
        get() {
            val l = Num.lengthMm(unitL) ?: return null
            val w = Num.lengthMm(unitW) ?: return null
            val h = Num.lengthMm(unitH) ?: return null
            return Triple(l, w, h)
        }

    /** Ліміт висоти з урахуванням ADR (лише 1,20 м). */
    val effectiveHeight: Int get() = if (adr) minOf(heightLimit, ADR_HEIGHT_MM) else heightLimit

    val check: LayoutCheck? get() = box?.let { Layout.check(layout, it, overhang) }

    val stack: Stack?
        get() {
            val h = boxHmm ?: return null
            val g = boxG ?: return null
            if (layout.isEmpty()) return null
            return Stacking.stack(layout.size, h, g, effectiveHeight, weightLimitG, if (palletOn) palletG else 0)
        }

    /** Можна зберегти: є коробка, вага, розкладка без помилок. */
    val canSave: Boolean get() = stack != null && check?.ok == true

    /** Бланк для поточного вводу (дата — сьогодні). */
    fun currentView(): CardView? {
        val b = box ?: return null
        val h = boxHmm ?: return null
        val g = boxG ?: return null
        // Як у бланку: довжина — найдовший бік основи, висота — як стоїть на палеті.
        val triple = Triple(maxOf(b.l, b.w), minOf(b.l, b.w), h)
        val unit = unitTriple
        return CardView(
            dateMs = System.currentTimeMillis(),
            sku = sku.trim(),
            box = triple,
            boxG = g,
            unitsPerBox = unitsPerBox,
            unit = unit,
            unitG = Num.grams(unitKg),
            stack = stack,
            palletG = if (palletOn) palletG else 0,
            adr = adr,
            flags = flagsOf(triple, g, unit),
        )
    }

    /** Від чого залежить авторозкладка й вибір положення. */
    private data class AutoKey(
        val l: Int?, val w: Int?, val h: Int?,
        val arrows: Boolean, val overhang: Int, val height: Int, val boxG: Long?, val weight: Long, val pallet: Long,
        val manual: Pair<Triple<Int, Int, Int>, Int>?,
    )

    init {
        // Змінились розміри, вага, обмеження чи звис — вибрати найкраще положення й авторозкладку заново
        // (у фоні, з невеликою паузою на введення).
        viewModelScope.launch {
            snapshotFlow {
                AutoKey(
                    Num.lengthMm(boxL), Num.lengthMm(boxW), Num.lengthMm(boxH),
                    arrows, overhang, effectiveHeight, boxG, weightLimitG, if (palletOn) palletG else 0, manualOrientation,
                )
            }
                .distinctUntilChanged()
                .debounce(180)
                .collectLatest { k ->
                    if (k.l == null || k.w == null) {
                        variants = emptyList()
                        orientations = emptyList()
                        if (!custom) { layout = emptyList(); layoutBox = null }
                        return@collectLatest
                    }
                    val (chosen, auto, options) = withContext(Dispatchers.Default) {
                        if (k.h == null) {
                            Triple(0, Packer.auto(BoxSize(k.l, k.w), k.overhang), listOf(0))
                        } else {
                            val ranked = Orient.rank(k.l, k.w, k.h, k.arrows, k.overhang, k.height, k.boxG, k.weight, k.pallet)
                            val fits = ranked.filter { it.auto.count > 0 }.map { it.orientation.index }
                            val manual = k.manual?.takeIf { it.first == Triple(k.l, k.w, k.h) }?.second
                            val pick = ranked.firstOrNull { it.orientation.index == manual && (!k.arrows || manual == 0) } ?: ranked.first()
                            Triple(pick.orientation.index, pick.auto, fits)
                        }
                    }
                    orientation = chosen
                    orientations = options
                    val b = box ?: return@collectLatest
                    val o = k.overhang
                    variants = auto.variants
                    when {
                        layoutBox == b && layoutOverhang == o -> Unit
                        layoutBox != b -> {
                            if (custom && layoutBox != null && layout.isNotEmpty()) _events.tryEmit(UiEvent.LayoutReset)
                            applyVariant(0)
                        }
                        !custom -> applyVariant(0)
                    }
                    layoutBox = b
                    layoutOverhang = o
                }
        }
    }

    /** «Інше положення»: наступне з тих, де поміщається хоч одна коробка. */
    fun nextOrientation() {
        val d = dims ?: return
        val list = orientations
        if (list.size < 2) {
            _events.tryEmit(UiEvent.OnlyOrientation)
            return
        }
        val next = list[(list.indexOf(orientation) + 1).mod(list.size)]
        manualOrientation = d to next
        val o = Orient.of(d.first, d.second, d.third, next)
        _events.tryEmit(UiEvent.Orientation(next, maxOf(o.plan.l, o.plan.w), minOf(o.plan.l, o.plan.w), o.height))
    }

    private fun applyVariant(i: Int) {
        variantIndex = i
        layout = variants.getOrNull(i).orEmpty()
        custom = false
        selected = null
    }

    /** «Авто»: своя розкладка → найкраща авто; авто → наступний варіант з такою самою кількістю. */
    fun auto() {
        if (variants.isEmpty()) return
        if (custom) {
            applyVariant(0)
            if (variants.size > 1) _events.tryEmit(UiEvent.Variant(1, variants.size))
        } else if (variants.size > 1) {
            val next = (variantIndex + 1) % variants.size
            applyVariant(next)
            _events.tryEmit(UiEvent.Variant(next + 1, variants.size))
        } else {
            _events.tryEmit(UiEvent.OnlyVariant)
        }
    }

    private fun edit(list: List<Placed>) {
        layout = list
        custom = true
        layoutBox = box
        layoutOverhang = overhang
    }

    /** Перетягування: [rawX], [rawY] — де під пальцем (мм), коробка прилипає до країв і сусідів. */
    fun move(index: Int, rawX: Int, rawY: Int) {
        val b = box ?: return
        val p = layout.getOrNull(index) ?: return
        val snapped = Layout.snap(layout, index, rawX, rawY, p.rotated, b, overhang)
        if (snapped != p) edit(layout.toMutableList().also { it[index] = snapped })
    }

    fun rotateSelected() {
        val b = box ?: return
        val i = selected ?: return
        if (i !in layout.indices) return
        edit(layout.toMutableList().also { it[i] = Layout.rotate(layout, i, b, overhang) })
    }

    fun removeSelected() {
        val i = selected ?: return
        if (i !in layout.indices) return
        edit(layout.toMutableList().also { it.removeAt(i) })
        selected = null
    }

    fun addBox() {
        val b = box ?: return
        val prefer = selected?.let { layout.getOrNull(it)?.rotated } ?: layout.lastOrNull()?.rotated ?: false
        val spot = Layout.freeSpot(layout, b, overhang, prefer)
        if (spot == null) {
            _events.tryEmit(UiEvent.NoRoom)
            return
        }
        edit(layout + spot)
        selected = layout.size - 1
    }

    fun fill() {
        val b = box ?: return
        val filled = Layout.fill(layout, b, overhang)
        if (filled.size == layout.size) _events.tryEmit(UiEvent.NoRoom) else edit(filled)
    }

    fun clearLayout() {
        edit(emptyList())
        selected = null
    }

    /** Зберегти в історію. */
    fun save() {
        val b = box ?: return
        val h = boxHmm ?: return
        val g = boxG ?: return
        val s = stack ?: return
        if (check?.ok != true) return
        val unit = unitTriple
        val card = ProductCard(
            createdAt = System.currentTimeMillis(),
            sku = sku.trim().take(MAX_SKU),
            boxL = b.l, boxW = b.w, boxH = h, boxG = g, unitsPerBox = unitsPerBox,
            unitL = unit?.first, unitW = unit?.second, unitH = unit?.third, unitG = Num.grams(unitKg),
            heightLimit = effectiveHeight, weightLimitG = weightLimitG, palletG = if (palletOn) palletG else 0,
            overhang = overhang, adr = adr,
            layout = Layout.encode(layout), customLayout = custom,
            perLayer = s.perLayer, layers = s.layers, boxes = s.boxes, partial = s.partial,
            heightMm = s.heightMm, weightG = s.weightG, limitedByWeight = s.limit == Limit.Weight, layersByHeight = s.layersByHeight,
        )
        viewModelScope.launch {
            dao.insert(card)
            _events.tryEmit(UiEvent.Saved)
        }
    }

    /** Почати новий товар: поля порожні, обмеження лишаються. */
    fun clearAll() {
        sku = ""; boxL = ""; boxW = ""; boxH = ""; boxKg = ""; units = ""
        unitL = ""; unitW = ""; unitH = ""; unitKg = ""; adr = false; arrows = false; manualOrientation = null
        layout = emptyList(); custom = false; layoutBox = null; layoutOverhang = -1; selected = null
    }

    /** «Дублювати»: усе зі збереженої картки — в поля (дату поставить нове збереження). */
    fun duplicate(card: ProductCard, comma: Boolean) {
        sku = card.sku
        boxL = Num.metres(card.boxL, comma); boxW = Num.metres(card.boxW, comma); boxH = Num.metres(card.boxH, comma)
        boxKg = Num.kilos(card.boxG, comma)
        units = card.unitsPerBox.toString()
        unitL = card.unitL?.let { Num.metres(it, comma) }.orEmpty()
        unitW = card.unitW?.let { Num.metres(it, comma) }.orEmpty()
        unitH = card.unitH?.let { Num.metres(it, comma) }.orEmpty()
        unitKg = card.unitG?.let { Num.kilos(it, comma) }.orEmpty()
        adr = card.adr
        arrows = false
        // Збережено так, як коробка стояла (основа + висота) — лишаємо саме це положення.
        manualOrientation = Triple(card.boxL, card.boxW, card.boxH) to 0
        orientation = 0
        if (!card.adr) setHeight(card.heightLimit)
        setWeight(card.weightLimitG)
        setPallet(card.palletG > 0, if (card.palletG > 0) card.palletG else palletG)
        setOverhangMm(card.overhang)
        // Розкладка — та сама (і своя лишається своєю): автоперерахунок її не замінить, бо коробка та сама.
        layoutBox = card.plan
        layoutOverhang = card.overhang
        layout = Layout.decode(card.layout)
        custom = card.customLayout
        selected = null
        _events.tryEmit(UiEvent.Duplicated)
    }

    fun delete(card: ProductCard) {
        viewModelScope.launch {
            dao.delete(card)
            _events.tryEmit(UiEvent.Deleted(card))
        }
    }

    fun restore(card: ProductCard) {
        viewModelScope.launch { dao.insert(card) }
    }

    companion object {
        const val MAX_SKU = 40
    }
}
