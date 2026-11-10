package com.projectxo.app.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.projectxo.app.MainActivity
import com.projectxo.app.R
import com.projectxo.app.XoApplication
import com.projectxo.app.core.model.BalanceMath
import com.projectxo.app.core.model.WaterPortions
import com.projectxo.app.core.reminders.WaterActionReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.projectxo.app.core.ui.format.formatVolume

/**
 * Виджет «Баланс дня»: кольцо с процентом и кнопка «+250 мл» (docs/PRODUCT.md).
 * Цвета — тема «Тёплая», светлая или тёмная по телефону. Обновляется при каждом изменении дня в приложении
 * и раз в 30 минут системой (новый день подхватывается без запуска приложения).
 */
class DayRingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val container = (context.applicationContext as XoApplication).container
        container.appScope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Перерисовать все виджеты. Ничего не делает, если ни одного не добавлено. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DayRingWidget::class.java))
            if (ids.isEmpty()) return
            val container = (context.applicationContext as XoApplication).container
            val today = container.clock.today()
            val settings = container.settings.settings.value
            val totals = container.history.totalsBetween(today, today).first()[today]
            val goals = container.history.goals().first()
            val percent = Math.round(BalanceMath.balance(totals, goals, settings.modules) * 100)
            manager.updateAppWidget(ids, views(context, percent))
        }

        internal fun views(context: Context, percent: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_day_ring)
            rv.setImageViewBitmap(R.id.widget_ring, ring(context, percent))
            rv.setContentDescription(R.id.widget_ring, context.applicationContext.getString(R.string.widget_ring_a11y, percent.toString()))
            val unit = (context.applicationContext as XoApplication).container.settings.settings.value.units.volume
            val ml = formatVolume(context, WaterPortions.primaryMl(unit).toLong(), unit)
            rv.setTextViewText(R.id.widget_add, context.applicationContext.getString(R.string.notif_water_action, ml))
            rv.setContentDescription(R.id.widget_add, context.applicationContext.getString(R.string.widget_add_a11y, ml))
            val open = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val add = PendingIntent.getBroadcast(
                context, 11, Intent(context, WaterActionReceiver::class.java).setAction(WaterActionReceiver.ACTION_ADD_WATER),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            rv.setOnClickPendingIntent(R.id.widget_ring, open)
            rv.setOnClickPendingIntent(R.id.widget_root, open)
            rv.setOnClickPendingIntent(R.id.widget_add, add)
            return rv
        }

        /** Кольцо с процентом в центре. Рисуется в картинку: так цвета и толщина совпадают с приложением. */
        private fun ring(context: Context, percent: Int): Bitmap {
            val size = RING_PX
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val stroke = size * 0.085f
            val box = RectF(stroke / 2f, stroke / 2f, size - stroke / 2f, size - stroke / 2f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
            paint.color = ContextCompat.getColor(context, R.color.xo_widget_track)
            c.drawArc(box, 0f, 360f, false, paint)
            if (percent > 0) {
                paint.color = ContextCompat.getColor(context, R.color.xo_widget_accent)
                c.drawArc(box, -90f, 360f * percent.coerceIn(0, 100) / 100f, false, paint)
            }
            val ink = ContextCompat.getColor(context, R.color.xo_widget_ink)
            val muted = ContextCompat.getColor(context, R.color.xo_widget_muted)
            val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink; textSize = size * 0.26f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); textAlign = Paint.Align.CENTER
            }
            val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = muted; textSize = size * 0.09f; typeface = Typeface.create("sans-serif", Typeface.NORMAL); textAlign = Paint.Align.CENTER
            }
            val cx = size / 2f
            c.drawText(percent.toString(), cx - size * 0.04f, size * 0.54f, num)
            // «%» поменьше и приглушённо, справа от числа.
            val unit = Paint(label).apply { textSize = size * 0.12f; textAlign = Paint.Align.LEFT }
            c.drawText("%", cx + num.measureText(percent.toString()) / 2f - size * 0.02f, size * 0.54f, unit)
            c.drawText(context.applicationContext.getString(R.string.day_balance), cx, size * 0.68f, label)
            return bmp
        }

        private const val RING_PX = 360
    }
}
