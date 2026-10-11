package com.example.autoclicker

import android.content.Context
import android.graphics.Rect

data class Pt(val x: Float, val y: Float)

/** Кнопка таймера: после нажатия ждём [sec] секунд и нажимаем следующую (по кругу) */
data class TimerBtn(val x: Float, val y: Float, var sec: Double)

class Prefs(c: Context) {
    private val sp = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    /** Комиссия, которая вычитается из числа 1 (в %) */
    var fee: Double
        get() = sp.getFloat("fee", 20f).toDouble()
        set(v) { sp.edit().putFloat("fee", v.toFloat()).apply() }

    /** Из какой области вычитается комиссия: 1 (по умолчанию) или 2 */
    var feeRegion: Int
        get() = sp.getInt("feeRegion", 1)
        set(v) { sp.edit().putInt("feeRegion", v).apply() }

    /** 0 = допустимый минус в процентах, 1 = допустимый минус в сумме */
    var mode: Int
        get() = sp.getInt("mode", 0)
        set(v) { sp.edit().putInt("mode", v).apply() }

    /** Допустимый минус (% или сумма, в зависимости от режима) */
    var margin: Double
        get() = sp.getFloat("margin", 0f).toDouble()
        set(v) { sp.edit().putFloat("margin", v.toFloat()).apply() }

    /** Сколько сравнений (скриншотов) в секунду */
    var checksPerSec: Double
        get() = sp.getFloat("cps", 1f).toDouble()
        set(v) { sp.edit().putFloat("cps", v.toFloat()).apply() }

    /** Пауза между сравнениями, мс (считается из сравнений в секунду) */
    val interval: Int
        get() = (1000.0 / checksPerSec.coerceIn(0.1, 20.0)).toInt()

    /** Сколько мс цена должна оставаться неизменной и подходящей, прежде чем нажимать */
    var confirmDelay: Int
        get() = sp.getInt("confirm", 500)
        set(v) { sp.edit().putInt("confirm", v).apply() }

    /** Пауза между нажатиями, мс */
    var tapDelay: Int
        get() = sp.getInt("tapDelay", 100)
        set(v) { sp.edit().putInt("tapDelay", v).apply() }

    /** Пауза между нажатиями цифр (синие точки), мс — чтобы приложение не теряло цифры */
    var digitDelay: Int
        get() = sp.getInt("digitDelay", 250)
        set(v) { sp.edit().putInt("digitDelay", v).apply() }

    /** Пауза после отмены своего запроса (розовые точки) перед следующей проверкой, мс */
    var cancelWait: Int
        get() = sp.getInt("cancelWait", 800)
        set(v) { sp.edit().putInt("cancelWait", v).apply() }

    /** Пауза после срабатывания, мс */
    var cooldown: Int
        get() = sp.getInt("cooldown", 3000)
        set(v) { sp.edit().putInt("cooldown", v).apply() }

    /** На сколько повышаем цену числа 2 при перебитии (напр. 750 -> 751 при шаге 1) */
    var step: Int
        get() = sp.getInt("step", 1)
        set(v) { sp.edit().putInt("step", v).apply() }

    /** Какую область перебиваем: 1 или 2 (по умолчанию 1) */
    var outbidRegion: Int
        get() = sp.getInt("outbidRegion", 1)
        set(v) { sp.edit().putInt("outbidRegion", v).apply() }

    /** Интервал проигрывания зелёных точек, секунд (0 = выключено) */
    var greenSec: Double
        get() = sp.getFloat("greenSec", 300f).toDouble()
        set(v) { sp.edit().putFloat("greenSec", v.toFloat()).apply() }

    fun getGreens(): MutableList<Pt> {
        val s = sp.getString("greens", "") ?: ""
        val out = mutableListOf<Pt>()
        for (item in s.split(";")) {
            val q = item.split(",")
            if (q.size == 2) {
                val x = q[0].toFloatOrNull()
                val y = q[1].toFloatOrNull()
                if (x != null && y != null) out.add(Pt(x, y))
            }
        }
        return out
    }

