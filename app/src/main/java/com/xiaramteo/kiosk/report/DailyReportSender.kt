package com.xiaramteo.kiosk.report

import android.util.Log
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.settings.MailSettings
import java.time.LocalDate

/** 발송 한 번의 결과. */
sealed interface SendOutcome {
    /** 보낼 날짜가 없었다. 실패가 아니다. */
    data object NothingToSend : SendOutcome
    data class Sent(val dates: List<LocalDate>) : SendOutcome
    data class Failed(val date: LocalDate, val cause: Throwable) : SendOutcome
    data object NotConfigured : SendOutcome
}

/**
 * 아직 보내지 않은 날짜를 오래된 순으로 보낸다.
 *
 * 하루치만 보내지 않고 밀린 날짜를 함께 처리하는 이유는, 와이파이가 끊겼거나 기기가 꺼져 있어
 * 발송을 건너뛴 날이 생기기 때문이다. 그런 날도 다음 발송에 따라 나가야 기록이 새지 않는다.
 *
 * 한 날짜라도 실패하면 거기서 멈춘다. 순서를 지켜 보내야 사서가 받는 순서도 날짜순이 되고,
 * 계정이나 네트워크 문제라면 뒤 날짜도 어차피 실패하기 때문이다.
 */
class DailyReportSender(
    private val store: ScanRecordStore,
    private val mailer: DailyReportMailer = DailyReportMailer(),
) {
    suspend fun sendPending(settings: MailSettings, today: LocalDate): SendOutcome {
        if (!settings.isSendable) return SendOutcome.NotConfigured

        val dates = store.unsentDates(upTo = today)
        if (dates.isEmpty()) return SendOutcome.NothingToSend

        val sent = mutableListOf<LocalDate>()
        for (date in dates) {
            try {
                mailer.send(settings, date, store.readRecords(date), store.csvFor(date))
                // 보낸 뒤에만 표식을 남긴다. 실패하면 다음에 다시 시도한다.
                store.markSent(date)
                sent += date
            } catch (e: Exception) {
                Log.w(TAG, "$date 발송 실패", e)
                return if (sent.isEmpty()) {
                    SendOutcome.Failed(date, e)
                } else {
                    // 일부라도 보냈으면 그건 성공으로 알리고, 남은 건 다음 발송에 맡긴다.
                    SendOutcome.Sent(sent)
                }
            }
        }
        return SendOutcome.Sent(sent)
    }

    private companion object { const val TAG = "DailyReport" }
}
