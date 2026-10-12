package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.MailSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class DailyReportWorkerScheduleTest {

    private val at18 = MailSettings(sendAt = LocalTime.of(18, 0))

    @Test
    fun `발송 시각 전이면 오늘 그 시각까지 기다린다`() {
        val delay = DailyReportWorker.initialDelay(
            at18,
            now = LocalDateTime.of(2026, 10, 12, 9, 30),
        )
        assertEquals(Duration.ofHours(8).plusMinutes(30), delay)
    }

    @Test
    fun `발송 시각이 지났으면 다음 날로 넘긴다`() {
        val delay = DailyReportWorker.initialDelay(
            at18,
            now = LocalDateTime.of(2026, 10, 12, 20, 0),
        )
        assertEquals(Duration.ofHours(22), delay)
    }

    @Test
    fun `정각에 걸리면 바로 보내지 않고 다음 날로 넘긴다`() {
        // 이미 오늘 발송이 돌았을 시점이라 중복 발송을 피한다.
        val delay = DailyReportWorker.initialDelay(
            at18,
            now = LocalDateTime.of(2026, 10, 12, 18, 0),
        )
        assertEquals(Duration.ofDays(1), delay)
    }
}
