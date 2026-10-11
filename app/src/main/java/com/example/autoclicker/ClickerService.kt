package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ClickerService : AccessibilityService() {

    companion object {
        var instance: ClickerService? = null
    }

    private lateinit var wm: WindowManager
    private lateinit var cfg: Prefs
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var panel: LinearLayout? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private var statusView: TextView? = null
    private val tools = mutableListOf<View>()
    private var toggleBtn: TextView? = null
    private var running = false
    private var pendingKey: String? = null
    private var pendingSince = 0L
    private var greenNext = 0L
    // Таймерные точки идут по кругу: 1 → пауза → 2 → пауза → ... → последняя → пауза → снова 1
    private var timerIdx = 0
    private var timerNext = 0L
    private var timerCount = 0
    // Отмена своего запроса перед перебитием
    private var lastCancelAt = 0L
    private var cancelTries = 0

    /** Одно нажатие + пауза после него */
    private data class Step(val x: Float, val y: Float, val delay: Long)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cfg = Prefs(this)
        buildPanel()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        instance = null
        super.onDestroy()
    }

    // ---------- Панель ----------

    private fun tv(t: String, size: Float = 18f, click: (() -> Unit)? = null): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(Color.WHITE)
        v.setBackgroundColor(0xCC222222.toInt())
        v.setPadding(28, 18, 28, 18)
        v.gravity = Gravity.CENTER
        if (click != null) v.setOnClickListener { click() }
        return v
    }

    private fun buildPanel() {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        panel = p

        fun add(v: View) {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, 2, 0, 2)
            p.addView(v, lp)
        }

        panelLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        panelLp.gravity = Gravity.TOP or Gravity.START
        panelLp.x = 0
        panelLp.y = 400

        val handle = tv("≡")
        handle.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = panelLp.x; oy = panelLp.y }
                    MotionEvent.ACTION_MOVE -> {
                        panelLp.x = ox + (e.rawX - sx).toInt()
                        panelLp.y = oy + (e.rawY - sy).toInt()
                        wm.updateViewLayout(p, panelLp)
                    }
                }
                return true
            }
        })
        add(handle)

        val toggle = tv("▶") { toggle() }
        toggleBtn = toggle
        add(toggle)

        val b1 = tv("1") { showPicker(true, 1) }
        val b2 = tv("2") { showPicker(true, 2) }
        val b3 = tv("3") { showPicker(true, 3) }
        val bt = tv("＋") { showPicker(false, 0) }
        val bd = tv("🔵") { showDigitPicker() }
        val bg = tv("🟢") { showGreenPicker() }
        val bo = tv("🟠") { showOrangePicker() }
        val bp = tv("💗") { showPinkPicker() }
        val btm = tv("⏱") { showTimerPicker() }
        val bc = tv("🗑") {
            cfg.setTaps(emptyList())
            Toast.makeText(this, "Точки очищены", Toast.LENGTH_SHORT).show()
        }
        for (b in listOf(b1, b2, b3, bt, bd, bo, bg, bp, btm, bc)) { add(b); tools.add(b) }

        val st = tv("", 11f)
        st.maxWidth = 460
        st.visibility = View.GONE
        statusView = st
        add(st)

        wm.addView(p, panelLp)
    }

    private fun setStatus(s: String) {
        statusView?.text = s
    }

    private fun overlayLp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun showPicker(region: Boolean, n: Int) {
        if (running) return
        val taps = cfg.getTaps()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, taps, cfg.getDigits(), cfg.getGreens(), cfg.getOranges(), cfg.getTimers(), region, false, false, false, false, n, cfg.getRect(1), cfg.getRect(2), cfg.getPinks(), false, r3 = cfg.getRect(3),
            onRegion = { r ->
                cfg.setRect(n, r)
                Toast.makeText(this, "Область $n сохранена", Toast.LENGTH_SHORT).show()
                close()
            },
            onDone = {
                cfg.setTaps(taps)
                Toast.makeText(this, "Точек: ${taps.size}", Toast.LENGTH_SHORT).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    /** Синие точки: клавиши цифр 1..9 (и 0) для набора новой цены */
    private fun showDigitPicker() {
        if (running) return
        val digits = cfg.getDigits()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, cfg.getTaps(), digits, cfg.getGreens(), cfg.getOranges(), cfg.getTimers(), false, true, false, false, false, 0, cfg.getRect(1), cfg.getRect(2), cfg.getPinks(), false, r3 = cfg.getRect(3),
            onRegion = {},
            onDone = {
                cfg.setDigits(digits)
                Toast.makeText(this, "Синих точек: ${digits.count { it != null }}", Toast.LENGTH_SHORT).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    /** Зелёные точки: проигрываются по очереди каждые N секунд */
    private fun showGreenPicker() {
        if (running) return
        val greens = cfg.getGreens()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, cfg.getTaps(), cfg.getDigits(), greens, cfg.getOranges(), cfg.getTimers(), false, false, true, false, false, 0,
            cfg.getRect(1), cfg.getRect(2), cfg.getPinks(), false, r3 = cfg.getRect(3),
            onRegion = {},
            onDone = {
                cfg.setGreens(greens)
                Toast.makeText(this, "Зелёных точек: ${greens.size}", Toast.LENGTH_SHORT).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    /** Оранжевые точки: нажимаются по порядку сразу после синих */
    private fun showOrangePicker() {
        if (running) return
        val oranges = cfg.getOranges()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, cfg.getTaps(), cfg.getDigits(), cfg.getGreens(), oranges, cfg.getTimers(),
            false, false, false, true, false, 0, cfg.getRect(1), cfg.getRect(2), cfg.getPinks(), false, r3 = cfg.getRect(3),
            onRegion = {},
            onDone = {
                cfg.setOranges(oranges)
                Toast.makeText(this, "Оранжевых точек: ${oranges.size}", Toast.LENGTH_SHORT).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    /** Розовые точки: нажимаются по порядку, когда скин невыгоден (пропуск) */
    private fun showPinkPicker() {
        if (running) return
        val pinks = cfg.getPinks()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, cfg.getTaps(), cfg.getDigits(), cfg.getGreens(), cfg.getOranges(), cfg.getTimers(),
            false, false, false, false, false, 0, cfg.getRect(1), cfg.getRect(2), pinks, true, r3 = cfg.getRect(3),
            onRegion = {},
            onDone = {
                cfg.setPinks(pinks)
                Toast.makeText(this, "Розовых точек: ${pinks.size}", Toast.LENGTH_SHORT).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    /** Кнопки с таймерами: каждая нажимается со своим интервалом (интервал — в приложении) */
    private fun showTimerPicker() {
        if (running) return
        val timers = cfg.getTimers()
        var layer: PickerLayer? = null
        fun close() {
            layer?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            layer = null
        }
        layer = PickerLayer(
            this, cfg.getTaps(), cfg.getDigits(), cfg.getGreens(), cfg.getOranges(), timers,
            false, false, false, false, true, 0, cfg.getRect(1), cfg.getRect(2), cfg.getPinks(), false, r3 = cfg.getRect(3),
            onRegion = {},
            onDone = {
                cfg.setTimers(timers)
                Toast.makeText(this, "Кнопок с таймером: ${timers.size}. Время — в приложении", Toast.LENGTH_LONG).show()
                close()
            },
            onCancel = { close() }
        )
        wm.addView(layer, overlayLp())
    }

    // ---------- Старт / стоп ----------

    private fun toggle() {
        if (running) {
            running = false
            handler.removeCallbacksAndMessages(null)
            toggleBtn?.text = "▶"
            tools.forEach { it.visibility = View.VISIBLE }
            statusView?.visibility = View.GONE
            return
        }
        if (cfg.getRect(1) == null || cfg.getRect(2) == null || cfg.getTaps().isEmpty()) {
            Toast.makeText(this, "Задайте области 1, 2 и хотя бы одну точку", Toast.LENGTH_LONG).show()
            return
        }
        running = true
        pendingKey = null
        val t0 = SystemClock.elapsedRealtime()
        greenNext = t0 + (cfg.greenSec * 1000).toLong()
        timerIdx = 0
        timerNext = t0 // первая точка срабатывает сразу
        timerCount = cfg.getTimers().size
        toggleBtn?.text = "■"
        tools.forEach { it.visibility = View.GONE }
        statusView?.visibility = View.VISIBLE
        setStatus("работает…")
        schedule(0)
    }

    private fun schedule(ms: Long) {
        handler.removeCallbacksAndMessages(null)
        if (running) handler.postDelayed({ shoot() }, ms)
    }

    // ---------- Скриншот + OCR ----------

    private fun shoot() {
        if (!running) return
        val greens = cfg.getGreens()
        val nowMs = SystemClock.elapsedRealtime()
        val due = mutableListOf<Pt>()
        if (greens.isNotEmpty() && cfg.greenSec > 0 && nowMs >= greenNext) {
            greenNext = nowMs + (cfg.greenSec * 1000).toLong()
            due.addAll(greens)
        }
        val timers = cfg.getTimers()
        if (timers.size != timerCount) { // список изменили — начинаем цикл заново
            timerCount = timers.size
            timerIdx = 0
            timerNext = nowMs
        }
        if (timers.isNotEmpty() && nowMs >= timerNext) {
            if (timerIdx >= timers.size) timerIdx = 0
            val t = timers[timerIdx]
            due.add(Pt(t.x, t.y))
            // время точки = пауза ПОСЛЕ неё до следующей; после последней — снова к первой
            timerNext = nowMs + (t.sec * 1000).toLong()
            timerIdx = (timerIdx + 1) % timers.size
        }
        if (due.isNotEmpty()) {
            setStatus("таймеры / зелёные…")
            runTaps(due, cfg.interval.toLong())
            return
        }
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(r: ScreenshotResult) {
                    val hb = r.hardwareBuffer
                    val hw = Bitmap.wrapHardwareBuffer(hb, r.colorSpace)
                    val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                    hb.close()
                    hw?.recycle()
                    if (bmp == null) {
                        setStatus("ошибка скриншота")
                        schedule(cfg.interval.toLong())
                    } else {
                        process(bmp)
                    }
                }

                override fun onFailure(code: Int) {
                    setStatus("скриншот: код $code")
                    val tooFast = code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT
                    schedule(if (tooFast) 100L else 500L)
                }
            })
    }

    private fun crop(src: Bitmap, r: Rect): Bitmap {
        val l = r.left.coerceIn(0, src.width - 1)
        val t = r.top.coerceIn(0, src.height - 1)
        val w = (r.right - l).coerceIn(1, src.width - l)
        val h = (r.bottom - t).coerceIn(1, src.height - t)
        var c = Bitmap.createBitmap(src, l, t, w, h)
        if (h < 120) {
            val k = 3
            c = Bitmap.createScaledBitmap(c, w * k, h * k, true)
        }
        return c
    }

    private fun ocr(b: Bitmap, cb: (String) -> Unit) {
        recognizer.process(InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { cb(it.text) }
            .addOnFailureListener { cb("") }
    }

    private fun parseNum(t: String): Double? {
        val line = t.lines().firstOrNull { l -> l.any { it.isDigit() } } ?: return null
        val s = line.replace(" ", "").replace("\u00A0", "").replace(',', '.')
        val m = Regex("\\d+(\\.\\d+)?").find(s) ?: return null
        return m.value.toDoubleOrNull()
    }

    private fun process(bmp: Bitmap) {
        val r1 = cfg.getRect(1)
        val r2 = cfg.getRect(2)
        if (r1 == null || r2 == null) { schedule(cfg.interval.toLong()); return }
        val r3 = cfg.getRect(3)
        val c1 = crop(bmp, r1)
        val c2 = crop(bmp, r2)
        val c3 = if (r3 != null) crop(bmp, r3) else null
        bmp.recycle()
        ocr(c1) { t1 ->
            ocr(c2) { t2 ->
                if (c3 != null) ocr(c3) { t3 -> decide(t1, t2, t3) }
                else decide(t1, t2, null)
            }
        }
    }

    private fun decide(t1: String, t2: String, t3: String?) {
        if (!running) return
        val a = parseNum(t1)
        val b = parseNum(t2)
        if (a == null || b == null) {
            setStatus("не прочитал: '${t1.take(12)}' / '${t2.take(12)}'")
            schedule(cfg.interval.toLong())
            return
        }
        // Область 3: если там то же число, что в области 2 — наш запрос уже стоит, перебивать не нужно
        val cc = if (t3 != null) parseNum(t3) else null
        if (cc != null && cc == b) {
            pendingKey = null
            cancelTries = 0
            setStatus("1: $a\n2: $b\n3: $cc\nобласть 3 = область 2 → пропуск")
            schedule(cfg.interval.toLong())
            return
        }
        // Комиссию вычитаем из выбранной области (1 или 2), сравниваем с другой
        val feeOn2 = cfg.feeRegion == 2
        val src = if (feeOn2) b else a
        val ref = if (feeOn2) a else b
        val adj = src * (1 - cfg.fee / 100.0)
        val ok = if (cfg.mode == 0) adj >= ref * (1 - cfg.margin / 100.0)
        else adj >= ref - cfg.margin
        val head = if (feeOn2) "1: $a\n2: $b → ${"%.2f".format(adj)}\n"
        else "1: $a → ${"%.2f".format(adj)}\n2: $b\n"
        if (!ok) {
            pendingKey = null
            val pinks = cfg.getPinks()
            if (pinks.isNotEmpty()) {
                setStatus(head + "пропуск ✘ → розовая")
                runTaps(pinks, cfg.interval.toLong())
            } else {
                setStatus(head + "пропуск ✘")
                schedule(cfg.interval.toLong())
            }
            return
        }
        val now = SystemClock.elapsedRealtime()
        // Область 3 пустая — своего запроса нет (или отмена прошла)
        if (cc == null) cancelTries = 0
        // Наш запрос стоит (область 3), но его перебили (3 ≠ 2), и сделка всё ещё выгодна:
        // сначала отменяем свой запрос розовыми, а перебиваем уже на следующем проходе — когда запроса не будет
        if (cc != null && cancelTries < 2) {
            val pinks = cfg.getPinks()
            if (pinks.isNotEmpty()) {
                cancelTries++
                pendingKey = null
                lastCancelAt = now
                setStatus(head + "3: $cc ≠ 2: $b → отменяю свой запрос (розовая), потом перебью")
                runTaps(pinks, cfg.cancelWait.toLong())
                return
            }
        }
        // Условие выполнено: ждём, пока числа не меняются заданное время
        val key = "$a|$b"
        if (key != pendingKey) {
            pendingKey = key
            pendingSince = now
        }
        val waited = now - pendingSince
        // Сразу после отмены своего запроса не ждём подтверждения — перебиваем без задержки
        val afterCancel = cc == null && lastCancelAt != 0L && now - lastCancelAt < 5000
        if (waited >= cfg.confirmDelay || afterCancel) {
            pendingKey = null
            lastCancelAt = 0L
            val seq = buildSequence(a, b, head)
            if (seq == null) {
                schedule(cfg.interval.toLong())
            } else {
                runSteps(seq, 0, 0) { schedule(cfg.cooldown.toLong()) }
            }
        } else {
            setStatus(head + "ждём ${waited}/${cfg.confirmDelay} мс")
            schedule(cfg.interval.toLong())
        }
    }

    // ---------- Нажатия по порядку ----------

    /**
     * Собирает последовательность нажатий.
     * Синие точки не заданы — как раньше: только красные точки.
     * Синие заданы — перебитие: красные → цифры новой цены (число выбранной области + шаг) → оранжевые → красные.
     * Между цифрами — отдельная пауза (digitDelay), чтобы приложение не теряло нажатия.
     * Возвращает null, если для нужной цифры нет синей точки.
     */
    private fun buildSequence(a: Double, b: Double, head: String): List<Step>? {
        val td = cfg.tapDelay.toLong()
        val dd = maxOf(cfg.digitDelay, cfg.tapDelay).toLong()
        fun steps(l: List<Pt>) = l.map { Step(it.x, it.y, td) }
        val red = steps(cfg.getTaps())
        val digits = cfg.getDigits()
        if (digits.all { it == null }) {
            setStatus(head + "ЗАКАЗЫВАЮ ✔")
            return red
        }
        val base = Math.floor(if (cfg.outbidRegion == 1) a else b).toLong()
        val target = base + cfg.step
        val typed = mutableListOf<Step>()
        for (ch in target.toString()) {
            val p = digits[ch - '0']
            if (p == null) {
                setStatus(head + "нет синей точки для цифры $ch")
                return null
            }
            typed.add(Step(p.x, p.y, dd))
        }
        setStatus(head + "перебиваю область ${cfg.outbidRegion}: $base → $target ✔")
        // красные → синие (цифры) → оранжевые → красные
        return red + typed + steps(cfg.getOranges()) + red
    }

    private fun runTaps(taps: List<Pt>, endDelay: Long) {
        val d = cfg.tapDelay.toLong()
        runSteps(taps.map { Step(it.x, it.y, d) }, 0, 0) { schedule(endDelay) }
    }

    /**
     * Нажимает шаги по очереди. Если нажатие отменено системой или не отправилось —
     * повторяет его (до 2 раз), а не пропускает: раньше из-за этого терялись цифры (172 → 12).
     */
    private fun runSteps(steps: List<Step>, i: Int, tries: Int, onEnd: () -> Unit) {
        if (!running) return
        if (i >= steps.size) { onEnd(); return }
        val st = steps[i]
        val path = Path()
        path.moveTo(st.x, st.y)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        val next = Runnable { runSteps(steps, i + 1, 0, onEnd) }
        val again = Runnable { runSteps(steps, i, tries + 1, onEnd) }
        fun retryOrSkip() {
            if (tries < 2) handler.postDelayed(again, 150L) else handler.postDelayed(next, st.delay)
        }
        val started = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) {
                handler.postDelayed(next, st.delay)
            }
            override fun onCancelled(d: GestureDescription?) {
                retryOrSkip()
            }
        }, null)
        if (!started) retryOrSkip()
    }
}
