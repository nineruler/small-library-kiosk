package com.xiaramteo.kiosk.report

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xiaramteo.kiosk.KioskApplication
import com.xiaramteo.kiosk.settings.MailConfigResult
import com.xiaramteo.kiosk.settings.MailSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 하루치 기록을 메일로 보내는 작업.
 *
 * 15분마다 깨어나 "지금 보낼 때가 되었는지"를 스스로 판단한다. WorkManager 의 주기 작업에
 * 시각을 맡기지 않는 이유가 있다 — 주기만 주고 flex 를 주지 않으면 창(window)이 주기 전체가
 * 되어, 하루 주기로 걸면 24시간 중 아무 때나 돌 수 있다. 실제로 18시로 걸어 둔 메일이
 * 다음 날 00:42 에 도착했다. 그래서 시각 판단은 [sendableUpTo] 가 하고, WorkManager 에는
 * "자주 깨워 달라"고만 맡긴다.
 */
class DailyReportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KioskApplication
        // 설정 파일이 바뀌었을 수 있으니 돌 때마다 다시 읽는다.
        val settings = when (val config = app.mailConfigLoader.load()) {
            is MailConfigResult.Loaded -> config.settings
            is MailConfigResult.Missing -> {
                Log.i(TAG, "설정 파일이 없어 발송하지 않음: ${config.expectedPath}")
                return Result.success()
            }
            is MailConfigResult.Invalid -> {
                Log.w(TAG, "설정 파일에 문제가 있어 발송하지 않음: ${config.reason}")
                return Result.success()
            }
        }

        val upTo = sendableUpTo(settings, LocalDateTime.now())
        return when (val outcome = app.reportSender.sendPending(settings, upTo)) {
            is SendOutcome.Sent -> {
                Log.i(TAG, "발송 완료: ${outcome.dates.joinToString()}")
                Result.success()
            }
            SendOutcome.NothingToSend -> Result.success()
            SendOutcome.NotConfigured -> {
                Log.i(TAG, "메일 설정이 아직 없어 발송하지 않음")
                Result.success()
            }
            is SendOutcome.Failed -> {
                // 네트워크나 일시적 장애일 수 있으므로 WorkManager 에 재시도를 맡긴다.
                Log.w(TAG, "발송 실패, 재시도 예정", outcome.cause)
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "DailyReport"
        private const val WORK_NAME = "daily-report-mail"

        /** WorkManager 가 창 안 아무 때나 돌려도 되도록, 자주 깨우기만 한다. */
        private val CHECK_EVERY: Duration = Duration.ofMinutes(15)

        /**
         * 보낼 때가 되었는지 주기적으로 확인하도록 예약한다.
         *
         * 발송 시각은 설정 파일에 있고 작업이 돌 때마다 다시 읽으므로, 예약 자체는
         * 시각을 모른다. 설정을 바꿔도 다시 예약할 필요가 없다.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyReportWorker>(CHECK_EVERY)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * 지금 보내도 되는 마지막 날짜.
         *
         * 오늘 치는 [MailSettings.sendAt] 이 지나야 보낸다. 그 전에 보내면 아직 더 빌려 갈
         * 사람이 남은 하루를 반쪽만 보내게 된다. 지난 날짜는 시각과 상관없이 보낸다 —
         * 기기가 꺼져 있어 거른 날이 있으면 다음에 깨어났을 때 바로 나가야 한다.
         */
        fun sendableUpTo(
            settings: MailSettings,
            now: LocalDateTime = LocalDateTime.now(),
        ): LocalDate = if (now.toLocalTime() < settings.sendAt) {
            now.toLocalDate().minusDays(1)
        } else {
            now.toLocalDate()
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
