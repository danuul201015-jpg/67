package com.example.autoclicker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var status: TextView
    private lateinit var fee: EditText
    private lateinit var margin: EditText
    private lateinit var cps: EditText
    private lateinit var confirm: EditText
    private lateinit var tapDelay: EditText
    private lateinit var digitDelay: EditText
    private lateinit var cancelWait: EditText
    private lateinit var cooldown: EditText
    private lateinit var step: EditText
    private lateinit var greenSec: EditText
    private lateinit var rbOut1: RadioButton
    private lateinit var rbOut2: RadioButton
    private lateinit var rbFee1: RadioButton
    private lateinit var rbFee2: RadioButton
    private lateinit var rbPercent: RadioButton
    private lateinit var rbAbs: RadioButton
    private lateinit var info: TextView
    private lateinit var timersBox: LinearLayout
    private var timerList = mutableListOf<TimerBtn>()
    private val timerFields = mutableListOf<EditText>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(24), dp(16), dp(24))
        val sv = ScrollView(this)
        sv.addView(root)
        setContentView(sv)

        fun label(t: String) {
            val v = TextView(this); v.text = t; v.setPadding(0, dp(12), 0, 0); root.addView(v)
        }
        fun field(l: String, value: String): EditText {
            label(l)
            val e = EditText(this)
            e.setText(value)
            e.inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            root.addView(e)
            return e
        }
        fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

        val title = TextView(this)
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "" }
        title.text = "АвтоКликер v$ver"; title.textSize = 24f
        root.addView(title)

        status = TextView(this); status.setPadding(0, dp(8), 0, dp(8)); root.addView(status)

        val bAcc = Button(this)
        bAcc.text = "Включить службу (Спец. возможности)"
        bAcc.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(bAcc)

        fee = field("Комиссия, % (вычитается из выбранной ниже области)", fmt(p.fee))
        label("Из какой области вычитать комиссию")
        val rgFee = RadioGroup(this)
        rbFee1 = RadioButton(this); rbFee1.text = "Область 1"; rbFee1.id = View.generateViewId()
        rbFee2 = RadioButton(this); rbFee2.text = "Область 2"; rbFee2.id = View.generateViewId()
        rgFee.addView(rbFee1); rgFee.addView(rbFee2)
        if (p.feeRegion == 2) rbFee2.isChecked = true else rbFee1.isChecked = true
        root.addView(rgFee)

        label("Режим допустимого минуса")
        val rg = RadioGroup(this)
        rbPercent = RadioButton(this); rbPercent.text = "В процентах"; rbPercent.id = View.generateViewId()
        rbAbs = RadioButton(this); rbAbs.text = "В сумме (валюта)"; rbAbs.id = View.generateViewId()
        rg.addView(rbPercent); rg.addView(rbAbs)
        if (p.mode == 0) rbPercent.isChecked = true else rbAbs.isChecked = true
        root.addView(rg)

        margin = field("Допустимый минус (% или сумма — по режиму)", fmt(p.margin))
        cps = field("Сравнений в секунду (напр. 1 или 2.5)", fmt(p.checksPerSec))
        confirm = field("Ждать неизменности цены, мс (0 = сразу)", p.confirmDelay.toString())
        tapDelay = field("Пауза между нажатиями, мс", p.tapDelay.toString())
        digitDelay = field("Пауза между цифрами (синие), мс — увеличьте, если цифры теряются", p.digitDelay.toString())
        cancelWait = field("Пауза после отмены своего запроса (розовые), мс", p.cancelWait.toString())
        cooldown = field("Кулдаун после заказа, мс", p.cooldown.toString())
        label("Какую область перебивать (синие точки)")
        val rgOut = RadioGroup(this)
        rbOut1 = RadioButton(this); rbOut1.text = "Область 1"; rbOut1.id = View.generateViewId()
        rbOut2 = RadioButton(this); rbOut2.text = "Область 2"; rbOut2.id = View.generateViewId()
        rgOut.addView(rbOut1); rgOut.addView(rbOut2)
        if (p.outbidRegion == 2) rbOut2.isChecked = true else rbOut1.isChecked = true
        root.addView(rgOut)
        step = field("Шаг перебития: на сколько повышать цену выбранной области", p.step.toString())
        greenSec = field("Интервал зелёных точек, сек (0 = выключено)", fmt(p.greenSec))

        label("Кнопки с таймерами (⏱ на панели — расставить точки). Время каждой — в секундах:")
        timersBox = LinearLayout(this)
        timersBox.orientation = LinearLayout.VERTICAL
        root.addView(timersBox)

        val bSave = Button(this)
        bSave.text = "Сохранить"
        bSave.setOnClickListener { save(); Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show() }
        root.addView(bSave)

        info = TextView(this); info.setPadding(0, dp(16), 0, 0); root.addView(info)

        val help = TextView(this)
        help.setPadding(0, dp(16), 0, 0)
        help.text = """Как пользоваться:
1. Включите службу «АвтоКликер» в спец. возможностях.
2. Откройте нужное приложение. Поверх экрана появится маленькая панель.
3. Кнопка «1» — обведите пальцем область 1 (число, из которого вычитается комиссия).
4. Кнопка «2» — обведите область 2 (число для сравнения).
4а. Кнопка «3» (необязательно) — обведите область 3 (ваш текущий запрос). Если число в области 3 равно числу в области 2, перебитие пропускается.
5. Кнопка «＋» — ставьте точки нажатий по порядку, затем «Готово».
6. «💗» — розовые точки: нажимаются, когда цена невыгодна (пропуск). «🗑» — очистить красные точки. «≡» — перетащить панель.
7. «🔵» — синие точки: по очереди нажмите на клавиши цифр 1…9 (на клавиатуре приложения), затем при желании 0 («Пропуск» — если 0 не нужен).
8. «🟢» — зелёные точки: расставьте по порядку, «Готово». Пока приложение работает, оно каждые N секунд (поле «Интервал зелёных точек») проигрывает всю цепочку зелёных точек по порядку, по кругу.
9. «🟠» — оранжевые точки: расставьте по порядку, «Готово». Они нажимаются после синих, когда набор цены закончен (только при заданных синих).
10. «⏱» — кнопки с таймерами: тапайте по экрану — каждый тап создаёт отдельную кнопку. Время каждой (в секундах) задаётся здесь, в приложении, в блоке «Кнопки с таймерами». Каждая кнопка нажимается сама по своему таймеру, кнопок можно сколько угодно.
11. «▶» — старт, «■» — стоп.

Логика:
Комиссия вычитается из области, выбранной в настройках (1 или 2): число после вычета = число − комиссия%.
Режим %: берём, если оно ≥ число другой области − допустимый%.
Режим суммы: берём, если оно ≥ число другой области − допустимая сумма.
Подтверждение: когда условие выполнено, приложение ждёт «Ждать неизменности цены». Если за это время числа не изменились и условие всё ещё выполняется — нажимает точки. Если цена изменилась — отсчёт начинается заново.
Кулдаун — пауза после заказа перед следующими проверками.

Перебитие (если заданы синие точки):
когда цена выгодна, приложение нажимает красные точки, набирает синими точками новую цену = число выбранной области (1 или 2, в настройках) + шаг (например 750 → 751), затем нажимает оранжевые точки и снова красные. Если синих точек нет — работает как раньше (только красные).

Отмена своего запроса (нужны область 3 и розовые точки):
если в области 3 есть число, оно не равно числу области 2 (нас перебили) и сделка всё ещё выгодна — приложение сначала нажимает розовые точки (отмена своего запроса), ждёт «Пауза после отмены» и только на следующей проверке, когда своего запроса уже нет, перебивает."""
        root.addView(help)
    }

    private fun fmtSec(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    private fun buildTimerRows() {
        timersBox.removeAllViews()
        timerFields.clear()
        timerList = p.getTimers()
        if (timerList.isEmpty()) {
            val t = TextView(this); t.text = "(пока нет)"; timersBox.addView(t)
        }
        timerList.forEachIndexed { i, t ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val name = TextView(this); name.text = "⏱${i + 1}"
            row.addView(name)
            val e = EditText(this)
            e.setText(fmtSec(t.sec))
            e.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            e.minEms = 5
            row.addView(e)
            val unit = TextView(this); unit.text = "с"
            row.addView(unit)
            val del = Button(this); del.text = "✕"
            del.setOnClickListener {
                saveTimers()
                timerList.removeAt(i)
                p.setTimers(timerList)
                buildTimerRows()
            }
            row.addView(del)
            timersBox.addView(row)
            timerFields.add(e)
        }
    }

    private fun saveTimers() {
        if (timerFields.size != timerList.size) return
        timerList.forEachIndexed { i, t ->
            val v = timerFields[i].text.toString().replace(',', '.').toDoubleOrNull()
            if (v != null) t.sec = v.coerceAtLeast(0.1)
        }
        p.setTimers(timerList)
    }

    private fun save() {
        saveTimers()
        fun d(e: EditText) = e.text.toString().replace(',', '.').toDoubleOrNull()
        d(fee)?.let { p.fee = it }
        d(margin)?.let { p.margin = it }
        d(cps)?.let { p.checksPerSec = it.coerceIn(0.1, 20.0) }
        d(confirm)?.let { p.confirmDelay = it.toInt().coerceAtLeast(0) }
        d(tapDelay)?.let { p.tapDelay = it.toInt().coerceAtLeast(0) }
        d(digitDelay)?.let { p.digitDelay = it.toInt().coerceAtLeast(0) }
        d(cancelWait)?.let { p.cancelWait = it.toInt().coerceAtLeast(0) }
        d(cooldown)?.let { p.cooldown = it.toInt().coerceAtLeast(0) }
        d(step)?.let { p.step = it.toInt().coerceAtLeast(1) }
        d(greenSec)?.let { p.greenSec = it.coerceAtLeast(0.0) }
        p.mode = if (rbPercent.isChecked) 0 else 1
        p.feeRegion = if (rbFee2.isChecked) 2 else 1
        p.outbidRegion = if (rbOut2.isChecked) 2 else 1
    }

    override fun onPause() { super.onPause(); save() }

    override fun onResume() {
        super.onResume()
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = en.contains("$packageName/") && en.contains("ClickerService")
        status.text = if (on) "Служба: включена ✔" else "Служба: ВЫКЛЮЧЕНА ✘"
        buildTimerRows()
        info.text = "Область 1: ${if (p.getRect(1) != null) "задана" else "нет"}\n" +
                "Область 2: ${if (p.getRect(2) != null) "задана" else "нет"}\n" +
                "Область 3: ${if (p.getRect(3) != null) "задана" else "нет"}\n" +
                "Красных точек: ${p.getTaps().size}\n" +
                "Синих точек (цифры): ${p.getDigits().count { it != null }}/10\n" +
                "Оранжевых точек: ${p.getOranges().size}\n" +
                "Зелёных точек: ${p.getGreens().size}\n" +
                "Кнопок с таймером: ${p.getTimers().size}"
    }
}
