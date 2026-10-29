package ru.fermocontrol.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object ReminderScheduler {
    fun areRemindersEnabled(context: Context): Boolean {
        return context.getSharedPreferences("fermocontrol_reminders", Context.MODE_PRIVATE)
            .getBoolean("reminders_enabled", true)
    }

    fun setRemindersEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("fermocontrol_reminders", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("reminders_enabled", enabled)
            .apply()
    }
    private const val PREFS_NAME = "fermocontrol_reminder_prefs"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_DEFAULT_INTERVAL_DAYS = "default_interval_days"
    private const val KEY_LAST_NOTIFIED_DATE = "last_notified_day"

    const val ACTION_CHECK_REMINDERS = "ru.fermocontrol.app.ACTION_CHECK_REMINDERS"
    const val EXTRA_PRODUCT_ID = "extra_open_product_id"
    private const val CHANNEL_ID = "fermocontrol_weighing_reminders"
    private const val NOTIFICATION_ID = 4201
    private const val ALARM_REQUEST_CODE = 4200

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
        if (enabled) {
            schedulePeriodicCheck(context)
        } else {
            cancelScheduledCheck(context)
        }
    }

    fun getDefaultIntervalDays(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_DEFAULT_INTERVAL_DAYS, 3)
            .coerceIn(1, 30)

    fun setDefaultIntervalDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DEFAULT_INTERVAL_DAYS, days.coerceIn(1, 30))
            .apply()
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun schedulePeriodicCheck(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = buildCheckPendingIntent(appContext)
        val intervalMs = AlarmManager.INTERVAL_HALF_DAY
        val triggerAt = SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_HOUR * 4
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            intervalMs,
            pending
        )
    }

    fun cancelScheduledCheck(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(buildCheckPendingIntent(appContext))
    }

    private fun buildCheckPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_CHECK_REMINDERS
        }
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun checkAndNotifyIfNeeded(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext) && !force) return
        if (!hasNotificationPermission(appContext)) return

        val products = runCatching { ProductStore(appContext).load() }.getOrNull().orEmpty()
        val nowMs = System.currentTimeMillis()
        val dueProducts = products.filter { needsWeighing(it, nowMs = nowMs) }
        val reachedProducts = products.filter { isTargetReached(it) }

        if (dueProducts.isEmpty() && reachedProducts.isEmpty()) return

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val todayBucket = nowMs / (12 * 3_600_000L)
        if (!force && prefs.getLong(KEY_LAST_NOTIFIED_DATE, -1L) == todayBucket) {
            return
        }
        prefs.edit().putLong(KEY_LAST_NOTIFIED_DATE, todayBucket).apply()

        ensureChannel(appContext)

        val targetProduct = dueProducts.firstOrNull() ?: reachedProducts.firstOrNull() ?: return
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_PRODUCT_ID, targetProduct.id)
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            targetProduct.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when {
            dueProducts.isNotEmpty() && reachedProducts.isNotEmpty() ->
                "Пора взвесить (${dueProducts.size}) · Цель достигнута (${reachedProducts.size})"
            dueProducts.size == 1 ->
                "Пора взвесить №${targetProduct.number} «${targetProduct.name}»"
            dueProducts.size > 1 ->
                "Ждут взвешивания: ${dueProducts.size} партий"
            else ->
                "Достигнута цель по весу: №${targetProduct.number} «${targetProduct.name}»"
        }

        val body = buildString {
            if (dueProducts.isNotEmpty()) {
                append("К проверке: ")
                append(dueProducts.take(3).joinToString { "№${it.number} ${it.name}" })
                if (dueProducts.size > 3) append(" и ещё ${dueProducts.size - 3}")
            }
            if (reachedProducts.isNotEmpty()) {
                if (isNotEmpty()) append(". ")
                append("Готовы по весу: ")
                append(reachedProducts.take(2).joinToString { "№${it.number} ${it.name}" })
            }
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Напоминания о взвешивании",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Напоминания о плановом контроле веса и достижении цели усушки"
            }
            manager.createNotificationChannel(channel)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderScheduler.schedulePeriodicCheck(context)
        ReminderScheduler.checkAndNotifyIfNeeded(context, force = false)
    }
}