    fun setGreens(list: List<Pt>) {
        sp.edit().putString("greens", list.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    fun getRect(n: Int): Rect? {
        val s = sp.getString("r$n", null) ?: return null
        val p = s.split(",").mapNotNull { it.toIntOrNull() }
        return if (p.size == 4) Rect(p[0], p[1], p[2], p[3]) else null
    }

    fun setRect(n: Int, r: Rect) {
        sp.edit().putString("r$n", "${r.left},${r.top},${r.right},${r.bottom}").apply()
    }

    fun getTaps(): MutableList<Pt> {
        val s = sp.getString("taps", "") ?: ""
        val out = mutableListOf<Pt>()
        for (item in s.split(";")) {
            val p = item.split(",")
            if (p.size == 2) {
                val x = p[0].toFloatOrNull()
                val y = p[1].toFloatOrNull()
                if (x != null && y != null) out.add(Pt(x, y))
            }
        }
        return out
    }

    fun setTaps(list: List<Pt>) {
        sp.edit().putString("taps", list.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    /** Синие точки: клавиши цифр 0..9 (индекс массива = цифра) */
    fun getDigits(): Array<Pt?> {
        val out = arrayOfNulls<Pt>(10)
        val s = sp.getString("digits", "") ?: ""
        for (item in s.split(";")) {
            val kv = item.split(":")
            if (kv.size != 2) continue
            val d = kv[0].toIntOrNull() ?: continue
            val xy = kv[1].split(",")
            if (d in 0..9 && xy.size == 2) {
                val x = xy[0].toFloatOrNull()
                val y = xy[1].toFloatOrNull()
                if (x != null && y != null) out[d] = Pt(x, y)
            }
        }
        return out
    }

    fun setDigits(a: Array<Pt?>) {
        val s = a.withIndex()
            .filter { it.value != null }
            .joinToString(";") { "${it.index}:${it.value!!.x},${it.value!!.y}" }
        sp.edit().putString("digits", s).apply()
    }

    /** Оранжевые точки: нажимаются по порядку сразу после синих (цифр) */
    fun getOranges(): MutableList<Pt> {
        val s = sp.getString("oranges", "") ?: ""
        val out = mutableListOf<Pt>()
        for (item in s.split(";")) {
            val q = item.split(",")
            if (q.size == 2) {
                val x = q[0].toFloatOrNull()
                val y = q[1].toFloatOrNull()
                if (x != null && y != null) out.add(Pt(x, y))
            }
        }
        return out
    }

    fun setOranges(list: List<Pt>) {
        sp.edit().putString("oranges", list.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    /** Розовые точки: нажимаются, когда скин невыгоден (пропуск) */
    fun getPinks(): MutableList<Pt> {
        val s = sp.getString("pinks", "") ?: ""
        val out = mutableListOf<Pt>()
        for (item in s.split(";")) {
            val q = item.split(",")
            if (q.size == 2) {
                val x = q[0].toFloatOrNull()
                val y = q[1].toFloatOrNull()
                if (x != null && y != null) out.add(Pt(x, y))
            }
        }
        return out
    }

    fun setPinks(list: List<Pt>) {
        sp.edit().putString("pinks", list.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    /** Кнопки с таймерами: нажимаются по кругу, у каждой своя пауза после нажатия (сек) */
    fun getTimers(): MutableList<TimerBtn> {
        val s = sp.getString("timers", "") ?: ""
        val out = mutableListOf<TimerBtn>()
        for (item in s.split(";")) {
            val q = item.split(",")
            if (q.size == 3) {
                val x = q[0].toFloatOrNull()
                val y = q[1].toFloatOrNull()
                val t = q[2].toDoubleOrNull()
                if (x != null && y != null && t != null) out.add(TimerBtn(x, y, t))
            }
        }
        return out
    }

    fun setTimers(list: List<TimerBtn>) {
        sp.edit().putString("timers", list.joinToString(";") { "${it.x},${it.y},${it.sec}" }).apply()
    }
}
