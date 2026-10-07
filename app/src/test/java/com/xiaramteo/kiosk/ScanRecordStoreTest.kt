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

class ScanRecordStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private fun store() = ScanRecordStore(tempFolder.root, zone)

    private fun at(day: Int, hour: Int) =
        ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone)

    @Test
    fun `적은 기록을 그대로 다시 읽는다`() = runTest {
        val s = store()
        s.append(ScanRecord(at(7, 10), RecordMode.BORROW, "1000000001", "EM0000000263"))
        s.append(ScanRecord(at(7, 11), RecordMode.RETURN, null, "EM0000000999"))

        val records = s.readRecords(LocalDate.of(2026, 10, 7))
        assertEquals(2, records.size)
        assertEquals(RecordMode.BORROW, records[0].mode)
        assertEquals("1000000001", records[0].memberBarcode)
        assertEquals("EM0000000263", records[0].itemBarcode)
        // 반납은 회원번호가 없다
        assertEquals(null, records[1].memberBarcode)
    }

    @Test
    fun `날짜별로 파일이 갈린다`() = runTest {
        val s = store()
        s.append(ScanRecord(at(7, 10), RecordMode.BORROW, "1", "A"))
        s.append(ScanRecord(at(8, 10), RecordMode.BORROW, "2", "B"))

        assertEquals(1, s.readRecords(LocalDate.of(2026, 10, 7)).size)
        assertEquals(1, s.readRecords(LocalDate.of(2026, 10, 8)).size)
    }

    @Test
    fun `보내지 않은 날짜만 돌려주고 보낸 날짜는 빠진다`() = runTest {
        val s = store()
        s.append(ScanRecord(at(5, 10), RecordMode.BORROW, "1", "A"))
        s.append(ScanRecord(at(6, 10), RecordMode.BORROW, "2", "B"))
        s.append(ScanRecord(at(7, 10), RecordMode.BORROW, "3", "C"))

        val today = LocalDate.of(2026, 10, 7)
        assertEquals(
            listOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), today),
            s.unsentDates(upTo = today),
        )

        s.markSent(LocalDate.of(2026, 10, 5))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 6), today),
            s.unsentDates(upTo = today),
        )
    }

    @Test
    fun `발송해도 기록은 남는다`() = runTest {
        val s = store()
        val day = LocalDate.of(2026, 10, 7)
        s.append(ScanRecord(at(7, 10), RecordMode.BORROW, "1", "A"))
        s.markSent(day)

        assertEquals(1, s.readRecords(day).size)
        assertTrue(s.csvFor(day)!!.exists())
    }

    @Test
    fun `미래 날짜는 발송 대상에서 제외한다`() = runTest {
        val s = store()
        s.append(ScanRecord(at(9, 10), RecordMode.BORROW, "1", "A"))
        assertTrue(s.unsentDates(upTo = LocalDate.of(2026, 10, 7)).isEmpty())
    }

    @Test
    fun `기록이 없는 날은 빈 목록이다`() = runTest {
        assertTrue(store().readRecords(LocalDate.of(2026, 1, 1)).isEmpty())
        assertFalse(store().csvFor(LocalDate.of(2026, 1, 1))?.exists() ?: false)
    }

    @Test
    fun `쉼표가 든 바코드도 깨지지 않는다`() = runTest {
        val s = store()
        s.append(ScanRecord(at(7, 10), RecordMode.BORROW, "1,000", "EM,263"))
        val r = s.readRecords(LocalDate.of(2026, 10, 7)).single()
        assertEquals("1,000", r.memberBarcode)
        assertEquals("EM,263", r.itemBarcode)
    }
}
