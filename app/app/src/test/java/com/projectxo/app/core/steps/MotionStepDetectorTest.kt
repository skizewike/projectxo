package com.projectxo.app.core.steps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MotionStepDetectorTest {
    private val g = 9.81f
    /** Прогнать сигнал: [accel] — добавка к силе тяжести по вертикали в момент t (с). */
    private fun run(seconds: Double, noise: Float = 0.15f, seed: Int = 1, rateHz: Int = 50, accel: (Double) -> Double): Int {
        val d = MotionStepDetector()
        val rnd = Random(seed)
        var total = 0
        val n = (seconds * rateHz).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / rateHz
            val z = g + accel(t).toFloat() + (rnd.nextFloat() - 0.5f) * 2 * noise
            val x = (rnd.nextFloat() - 0.5f) * 2 * noise
            total += d.onSample((t * 1e9).toLong(), x, 0.3f, z)
        }
        return total
    }

    @Test
    fun `steady walk at 2 steps per second counts about every step`() {
        val steps = run(30.0) { t -> 3.0 * sin(2 * PI * 2.0 * t) }
        // 60 шагов; допускаем потерю первого пика, пока фильтр входит в режим.
        assertTrue("got $steps", steps in 57..61)
    }

    @Test
    fun `slow walk is counted`() {
        val steps = run(30.0) { t -> 1.8 * sin(2 * PI * 1.4 * t) }
        assertTrue("got $steps", steps in 39..43)
    }

    @Test
    fun `phone lying on the table counts nothing`() {
        assertEquals(0, run(60.0, noise = 0.3f) { 0.0 })
    }

    @Test
    fun `a few bumps do not become steps`() {
        // Телефон взяли и положили: три толчка.
        val steps = run(10.0) { t -> if (t in 2.0..2.1 || t in 2.6..2.7 || t in 3.2..3.3) 6.0 else 0.0 }
        assertEquals(0, steps)
    }

    @Test
    fun `fast shaking is not walking`() {
        // 8 Гц — быстрее любого шага.
        val steps = run(10.0) { t -> 4.0 * sin(2 * PI * 8.0 * t) }
        assertEquals(0, steps)
    }

    @Test
    fun `walk, pause, walk again counts both parts`() {
        val steps = run(40.0) { t -> if (t < 15.0 || t > 25.0) 3.0 * sin(2 * PI * 2.0 * t) else 0.0 }
        // 30 + 30 шагов.
        assertTrue("got $steps", steps in 56..61)
    }

    @Test
    fun `walk is counted on phones with fast and slow accelerometers`() {
        // Частоту задаёт телефон, не приложение: от 15 до 200 показаний в секунду.
        for (rate in listOf(15, 100, 200)) {
            val steps = run(30.0, rateHz = rate) { t -> 3.0 * sin(2 * PI * 2.0 * t) }
            assertTrue("rate $rate: got $steps", steps in 57..61)
        }
    }
}
