package com.example.autoclicker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Холст заставки: кольцо-прицел, курсор, клик, буквы названия, полоска загрузки */
class SplashView(c: Context) : View(c) {
    private val density = c.resources.displayMetrics.density
    private fun d(v: Float): Float = v * density
    private fun al(v: Float): Int = (v.coerceIn(0f, 1f) * 255).toInt()

    var fadeP = 0f
        set(v) { field = v; invalidate() }
    var ringP = 0f
        set(v) { field = v; invalidate() }
    var crossP = 0f
        set(v) { field = v; invalidate() }
    var cursorP = 0f
        set(v) { field = v; invalidate() }
    var tapP = 0f
        set(v) { field = v; invalidate() }
    var textP = 0f
        set(v) { field = v; invalidate() }
    var subP = 0f
        set(v) { field = v; invalidate() }
    var barP = 0f
        set(v) { field = v; invalidate() }
    var spin = 0f
        set(v) { field = v; invalidate() }

    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d(3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val dashed = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = d(1.5f)
        pathEffect = DashPathEffect(floatArrayOf(d(4f), d(10f)), 0f)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
        letterSpacing = 0.08f
    }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.22f
    }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = d(3f)
    }
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val cursorPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(0f, 22f)
        lineTo(6f, 17f)
        lineTo(10f, 26f)
        lineTo(13f, 24.5f)
        lineTo(9f, 16f)
        lineTo(16f, 16f)
        close()
    }
    private val particles = Array(26) {
        floatArrayOf(
            Random.nextFloat(),
            Random.nextFloat(),
            0.3f + Random.nextFloat() * 0.9f,
            Random.nextFloat() * 6.28f,
            Random.nextFloat()
        )
    }
    private val fullTitle = "АВТОКЛИКЕР"
    private var cx = 0f
    private var cy = 0f
    private var r = 0f
    private var glowShader: RadialGradient? = null

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        cx = w / 2f
        cy = h * 0.42f
        r = minOf(w, h) * 0.16f
        title.textSize = minOf(w, h) * 0.085f
        sub.textSize = minOf(w, h) * 0.032f
        glowShader = RadialGradient(cx, cy, r * 3.2f,
            intArrayOf(0x55FF7A1A, 0x00FF7A1A), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Ui.BG)
        val w = width.toFloat()
        val h = height.toFloat()

        // плавающие оранжевые точки
        for (pt in particles) {
            val y = ((pt[1] - spin * pt[2] * 0.2f) % 1f + 1f) % 1f
            val twinkle = 0.5f + 0.5f * sin(spin * 2f * PI.toFloat() * pt[2] + pt[3])
            fill.color = Ui.ORANGE
            fill.alpha = al(twinkle * 0.5f * fadeP)
            canvas.drawCircle(pt[0] * w, y * h, d(1f + pt[4] * 2f), fill)
        }

        // свечение за прицелом
        glow.shader = glowShader
        glow.alpha = al(fadeP)
        canvas.drawCircle(cx, cy, r * 3.2f, glow)

        // кольцо рисуется по кругу
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        ring.strokeWidth = d(3f)
        ring.color = Ui.ORANGE
        ring.alpha = al(fadeP)
        canvas.drawArc(oval, -90f, 360f * ringP, false, ring)

        // вращающийся пунктир
        canvas.save()
        canvas.rotate(spin * 360f, cx, cy)
        dashed.color = Ui.ORANGE
        dashed.alpha = al(0.35f * fadeP * ringP)
        canvas.drawCircle(cx, cy, r * 1.32f, dashed)
        canvas.restore()

        // засечки прицела и центр
        if (crossP > 0f) {
            fill.color = Ui.ORANGE
            fill.alpha = al(crossP * fadeP)
            canvas.drawCircle(cx, cy, r * 0.09f * crossP, fill)
            ring.color = Ui.ORANGE
            ring.alpha = al(0.8f * crossP * fadeP)
            ring.strokeWidth = d(2.5f)
            val inner = r * 0.55f
            val outer = r * (0.55f + 0.3f * crossP)
            canvas.drawLine(cx, cy - inner, cx, cy - outer, ring)
            canvas.drawLine(cx, cy + inner, cx, cy + outer, ring)
            canvas.drawLine(cx - inner, cy, cx - outer, cy, ring)
            canvas.drawLine(cx + inner, cy, cx + outer, cy, ring)
        }

        // волна клика
        if (tapP > 0f && tapP < 1f) {
            ring.strokeWidth = d(4f)
            ring.color = Ui.ORANGE
            ring.alpha = al((1f - tapP) * fadeP)
            canvas.drawCircle(cx, cy, r * (0.1f + 1.7f * tapP), ring)
        }

        // курсор приезжает к центру и «нажимает»
        if (cursorP > 0f) {
            val sx = cx + r * 1.7f
            val sy = cy + r * 1.5f
            val px = sx + (cx - sx) * cursorP
            val py = sy + (cy - sy) * cursorP
            val press = 1f - 0.18f * sin(tapP * PI.toFloat())
            val s = r * 0.028f * press
            cursor.alpha = al(minOf(1f, cursorP * 3f) * fadeP)
            canvas.save()
            canvas.translate(px, py)
            canvas.scale(s, s)
            canvas.drawPath(cursorPath, cursor)
            canvas.restore()
        }

        // название по буквам: АВТО белым, КЛИКЕР оранжевым
        val n = (textP * fullTitle.length).toInt().coerceIn(0, fullTitle.length)
        val p1 = fullTitle.substring(0, 4)
        val p2 = fullTitle.substring(4)
        val w1 = title.measureText(p1)
        val w2 = title.measureText(p2)
        val x0 = cx - (w1 + w2) / 2f
        val ty = cy + r * 2.1f
        if (n > 0) {
            title.color = Color.WHITE
            title.alpha = al(fadeP)
            canvas.drawText(p1.take(n), x0, ty, title)
        }
        if (n > 4) {
            title.color = Ui.ORANGE
            title.alpha = al(fadeP)
            canvas.drawText(p2.take(n - 4), x0 + w1, ty, title)
        }

        // подзаголовок
        if (subP > 0f) {
            sub.color = Ui.DIM
            sub.alpha = al(subP * fadeP)
            canvas.drawText("ПАНЕЛЬ АВТОМАТИЗАЦИИ", cx, ty + r * 0.45f, sub)
        }

        // полоска загрузки внизу
        val bw = w * 0.42f
        val by = h * 0.9f
        track.color = Ui.BORDER
        track.alpha = al(fadeP)
        canvas.drawLine(cx - bw / 2f, by, cx + bw / 2f, by, track)
        if (barP > 0f) {
            track.color = Ui.ORANGE
            track.alpha = al(fadeP)
            canvas.drawLine(cx - bw / 2f, by, cx - bw / 2f + bw * barP, by, track)
        }
    }
}

