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

/** 설정된 시각에 하루치 기록을 메일로 보내는 작업. */
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

        return when (val outcome = app.reportSender.sendPending(settings, LocalDate.now())) {
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

        /**
         * 매일 [MailSettings.sendAt] 에 돌도록 예약한다.
         *
         * WorkManager 의 주기 작업은 정확한 시각을 보장하지 않고 창(window) 안에서 돈다.
         * 하루 한 번 보내는 보고라 몇 분 늦는 것은 문제가 되지 않고, 대신 기기가 자고 있어도
         * 깨어난 뒤 반드시 실행된다는 점이 중요하다.
         */
        fun schedule(context: Context, settings: MailSettings) {
            val request = PeriodicWorkRequestBuilder<DailyReportWorker>(Duration.ofDays(1))
                .setInitialDelay(initialDelay(settings))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                // 발송 시각이 바뀌면 새 일정으로 갈아끼워야 한다.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /** 지금부터 다음 발송 시각까지 남은 시간. */
        fun initialDelay(
            settings: MailSettings,
            now: LocalDateTime = LocalDateTime.now(),
        ): Duration {
            var next = now.toLocalDate().atTime(settings.sendAt)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
