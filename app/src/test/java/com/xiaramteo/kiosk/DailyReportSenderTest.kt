package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.report.DailyReportMailer
import com.xiaramteo.kiosk.report.DailyReportSender
import com.xiaramteo.kiosk.report.SendOutcome
import com.xiaramteo.kiosk.settings.MailSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReportSenderTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 10, 12)

    private val settings = MailSettings(
        username = "kiosk@example.com",
        password = "apppassword",
        recipients = listOf("a@example.com", "b@example.com"),
        enabled = true,
    )

    /** 실제 SMTP 대신 호출만 기록하는 대역. [failOn] 날짜에서는 실패한다. */
    private class FakeMailer(private val failOn: LocalDate? = null) : DailyReportMailer() {
        val sentDates = mutableListOf<LocalDate>()
        override suspend fun send(
            settings: MailSettings,
            date: LocalDate,
            records: List<ScanRecord>,
            csv: File?,
        ) {
            if (date == failOn) throw IOException("네트워크 끊김")
            sentDates += date
        }
    }

    private suspend fun ScanRecordStore.put(day: Int) = append(
        ScanRecord(
            ZonedDateTime.of(2026, 10, day, 10, 0, 0, 0, zone),
            RecordMode.BORROW,
            "1000000001",
            "EM000000026$day",
        )
    )

    @Test
    fun `밀린 날짜를 오래된 순으로 모두 보낸다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        store.put(10); store.put(11); store.put(12)
        val mailer = FakeMailer()

        val outcome = DailyReportSender(store, mailer).sendPending(settings, today)

        assertEquals(
            listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 11), today),
            mailer.sentDates,
        )
        assertTrue(outcome is SendOutcome.Sent)
        assertTrue("모두 보냈으면 남은 날짜가 없다", store.unsentDates(today).isEmpty())
    }

    @Test
    fun `실패한 날짜는 표식을 남기지 않아 다음에 다시 보낸다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        store.put(10); store.put(11)
        val failing = FakeMailer(failOn = LocalDate.of(2026, 10, 10))

        val outcome = DailyReportSender(store, failing).sendPending(settings, today)

        assertTrue(outcome is SendOutcome.Failed)
        assertTrue("아무것도 못 보냈다", failing.sentDates.isEmpty())
        // 두 날짜 모두 그대로 남아 있어야 한다
        assertEquals(2, store.unsentDates(today).size)

        val retry = FakeMailer()
        DailyReportSender(store, retry).sendPending(settings, today)
        assertEquals(2, retry.sentDates.size)
    }

    @Test
    fun `일부만 보낸 뒤 실패하면 보낸 날짜는 유지된다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        store.put(10); store.put(11)
        val mailer = FakeMailer(failOn = LocalDate.of(2026, 10, 11))

        val outcome = DailyReportSender(store, mailer).sendPending(settings, today)

        assertEquals(listOf(LocalDate.of(2026, 10, 10)), mailer.sentDates)
        assertTrue(outcome is SendOutcome.Sent)
        assertEquals(listOf(LocalDate.of(2026, 10, 11)), store.unsentDates(today))
    }

    @Test
    fun `설정이 덜 됐으면 보내지 않는다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        store.put(12)
        val mailer = FakeMailer()

        val outcome = DailyReportSender(store, mailer)
            .sendPending(settings.copy(recipients = emptyList()), today)

        assertEquals(SendOutcome.NotConfigured, outcome)
        assertTrue(mailer.sentDates.isEmpty())
    }

    @Test
    fun `보낼 기록이 없으면 아무 일도 하지 않는다`() = runTest {
        val mailer = FakeMailer()
        val outcome = DailyReportSender(ScanRecordStore(tempFolder.root, zone), mailer)
            .sendPending(settings, today)

        assertEquals(SendOutcome.NothingToSend, outcome)
        assertTrue(mailer.sentDates.isEmpty())
    }
}
