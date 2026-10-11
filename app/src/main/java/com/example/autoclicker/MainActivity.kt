package com.example.autoclicker

import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var p: Prefs
    private lateinit var root: LinearLayout
    private lateinit var statusRing: View
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var statusHint: TextView
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
    private lateinit var confirmCount: EditText
    private lateinit var holdAfterBid: EditText
    private lateinit var feeSeg: LinearLayout
    private lateinit var modeSeg: LinearLayout
    private lateinit var outSeg: LinearLayout
    private lateinit var statsBox: LinearLayout
    private lateinit var timersBox: LinearLayout
    private lateinit var helpBody: TextView
    private lateinit var helpChev: ImageView
    private var helpOpen = false
    private var timerList = mutableListOf<TimerBtn>()
    private val timerFields = mutableListOf<EditText>()
    private var pulseSet: AnimatorSet? = null
    private lateinit var backupLabel: TextView
    private lateinit var presetName: EditText
    private lateinit var presetsBox: LinearLayout
    private var skipSaveOnce = false

    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private val WC = ViewGroup.LayoutParams.WRAP_CONTENT

    /** Метки в тексте справки заменяются на SVG-иконки (без эмодзи) */
    private val tokens = mapOf(
        "[plus]" to R.drawable.ic_plus,
        "[grip]" to R.drawable.ic_grip,
        "[trash]" to R.drawable.ic_trash,
        "[play]" to R.drawable.ic_play,
        "[stop]" to R.drawable.ic_stop,
        "[blue]" to R.drawable.ic_dot_blue,
        "[green]" to R.drawable.ic_dot_green,
        "[orange]" to R.drawable.ic_dot_orange,
        "[pink]" to R.drawable.ic_dot_pink,
        "[timer]" to R.drawable.ic_dot_timer
    )

    private val HELP = """
Как пользоваться:
1. Включите службу «АвтоКликер» в спец. возможностях.
2. Откройте нужное приложение. Поверх экрана появится панель.
3. Кнопка «1» — обведите область 1 (число, из которого вычитается комиссия).
4. Кнопка «2» — обведите область 2 (число для сравнения).
4а. Кнопка «3» (необязательно) — обведите область 3 (ваш текущий запрос). Если число в области 3 равно числу в области 2, перебитие пропускается.
5. Кнопка [plus] — ставьте красные точки нажатий по порядку, затем «Готово».
6. [pink] — розовые точки: нажимаются при пропуске (цена невыгодна). [trash] — очистить красные точки. [grip] — перетащить панель.
7. [blue] — синие точки: по очереди нажмите клавиши цифр 1…9 на клавиатуре приложения, затем 0, если он нужен («Пропуск», если не нужен).
8. [green] — зелёные точки: расставьте по порядку. Каждые N секунд (поле «Интервал зелёных точек») они проигрываются по кругу, пока служба работает.
9. [orange] — оранжевые точки: нажимаются после синих, когда набор цены закончен.
10. [timer] — кнопки с таймерами: тап по экрану создаёт новую кнопку. Время каждой задаётся в блоке «Кнопки с таймерами».
11. [play] — старт, [stop] — стоп.

Логика:
Комиссия вычитается из области, выбранной в настройках. Режим %: берём, если результат не меньше числа другой области минус допустимый %. Режим суммы: берём, если результат не меньше числа другой области минус допустимая сумма.
Подтверждение: когда условие выполнено, приложение ждёт «Ждать неизменности цены». Если числа не изменились и условие всё ещё выполняется, нажимает точки.
Кулдаун — пауза после заказа.

Перебитие (нужны синие точки): когда цена выгодна, приложение нажимает красные, набирает синими новую цену (число выбранной области плюс шаг), затем оранжевые и снова красные.

Отмена своего запроса (нужны область 3 и розовые точки): если ваш запрос в области 3 ниже числа области 2 (вас перебили) и сделка всё ещё выгодна, розовые нажимаются только после нескольких одинаковых проверок подряд («Одинаковых проверок подряд»). После своей ставки приложение ждёт «Пауза после своей ставки» и ничего не отменяет. Если своего запроса нет, розовые не нажимаются.
""".trimIndent()

    companion object {
        private const val REQ_CREATE = 101
        private const val REQ_OPEN = 102
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun fmt(d: Double) = if (d == Math.floor(d)) d.toLong().toString() else d.toString()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(30), dp(18), dp(36))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Ui.BG)
            isFillViewport = true
            addView(root)
        })
        buildHeader()
        buildStatus()
        buildTuning()
        buildTimers()
        buildStats()
        buildBackup()
        buildHelp()
        root.addView(primaryButton("Сохранить") {
            save()
            Toast.makeText(this, "Сохранено", Toast.LENGTH_SHORT).show()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(18) }
        })
        intro()
    }

    // ---------- Блоки интерфейса ----------

    private fun buildHeader() {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6))
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        }
        row.addView(logo)
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        titles.addView(TextView(this).apply {
            text = SpannableString("АвтоКликер").apply {
                setSpan(ForegroundColorSpan(Ui.ORANGE), 4, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            ""
        }
        titles.addView(TextView(this).apply {
            text = "версия $ver"
            textSize = 12f
            setTextColor(Ui.DIM)
        })
        row.addView(titles)
        root.addView(row)
        // логотип мягко «парит»
        ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y, -dp(3).toFloat(), dp(3).toFloat()).apply {
            duration = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun buildStatus() {
        val box = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dotBox = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        }
        statusRing = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER)
            alpha = 0f
        }
        statusDot = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER)
        }
        dotBox.addView(statusRing)
        dotBox.addView(statusDot)
        row.addView(dotBox)
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        statusText = TextView(this).apply {
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.TEXT)
        }
        statusHint = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.DIM)
        }
        texts.addView(statusText)
        texts.addView(statusHint)
        row.addView(texts)
        box.addView(row)
        box.addView(TextView(this).apply {
            text = "Настройки службы"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.ORANGE)
            gravity = Gravity.CENTER
            background = Ui.round(this@MainActivity, Color.TRANSPARENT, 14, Ui.ORANGE)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14) }
            val icon = this@MainActivity.getDrawable(R.drawable.ic_power)?.mutate()
            icon?.setTint(Ui.ORANGE)
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
            compoundDrawablePadding = dp(8)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
    }

    private fun buildTuning() {
        val c = card("Комиссия и допуск")
        fee = field(c, "Комиссия, %", fmt(p.fee))
        feeSeg = segment(c, "Из какой области вычитать комиссию", listOf("Область 1", "Область 2"), p.feeRegion - 1)
        modeSeg = segment(c, "Режим допустимого минуса", listOf("В процентах", "В сумме"), p.mode)
        margin = field(c, "Допустимый минус (% или сумма, по режиму)", fmt(p.margin))

        val s = card("Скорость и задержки")
        cps = field(s, "Сравнений в секунду (например 1 или 2.5)", fmt(p.checksPerSec))
        confirm = field(s, "Ждать неизменности цены, мс (0 = сразу)", p.confirmDelay.toString())
        tapDelay = field(s, "Пауза между нажатиями, мс", p.tapDelay.toString())
        digitDelay = field(s, "Пауза между цифрами (синие), мс", p.digitDelay.toString())
        cancelWait = field(s, "Пауза после отмены своего запроса, мс", p.cancelWait.toString())
        cooldown = field(s, "Кулдаун после заказа, мс", p.cooldown.toString())
        confirmCount = field(s, "Одинаковых проверок подряд перед отменой или ставкой", p.confirmCount.toString())
        holdAfterBid = field(s, "Пауза после своей ставки, мс (без отмены и повторной ставки)", p.holdAfterBid.toString())

        val o = card("Перебитие")
        outSeg = segment(o, "Какую область перебивать", listOf("Область 1", "Область 2"), p.outbidRegion - 1)
        step = field(o, "Шаг перебития", p.step.toString())
        greenSec = field(o, "Интервал зелёных точек, сек (0 = выключено)", fmt(p.greenSec))
    }

    private fun buildTimers() {
        val c = card("Кнопки с таймерами")
        c.addView(labelView("Время каждой кнопки, сек. Новые добавляются иконкой таймера на панели"))
        timersBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(timersBox)
    }

    private fun buildStats() {
        val c = card("Текущее состояние")
        statsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(statsBox)
    }

    private fun buildHelp() {
        val c = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            setOnClickListener { toggleHelp() }
        }
        head.addView(TextView(this).apply {
            text = "Как пользоваться"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Ui.TEXT)
            layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
        })
        helpChev = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron)
            imageTintList = ColorStateList.valueOf(Ui.ORANGE)
        }
        head.addView(helpChev)
        c.addView(head)
        helpBody = TextView(this).apply {
            text = iconized(HELP)
            textSize = 14f
            setTextColor(Ui.DIM)
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(14), 0, 0)
            visibility = View.GONE
        }
        c.addView(helpBody)
    }

    private fun toggleHelp() {
        helpOpen = !helpOpen
        TransitionManager.beginDelayedTransition(root, AutoTransition())
        helpBody.visibility = if (helpOpen) View.VISIBLE else View.GONE
        helpChev.animate().rotation(if (helpOpen) 180f else 0f).setDuration(260).start()
    }

    /** Заменяет метки вида [blue] на векторные иконки внутри текста */
    private fun iconized(src: String): CharSequence {
        val sb = SpannableStringBuilder(src)
        val size = dp(16)
        for ((token, res) in tokens) {
            var at = sb.indexOf(token)
            while (at >= 0) {
                val d = getDrawable(res)!!.mutate()
                d.setBounds(0, 0, size, size)
                sb.setSpan(ImageSpan(d, ImageSpan.ALIGN_BASELINE), at, at + token.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                at = sb.indexOf(token, at + token.length)
            }
        }
        return sb
    }

    // ---------- Резервная копия и пресеты ----------

    private fun buildBackup() {
        val c = card("Резервная копия и пресеты")
        backupLabel = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.DIM)
            setPadding(dp(2), 0, 0, dp(10))
        }
        c.addView(backupLabel)
        updateBackupLabel()
        c.addView(outlineButton("Выбрать файл копии") { pickBackupFile() })
        c.addView(outlineButton("Загрузить из файла") { openBackupFile() })
        presetName = field(c, "Название нового пресета", "")
        c.addView(outlineButton("Сохранить текущие настройки как пресет") {
            val name = presetName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this@MainActivity, "Введите название пресета", Toast.LENGTH_SHORT).show()
                return@outlineButton
            }
            save()
            Backup.savePreset(this@MainActivity, name)
            presetName.setText("")
            buildPresets()
            Toast.makeText(this@MainActivity, "Пресет «$name» сохранён", Toast.LENGTH_SHORT).show()
        })
        presetsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(presetsBox)
        buildPresets()
    }

    private fun updateBackupLabel() {
        val uri = Backup.backupUri(this)
        backupLabel.text = if (uri == null) {
            "Копия не выбрана. Выберите файл, например в Загрузках: после этого настройки сохраняются в него сами."
        } else {
            "Копия сохраняется в файл: ${uri.lastPathSegment ?: "выбран"}"
        }
    }

    private fun buildPresets() {
        presetsBox.removeAllViews()
        val names = Backup.presetNames(this)
        if (names.isEmpty()) {
            presetsBox.addView(labelView("Пресетов пока нет"))
            return
        }
        names.forEach { name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.round(this@MainActivity, Ui.SURFACE2, 14)
                setPadding(dp(12), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(8) }
            }
            row.addView(TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(Ui.TEXT)
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            })
            row.addView(TextView(this).apply {
                text = "Загрузить"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Ui.ORANGE)
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener {
                    skipSaveOnce = true
                    if (Backup.loadPreset(this@MainActivity, name)) {
                        recreate()
                    } else {
                        skipSaveOnce = false
                    }
                }
            })
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_trash)
                imageTintList = ColorStateList.valueOf(Ui.RED)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    Backup.deletePreset(this@MainActivity, name)
                    buildPresets()
                    Backup.autoWrite(this@MainActivity)
                }
            })
            presetsBox.addView(row)
        }
    }

    private fun outlineButton(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Ui.ORANGE)
        gravity = Gravity.CENTER
        background = Ui.round(this@MainActivity, Color.TRANSPARENT, 14, Ui.ORANGE)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(10) }
        setOnClickListener { onClick() }
    }

    private fun pickBackupFile() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "autoclicker_backup.json")
        }, REQ_CREATE)
    }

    private fun openBackupFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }, REQ_OPEN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        when (requestCode) {
            REQ_CREATE -> {
                try { contentResolver.takePersistableUriPermission(uri, flags) } catch (e: Exception) { }
                Backup.setBackupUri(this, uri)
                save()
                updateBackupLabel()
                Toast.makeText(this, "Копия будет сохраняться в выбранный файл", Toast.LENGTH_SHORT).show()
            }
            REQ_OPEN -> {
                val text = Backup.readText(this, uri)
                if (text == null || !Backup.importText(this, text)) {
                    Toast.makeText(this, "Файл не похож на копию АвтоКликера", Toast.LENGTH_SHORT).show()
                    return
                }
                try { contentResolver.takePersistableUriPermission(uri, flags) } catch (e: Exception) { }
                Backup.setBackupUri(this, uri)
                skipSaveOnce = true
                recreate()
            }
        }
    }

    // ---------- Конструкторы элементов ----------

    private fun card(title: String? = null): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.round(this@MainActivity, Ui.SURFACE, 22, Ui.BORDER)
            setPadding(dp(18), dp(18), dp(18), dp(18))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { topMargin = dp(14) }
        }
        if (title != null) c.addView(sectionTitle(title))
        root.addView(c)
        return c
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t.uppercase()
        textSize = 11f
        letterSpacing = 0.12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Ui.ORANGE)
        setPadding(0, 0, 0, dp(12))
    }

    private fun labelView(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(Ui.DIM)
        setPadding(dp(2), 0, 0, dp(6))
    }

    private fun field(c: LinearLayout, label: String, value: String): EditText {
        c.addView(labelView(label))
        val e = EditText(this).apply {
            setText(value)
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.DIM)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = Ui.round(this@MainActivity, Ui.SURFACE2, 12)
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(12) }
            // при фокусе обводка становится оранжевой
            setOnFocusChangeListener { v, has ->
                v.background = Ui.round(this@MainActivity, Ui.SURFACE2, 12, if (has) Ui.ORANGE else null)
            }
        }
        c.addView(e)
        return e
    }

    /** Переключатель из двух кнопок. Выбранный пункт в tag строки (индекс) */
    private fun segment(c: LinearLayout, label: String, options: List<String>, initial: Int): LinearLayout {
        c.addView(labelView(label))
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = Ui.round(this@MainActivity, Ui.SURFACE2, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(12) }
            tag = initial
        }
        val items = mutableListOf<TextView>()
        options.forEachIndexed { i, opt ->
            val item = TextView(this).apply {
                text = opt
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(11), dp(8), dp(11))
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
                background = Ui.round(this@MainActivity, Color.TRANSPARENT, 11)
                tag = Color.TRANSPARENT
            }
            items.add(item)
            row.addView(item)
            item.setOnClickListener {
                if (row.tag == i) return@setOnClickListener
                row.tag = i
                items.forEachIndexed { j, other -> paintSeg(other, j == i) }
            }
        }
        items.forEachIndexed { j, v -> paintSeg(v, j == initial, animate = false) }
        c.addView(row)
        return row
    }

    /** Плавно меняет фон выбранного пункта переключателя */
    private fun paintSeg(v: TextView, on: Boolean, animate: Boolean = true) {
        val to = if (on) Ui.ORANGE else Color.TRANSPARENT
        val from = (v.tag as? Int) ?: to
        v.tag = to
        v.setTextColor(if (on) Color.BLACK else Ui.DIM)
        v.typeface = if (on) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val bg = v.background as GradientDrawable
        if (!animate) {
            bg.setColor(to)
            return
        }
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = 240
            addUpdateListener { bg.setColor(it.animatedValue as Int) }
            start()
        }
    }

    private fun primaryButton(t: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = t
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        background = Ui.round(this@MainActivity, Ui.ORANGE, 18)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        setOnClickListener { v ->
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
            onClick()
        }
    }

    // ---------- Состояние и таймеры ----------

    private fun applyServiceState(on: Boolean) {
        val color = if (on) Ui.GREEN else Ui.RED
        statusText.text = if (on) "Служба включена" else "Служба выключена"
        statusHint.text = if (on) "Готово. Панель появится в нужном приложении"
        else "Включите службу в спец. возможностях"
        statusDot.background = Ui.oval(color)
        statusRing.background = Ui.ring(this, color)
        pulseSet?.cancel()
        if (on) {
            val sx = ObjectAnimator.ofFloat(statusRing, View.SCALE_X, 1f, 2.2f)
            val sy = ObjectAnimator.ofFloat(statusRing, View.SCALE_Y, 1f, 2.2f)
            val al = ObjectAnimator.ofFloat(statusRing, View.ALPHA, 0.9f, 0f)
            listOf(sx, sy, al).forEach {
                it.duration = 1500
                it.repeatCount = ValueAnimator.INFINITE
                it.repeatMode = ValueAnimator.RESTART
            }
            pulseSet = AnimatorSet().apply {
                playTogether(sx, sy, al)
                start()
            }
        } else {
            statusRing.alpha = 0f
            statusRing.scaleX = 1f
            statusRing.scaleY = 1f
        }
    }

    private fun buildTimerRows() {
        timersBox.removeAllViews()
        timerFields.clear()
        timerList = p.getTimers()
        if (timerList.isEmpty()) {
            timersBox.addView(TextView(this).apply {
                text = "Пока пусто. Добавьте кнопку таймера иконкой на панели."
                textSize = 13f
                setTextColor(Ui.DIM)
            })
        }
        timerList.forEachIndexed { i, t ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = Ui.round(this@MainActivity, Ui.SURFACE2, 14)
                setPadding(dp(12), dp(6), dp(6), dp(6))
                layoutParams = LinearLayout.LayoutParams(MP, WC).apply { bottomMargin = dp(8) }
            }
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_dot_timer)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26))
            })
            row.addView(TextView(this).apply {
                text = "Таймер ${i + 1}"
                textSize = 15f
                setTextColor(Ui.TEXT)
                setPadding(dp(10), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(0, WC, 1f)
            })
            val e = EditText(this).apply {
                setText(fmt(t.sec))
                setTextColor(Ui.ORANGE)
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                background = Ui.round(this@MainActivity, Color.TRANSPARENT, 10, Ui.BORDER)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(dp(84), WC)
            }
            row.addView(e)
            row.addView(TextView(this).apply {
                text = "с"
                textSize = 14f
                setTextColor(Ui.DIM)
                setPadding(dp(6), 0, dp(4), 0)
            })
            row.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_trash)
                imageTintList = ColorStateList.valueOf(Ui.RED)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener {
                    saveTimers()
                    timerList.removeAt(i)
                    p.setTimers(timerList)
                    buildTimerRows()
                    fillStats()
                }
            })
            timersBox.addView(row)
            timerFields.add(e)
        }
    }

    private fun fillStats() {
        statsBox.removeAllViews()
        val cells = listOf(
            Triple("Область 1", if (p.getRect(1) != null) "есть" else "нет", p.getRect(1) != null),
            Triple("Область 2", if (p.getRect(2) != null) "есть" else "нет", p.getRect(2) != null),
            Triple("Область 3", if (p.getRect(3) != null) "есть" else "нет", p.getRect(3) != null),
            Triple("Красные", "${p.getTaps().size}", p.getTaps().isNotEmpty()),
            Triple("Синие", "${p.getDigits().count { it != null }}/10", p.getDigits().any { it != null }),
            Triple("Оранжевые", "${p.getOranges().size}", p.getOranges().isNotEmpty()),
            Triple("Зелёные", "${p.getGreens().size}", p.getGreens().isNotEmpty()),
            Triple("Розовые", "${p.getPinks().size}", p.getPinks().isNotEmpty()),
            Triple("Таймеры", "${p.getTimers().size}", p.getTimers().isNotEmpty())
        )
        cells.chunked(3).forEach { rowCells ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MP, WC)
            }
            rowCells.forEach { (label, value, on) ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = Ui.round(this@MainActivity, Ui.SURFACE2, 14)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, WC, 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    }
                }
                cell.addView(TextView(this).apply {
                    text = value
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(if (on) Ui.ORANGE else Ui.DIM)
                })
                cell.addView(TextView(this).apply {
                    text = label
                    textSize = 11f
                    setTextColor(Ui.DIM)
                })
                row.addView(cell)
            }
            statsBox.addView(row)
        }
    }

    private fun intro() {
        for (i in 0 until root.childCount) {
            val c = root.getChildAt(i)
            c.alpha = 0f
            c.translationY = dp(20).toFloat()
            c.animate().alpha(1f).translationY(0f)
                .setStartDelay(60L * i)
                .setDuration(460)
                .setInterpolator(DecelerateInterpolator())
                .start()
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
        d(confirmCount)?.let { p.confirmCount = it.toInt().coerceAtLeast(1) }
        d(holdAfterBid)?.let { p.holdAfterBid = it.toInt().coerceAtLeast(0) }
        d(step)?.let { p.step = it.toInt().coerceAtLeast(1) }
        d(greenSec)?.let { p.greenSec = it.coerceAtLeast(0.0) }
        p.mode = modeSeg.tag as Int
        p.feeRegion = (feeSeg.tag as Int) + 1
        p.outbidRegion = (outSeg.tag as Int) + 1
        Backup.autoWrite(this)
    }

    override fun onPause() {
        super.onPause()
        if (skipSaveOnce) {
            skipSaveOnce = false
            return
        }
        save()
    }

    override fun onResume() {
        super.onResume()
        val en = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val on = en.contains("$packageName/") && en.contains("ClickerService")
        applyServiceState(on)
        buildTimerRows()
        fillStats()
    }
}
