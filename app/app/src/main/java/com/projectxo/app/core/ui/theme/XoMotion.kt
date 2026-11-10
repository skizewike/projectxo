package com.projectxo.app.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring

/** Общие токены движения для Compose (docs/DESIGN.md, раздел 5). Тайминги заставки: core/motion/SplashTimeline.kt. */
object XoMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val FastOutSlowIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    /** Ход линии «Скана» в «Еде по фото»: плавный разгон и мягкий упор. */
    val Scan = CubicBezierEasing(0.45f, 0f, 0.2f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val MICRO_MS = 150
    const val STATE_MS = 280
    const val SCREEN_MS = 400

    /** Подтягивание дуги заставки к реальной доле загрузки. */
    const val LOAD_FOLLOW_MS = 200

    /** Перекатка цифр (DESIGN.md, 5b п. 2): лёгкий перелёт ~4 %. */
    val Roll = CubicBezierEasing(0.2f, 1.25f, 0.4f, 1f)
    const val ROLL_MS = 420
    const val ROLL_FADE_MS = 140
    const val ROLL_STAGGER_MS = 45

    /** Циферблат (5b п. 3 и 4). */
    const val DIAL_STEP_MS = 8
    const val DIAL_TICK_MS = 200
    const val SEAL_MS = 900
    const val SEAL_WIDTH = 0.12f

    /** Масштаб нажатой кнопки. */
    const val PRESS_SCALE = 0.96f

    const val LIST_STAGGER_MS = 40
    const val LIST_STAGGER_MAX = 6

    fun <T> springTouch() = spring<T>(dampingRatio = 0.85f, stiffness = 400f)
    fun <T> springSettle() = spring<T>(dampingRatio = 1f, stiffness = 200f)
    fun <T> springScreen() = spring<T>(dampingRatio = 0.9f, stiffness = 300f)
}