/** Заставка: проигрывает анимацию и открывает настройки. Тап по экрану пропускает */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val anims = mutableListOf<Animator>()
    private var mainSet: AnimatorSet? = null
    private var going = false
    private lateinit var scene: SplashView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        scene = SplashView(this)
        scene.setOnClickListener { go() }
        setContentView(scene)
        playIntro()
    }

    private fun tween(
        from: Float, to: Float, dur: Long, delay: Long,
        ip: TimeInterpolator, onValue: (Float) -> Unit
    ): ValueAnimator =
        ValueAnimator.ofFloat(from, to).apply {
            duration = dur
            startDelay = delay
            interpolator = ip
            addUpdateListener { onValue(it.animatedValue as Float) }
        }

    private fun playIntro() {
        val fade = tween(0f, 1f, 500, 0, LinearInterpolator()) { scene.fadeP = it }
        val ring = tween(0f, 1f, 900, 120, DecelerateInterpolator(1.5f)) { scene.ringP = it }
        val cross = tween(0f, 1f, 480, 900, OvershootInterpolator(2f)) { scene.crossP = it }
        val cursor = tween(0f, 1f, 650, 1050, DecelerateInterpolator(1.8f)) { scene.cursorP = it }
        val tap = tween(0f, 1f, 480, 1700, LinearInterpolator()) { scene.tapP = it }
        val text = tween(0f, 1f, 800, 1750, LinearInterpolator()) { scene.textP = it }
        val sub = tween(0f, 1f, 450, 2350, AccelerateDecelerateInterpolator()) { scene.subP = it }
        val bar = tween(0f, 1f, 1000, 1900, DecelerateInterpolator()) { scene.barP = it }
        val spin = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { scene.spin = it.animatedValue as Float }
        }
        val all: List<Animator> = listOf(fade, ring, cross, cursor, tap, text, sub, bar)
        anims.addAll(all)
        anims.add(spin)
        mainSet = AnimatorSet().apply {
            playTogether(*all.toTypedArray())
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    handler.postDelayed({ go() }, 450)
                }
            })
        }
        spin.start()
        mainSet?.start()
    }

    private fun go() {
        if (going) return
        going = true
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        mainSet?.cancel()
        anims.forEach { it.cancel() }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
