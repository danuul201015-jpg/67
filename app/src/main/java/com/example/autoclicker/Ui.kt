package com.example.autoclicker

import android.content.Context
import android.graphics.drawable.GradientDrawable

/** Общие цвета и хелперы оформления: чёрно-оранжевая тема */
object Ui {
    val BG = 0xFF0A0A0B.toInt()
    val SURFACE = 0xFF141418.toInt()
    val SURFACE2 = 0xFF1E1E24.toInt()
    val BORDER = 0xFF2A2A33.toInt()
    val ORANGE = 0xFFFF7A1A.toInt()
    val TEXT = 0xFFF5F5F7.toInt()
    val DIM = 0xFF8A8A94.toInt()
    val GREEN = 0xFF34D27B.toInt()
    val RED = 0xFFFF4D4D.toInt()

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun round(c: Context, color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(c, radiusDp).toFloat()
            setColor(color)
            if (strokeColor != null) setStroke(dp(c, 1), strokeColor)
        }

    fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    fun ring(c: Context, color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(c, 2), color)
        }
}
