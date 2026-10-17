package uz.reaktivombor.notify

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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import uz.reaktivombor.R
import uz.reaktivombor.data.AppDatabase
import uz.reaktivombor.data.Reagent
import uz.reaktivombor.domain.AlertStage
import uz.reaktivombor.domain.daysUntil
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Muddat yaqinlashganda telefonga keladigan eslatmalar. */
object ExpiryNotifications {
    private const val CHANNEL_ID = "expiry"
    private const val WORK_NAME = "expiry-check-daily"

    /** 1.0.0 versiyadagi davriy ish (vaqt o‘tishi bilan 9:00 dan surilib ketardi). */
    private const val LEGACY_PERIODIC_WORK = "expiry-check"

    /** Har kuni shu soatda tekshiriladi. */
    const val CHECK_HOUR = 9

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Yaroqlilik muddati", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Reaktiv sariq yoki qizilga o‘tganda yoki muddati tugaganda xabar beradi"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Ilova ochilganda: navbatdagi tekshiruv rejalashtirilmagan bo‘lsa — ertangi (yoki bugungi) 9:00 ga. */
    fun schedule(context: Context) {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LEGACY_PERIODIC_WORK)
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, nextCheck())
    }

    /** Tekshiruv ishlayotganda keyingisini zanjirga qo‘shadi — har kuni aynan 9:00 atrofida, surilmasdan. */
    fun scheduleFollowing(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, nextCheck())
    }

    private fun nextCheck(now: LocalDateTime = LocalDateTime.now()): OneTimeWorkRequest {
        var next = now.toLocalDate().atTime(CHECK_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return OneTimeWorkRequestBuilder<ExpiryCheckWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
    }

    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun describe(stage: AlertStage, days: Long): String = when (stage) {
        AlertStage.EXPIRED -> "muddati o‘tdi"
        AlertStage.RED -> "1 oydan kam qoldi ($days kun)"
        AlertStage.YELLOW -> "1–3 oy qoldi ($days kun)"
        AlertStage.NONE -> "$days kun qoldi"
    }

    fun show(context: Context, alerts: List<Pair<Reagent, AlertStage>>, today: LocalDate) {
        if (alerts.isEmpty() || !canNotify(context)) return

        val sorted = alerts.sortedWith(compareByDescending<Pair<Reagent, AlertStage>> { it.second.ordinal }.thenBy { it.first.expiresAt })
        val lines = sorted.map { (r, stage) -> "${r.name} — ${describe(stage, daysUntil(r.expiresAt, today))}" }

        val title = if (sorted.size == 1) {
            "«${sorted[0].first.name}»: ${describe(sorted[0].second, daysUntil(sorted[0].first.expiresAt, today))}"
        } else {
            "Muddat bo‘yicha ogohlantirish: ${sorted.size} ta reaktiv"
        }

        // Ikonka bosilgandek ochiladi: ilova ochiq bo‘lsa qayta yaratilmaydi (yozilayotgan forma yo‘qolmaydi).
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val openApp = launch?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val style = NotificationCompat.InboxStyle().also { inbox -> lines.take(7).forEach(inbox::addLine) }
        if (lines.size > 7) style.setSummaryText("yana ${lines.size - 7} ta")

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_flask)
            .setContentTitle(title)
            .setContentText(if (sorted.size == 1) "Ro‘yxatni ochish uchun bosing" else lines.first())
            .setStyle(style)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            // Har kun uchun alohida: ko‘rilmagan kechagi eslatma bugungisi bilan almashib ketmaydi.
            NotificationManagerCompat.from(context).notify(today.toEpochDay().toInt(), notification)
        } catch (e: SecurityException) {
            // Ruxsat shu orada olib qo‘yilgan — keyingi safar yana urinamiz.
        }
    }
}

/**
 * Har kuni ertalab ishga tushadi: reaktiv yangi bosqichga o‘tgan bo‘lsa (yashil → sariq → qizil → o‘tgan)
 * bitta umumiy eslatma yuboradi. Bir bosqich haqida faqat bir marta xabar beriladi.
 */
class ExpiryCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ExpiryNotifications.scheduleFollowing(applicationContext)

        val dao = AppDatabase.get(applicationContext).reagentDao()
        val today = LocalDate.now()
        val canNotify = ExpiryNotifications.canNotify(applicationContext)
        val alerts = mutableListOf<Pair<Reagent, AlertStage>>()

        for (reagent in dao.getAll()) {
            val stage = AlertStage.of(reagent.expiresAt, today)
            when {
                stage.ordinal > reagent.notifiedStage -> {
                    // Eslatma o‘chirilgan bo‘lsa, bosqichni saqlamaymiz — ruxsat berilgach xabar keladi.
                    if (canNotify) {
                        alerts += reagent to stage
                        dao.setNotifiedStage(reagent.id, stage.ordinal)
                    }
                }
                // Muddat tahrirlanib uzaytirilgan — keyingi o‘tishlar uchun pastga tushiramiz.
                stage.ordinal < reagent.notifiedStage -> dao.setNotifiedStage(reagent.id, stage.ordinal)
            }
        }

        ExpiryNotifications.show(applicationContext, alerts, today)
        return Result.success()
    }
}
