package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DaySummaryTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private fun store() = ScanRecordStore(tempFolder.root, zone)

    private suspend fun ScanRecordStore.put(day: Int, mode: RecordMode) = append(
        ScanRecord(
            ZonedDateTime.of(2026, 10, day, 10, 0, 0, 0, zone),
            mode,
            if (mode == RecordMode.BORROW) "1000000001" else null,
            "EM000000000$day",
        )
    )

    @Test
    fun `날짜별로 대출과 반납을 세어 최근 순으로 돌려준다`() = runTest {
        val s = store()
        s.put(1, RecordMode.BORROW)
        s.put(1, RecordMode.BORROW)
        s.put(1, RecordMode.RETURN)
        s.put(3, RecordMode.RETURN)

        val days = s.daySummaries()
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1)), days.map { it.date })

        val first = days.last()
        assertEquals(2, first.borrowed)
        assertEquals(1, first.returned)
        assertEquals(3, first.total)
    }

    @Test
    fun `발송 여부를 함께 알려준다`() = runTest {
        val s = store()
        s.put(1, RecordMode.BORROW)
        s.put(2, RecordMode.BORROW)
        s.markSent(LocalDate.of(2026, 10, 1))

        val byDate = s.daySummaries().associateBy { it.date }
        assertTrue(byDate.getValue(LocalDate.of(2026, 10, 1)).sent)
        assertFalse(byDate.getValue(LocalDate.of(2026, 10, 2)).sent)
    }

    @Test
    fun `기록이 없으면 빈 목록이다`() = runTest {
        assertTrue(store().daySummaries().isEmpty())
    }
}
