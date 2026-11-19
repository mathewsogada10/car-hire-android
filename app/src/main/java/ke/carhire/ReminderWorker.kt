package ke.carhire

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import ke.carhire.domain.model.SETTINGS_ID
import ke.carhire.domain.rules.Rules
import ke.carhire.util.amount
import ke.carhire.util.flag
import ke.carhire.util.now
import ke.carhire.util.text

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val dao = (applicationContext as CarHireApp).repository.dao
        val settings = dao.find(SETTINGS_ID)?.data() ?: return Result.success()
        if (!settings.flag("remindersEnabled")) return Result.success()
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            dao.meta(Meta("reminderError", "Notification permission is not granted"))
            return Result.success()
        }
        val time = ZonedDateTime.now(ZoneId.of("Africa/Nairobi"))
        if (time.toLocalTime() < LocalTime.parse(settings.text("reminderTime")))
            return Result.success()
        if (Build.VERSION.SDK_INT >= 26)
            applicationContext
                .getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "reminders",
                        "Car Hire reminders",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    )
                )
        val rows = dao.all().filter { it.deletedAt == null }
        val today = time.toLocalDate()
        val lead = settings.amount("reminderLeadDays")
        val due = Rules.dueReminders(rows, today, lead)
        val notifications = due.toMutableList()
        val backupDate =
            dao.meta("lastBackup")?.value?.let {
                Instant.parse(it).atZone(ZoneId.of("Africa/Nairobi")).toLocalDate()
            }
        if (
            backupDate == null ||
                !backupDate.plusDays(settings.amount("backupReminderDays")).isAfter(today)
        )
            notifications.add(
                LocalRecord("20000000-0000-4000-8000-000000000001", "reminders", payload = "{}")
            )
        for (r in notifications) {
            val key = "notified:${r.id}:$today"
            if (dao.meta(key) != null) continue
            val open =
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    android.content.Intent(applicationContext, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat.Builder(applicationContext, "reminders")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Car Hire reminder due")
                    .setContentText("Open the app to view due reminders.")
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            NotificationManagerCompat.from(applicationContext).notify(r.id.hashCode(), notification)
            dao.meta(Meta(key, now()))
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "carhire-reminders",
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build(),
                )
        }
    }
}
