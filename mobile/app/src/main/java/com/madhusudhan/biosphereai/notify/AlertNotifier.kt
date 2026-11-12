package com.madhusudhan.biosphereai.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.madhusudhan.biosphereai.MainActivity
import com.madhusudhan.biosphereai.R
import com.madhusudhan.biosphereai.ui.AlertKind
import com.madhusudhan.biosphereai.ui.LoggedAlert

/**
 * Posts glucose alerts as system notifications. Lows go to a high-importance channel
 * (sound and heads-up), highs to a default one. Each group reuses one notification id,
 * so a newer alert replaces the older one instead of stacking.
 */
class AlertNotifier(private val context: Context) {

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_URGENT, "Low glucose alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent low, low and predicted low"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HIGH, "High glucose alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "High and predicted high"
            },
        )
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun post(alert: LoggedAlert) {
        if (!canPost()) return
        val isLow = alert.kind in setOf(AlertKind.URGENT_LOW, AlertKind.LOW, AlertKind.LOW_SOON)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, if (isLow) CHANNEL_URGENT else CHANNEL_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${alert.title} · ${alert.valueText}")
            .setContentText("${alert.detail}. Simulated patient ${alert.patient}.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.detail}. Simulated patient ${alert.patient}, ${alert.clock}."))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (isLow) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(if (isLow) ID_LOW else ID_HIGH, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    companion object {
        private const val CHANNEL_URGENT = "glucose_low"
        private const val CHANNEL_HIGH = "glucose_high"
        private const val ID_LOW = 1
        private const val ID_HIGH = 2
    }
}
