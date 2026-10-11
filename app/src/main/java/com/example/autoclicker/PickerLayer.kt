package com.example.autoclicker

import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

/**
 * Полноэкранный слой: рисование области пальцем, расстановка красных точек нажатий
 * или расстановка синих точек (клавиши цифр 1..9, затем необязательный 0).
 */
class PickerLayer(
    ctx: Context,
    private val taps: MutableList<Pt>,
    private val digits: Array<Pt?>,
    private val greens: MutableList<Pt>,
    private val oranges: MutableList<Pt>,
    private val timers: MutableList<TimerBtn>,
    private val regionMode: Boolean,
    private val digitMode: Boolean,
    private val greenMode: Boolean,
    private val orangeMode: Boolean,
    private val timerMode: Boolean,
    private val regionNo: Int,
    private val r1: Rect?,
    private val r2: Rect?,
    private val pinks: MutableList<Pt> = mutableListOf(),
    private val pinkMode: Boolean = false,
    private val r3: Rect? = null,
    private val onRegion: (Rect) -> Unit,
    private val onDone: () -> Unit,
    private val onCancel: () -> Unit
) : FrameLayout(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sx = 0f
    private var sy = 0f
    private var cx = 0f
    private var cy = 0f
    private var drag = false
    private val loc = IntArray(2)

    /** Список, который сейчас редактируем: красные или зелёные точки */
    private val list: MutableList<Pt> get() = if (greenMode) greens else if (orangeMode) oranges else if (pinkMode) pinks else taps

    /** Порядок расстановки синих точек: 1..9, потом 0 */
    private val order = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    private var cur = 0

    init {
        setWillNotDraw(false)
        val bar = LinearLayout(ctx)
        bar.orientation = LinearLayout.HORIZONTAL
        fun btn(t: String, f: () -> Unit) {
            val v = TextView(ctx)
            v.text = t
            v.textSize = if (digitMode) 16f else 18f
            v.setTextColor(Color.WHITE)
            v.background = Ui.round(ctx, 0xEE15151A.toInt(), 12, Ui.ORANGE)
            val pad = if (digitMode) 24 else 36
            v.setPadding(pad, 24, pad, 24)
            v.setOnClickListener { f() }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(8, 0, 8, 0)
            bar.addView(v, lp)
        }
        if (digitMode) {
            btn("Готово") { onDone() }
            btn("↩") {
                if (cur > 0) {
                    cur--
                    digits[order[cur]] = null
                    invalidate()
                }
            }
            btn("Пропуск") { if (cur < order.size) { cur++; invalidate() } }
        } else if (!regionMode) {
            btn("Готово") { onDone() }
            btn("↩") {
                if (timerMode) {
                    if (timers.isNotEmpty()) { timers.removeAt(timers.size - 1); invalidate() }
                } else if (list.isNotEmpty()) { list.removeAt(list.size - 1); invalidate() }
            }
        }
        btn("Отмена") { onCancel() }
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = 140
        addView(bar, lp)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (digitMode) {
            if (e.actionMasked == MotionEvent.ACTION_UP && cur < order.size) {
                digits[order[cur]] = Pt(e.rawX, e.rawY)
                cur++
                invalidate()
            }
        } else if (regionMode) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; cx = sx; cy = sy; drag = true }
                MotionEvent.ACTION_MOVE -> { cx = e.rawX; cy = e.rawY }
                MotionEvent.ACTION_UP -> {
                    drag = false
                    val r = Rect(min(sx, e.rawX).toInt(), min(sy, e.rawY).toInt(),
                        max(sx, e.rawX).toInt(), max(sy, e.rawY).toInt())
                    if (r.width() > 20 && r.height() > 10) onRegion(r)
                }
            }
            invalidate()
        } else if (e.actionMasked == MotionEvent.ACTION_UP) {
            if (timerMode) timers.add(TimerBtn(e.rawX, e.rawY, 10.0))
            else list.add(Pt(e.rawX, e.rawY))
            invalidate()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0x55000000)
        getLocationOnScreen(loc)
        canvas.save()
        canvas.translate(-loc[0].toFloat(), -loc[1].toFloat())

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        r1?.let { paint.color = Color.GREEN; canvas.drawRect(it, paint) }
        r2?.let { paint.color = Color.CYAN; canvas.drawRect(it, paint) }
        r3?.let { paint.color = Color.YELLOW; canvas.drawRect(it, paint) }
        if (drag) {
            paint.color = Color.RED
            canvas.drawRect(min(sx, cx), min(sy, cy), max(sx, cx), max(sy, cy), paint)
        }
        // красные точки
        taps.forEachIndexed { i, p ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCCFF3030.toInt()
            canvas.drawCircle(p.x, p.y, 36f, paint)
            paint.color = Color.WHITE
            paint.textSize = 36f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText((i + 1).toString(), p.x, p.y + 12f, paint)
        }
        // зелёные точки
        greens.forEachIndexed { i, p ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCC1FA845.toInt()
            canvas.drawCircle(p.x, p.y, 33f, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText((i + 1).toString(), p.x, p.y + 12f, paint)
        }
        // оранжевые точки
        oranges.forEachIndexed { i, p ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCCFF8C1A.toInt()
            canvas.drawCircle(p.x, p.y, 33f, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText((i + 1).toString(), p.x, p.y + 12f, paint)
        }
        // розовые точки (пропуск)
        pinks.forEachIndexed { i, p ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCCFF5FB4.toInt()
            canvas.drawCircle(p.x, p.y, 33f, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText((i + 1).toString(), p.x, p.y + 12f, paint)
        }
        // кнопки с таймерами (фиолетовые)
        timers.forEachIndexed { i, t ->
            paint.style = Paint.Style.FILL
            paint.color = 0xCCA040E0.toInt()
            canvas.drawCircle(t.x, t.y, 33f, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("T${i + 1}", t.x, t.y + 12f, paint)
            paint.textSize = 24f
            canvas.drawText("${if (t.sec == Math.floor(t.sec)) t.sec.toLong().toString() else t.sec.toString()}с", t.x, t.y + 62f, paint)
        }
        // синие точки (цифры)
        for (d in 0..9) {
            val p = digits[d] ?: continue
            paint.style = Paint.Style.FILL
            paint.color = 0xCC2F6BFF.toInt()
            canvas.drawCircle(p.x, p.y, 30f, paint)
            paint.color = Color.WHITE
            paint.textSize = 34f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(d.toString(), p.x, p.y + 12f, paint)
        }
        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.textSize = 40f
        paint.textAlign = Paint.Align.LEFT
        val hint = when {
            digitMode && cur < order.size -> {
                val d = order[cur]
                if (d == 0) "Точка цифры 0 (необязательно)" else "Нажмите точку цифры $d"
            }
            digitMode -> "Все цифры заданы — «Готово»"
            regionMode -> "Обведите область $regionNo пальцем"
            greenMode -> "Зелёные точки по порядку, затем «Готово»"
            orangeMode -> "Оранжевые точки по порядку (после синих), затем «Готово»"
            pinkMode -> "Розовые точки — нажимаются при пропуске (невыгодно), затем «Готово»"
            timerMode -> "Ставьте кнопки таймеров (время — в настройках), затем «Готово»"
            else -> "Нажимайте точки по порядку, затем «Готово»"
        }
        canvas.drawText(hint, 30f, 100f, paint)
    }
}
