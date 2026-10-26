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
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DeleteRecordTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private val date = LocalDate.of(2026, 10, 26)
    private fun store() = ScanRecordStore(tempFolder.root, zone)

    private suspend fun ScanRecordStore.put(hour: Int, item: String) = append(
        ScanRecord(
            ZonedDateTime.of(2026, 10, 26, hour, 0, 0, 0, zone),
            RecordMode.BORROW,
            "1000000001",
            item,
        )
    )

    @Test
    fun `가운데 한 건만 지우고 나머지는 순서대로 남는다`() = runTest {
        val s = store()
        s.put(10, "A")
        s.put(11, "B")
        s.put(12, "C")

        assertTrue(s.deleteRecord(date, 1))

        assertEquals(listOf("A", "C"), s.readRecords(date).map { it.itemBarcode })
    }

    @Test
    fun `헤더는 남겨서 파일이 그대로 열린다`() = runTest {
        val s = store()
        s.put(10, "A")
        s.put(11, "B")
        s.deleteRecord(date, 0)

        val lines = File(tempFolder.root, "records/$date.csv").readLines()
        assertEquals("기록시각,구분,회원번호,장서바코드", lines.first())
        assertEquals(2, lines.size)
    }

    @Test
    fun `마지막 한 건을 지우면 그날 파일과 발송 표식도 사라진다`() = runTest {
        val s = store()
        s.put(10, "A")
        s.markSent(date)
        assertTrue(File(tempFolder.root, "sent/$date.ok").exists())

        assertTrue(s.deleteRecord(date, 0))

        assertFalse(File(tempFolder.root, "records/$date.csv").exists())
        assertFalse(File(tempFolder.root, "sent/$date.ok").exists())
        assertTrue(s.daySummaries().isEmpty())
    }

    @Test
    fun `없는 자리나 없는 날짜를 지우라고 하면 아무것도 하지 않는다`() = runTest {
        val s = store()
        s.put(10, "A")

        assertFalse(s.deleteRecord(date, 5))
        assertFalse(s.deleteRecord(LocalDate.of(2026, 1, 1), 0))
        assertEquals(1, s.readRecords(date).size)
    }

    @Test
    fun `읽히지 않는 줄이 섞여 있어도 화면에 보이는 순서대로 지운다`() = runTest {
        val s = store()
        s.put(10, "A")
        s.put(11, "B")
        // 손으로 고치다 깨진 줄. 화면에는 뜨지 않으므로 세는 데서 빠져야 한다.
        val file = File(tempFolder.root, "records/$date.csv")
        val lines = file.readLines().toMutableList()
        lines.add(1, "깨진줄")
        file.writeText(lines.joinToString("\n", postfix = "\n"))

        assertTrue(s.deleteRecord(date, 0))

        assertEquals(listOf("B"), s.readRecords(date).map { it.itemBarcode })
        assertTrue(file.readLines().any { it == "깨진줄" })
    }
}
