package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.MailSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 작업은 15분마다 깨어나므로, "지금 어디까지 보내도 되는가"를 스스로 판단해야 한다.
 * 이 판단이 틀리면 아직 끝나지 않은 하루를 반쪽만 보내거나, 거른 날이 계속 밀린다.
 */
class DailyReportWorkerScheduleTest {

    private val at18 = MailSettings(sendAt = LocalTime.of(18, 0))

    private fun upTo(hour: Int, minute: Int = 0): LocalDate =
        DailyReportWorker.sendableUpTo(at18, LocalDateTime.of(2026, 10, 12, hour, minute))

    @Test
    fun `발송 시각 전에는 오늘 치를 보내지 않는다`() {
        // 아직 더 빌려 갈 사람이 남아 있다. 어제까지만 보낸다.
        assertEquals(LocalDate.of(2026, 10, 11), upTo(9, 30))
        assertEquals(LocalDate.of(2026, 10, 11), upTo(17, 59))
    }

    @Test
    fun `발송 시각이 되면 오늘 치까지 보낸다`() {
        assertEquals(LocalDate.of(2026, 10, 12), upTo(18, 0))
        assertEquals(LocalDate.of(2026, 10, 12), upTo(23, 59))
    }

    @Test
    fun `자정을 넘기면 다시 전날까지가 된다`() {
        // 00시 42분에 깨어나도 11일·12일은 이미 보냈고, 13일은 아직 열지도 않았다.
        assertEquals(LocalDate.of(2026, 10, 11), upTo(0, 42))
    }

    @Test
    fun `발송 시각을 바꾸면 경계도 함께 움직인다`() {
        val at20 = MailSettings(sendAt = LocalTime.of(20, 0))
        val evening = LocalDateTime.of(2026, 10, 12, 19, 0)
        assertEquals(LocalDate.of(2026, 10, 11), DailyReportWorker.sendableUpTo(at20, evening))
        assertEquals(
            LocalDate.of(2026, 10, 12),
            DailyReportWorker.sendableUpTo(at20, evening.plusHours(1)),
        )
    }
}
