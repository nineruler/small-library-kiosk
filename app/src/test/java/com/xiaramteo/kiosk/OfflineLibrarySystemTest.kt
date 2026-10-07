package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.ItemResult
import com.xiaramteo.kiosk.data.OfflineLibrarySystem
import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OfflineLibrarySystemTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val zone = ZoneId.of("Asia/Seoul")
    private val clock = Clock.fixed(Instant.parse("2026-10-07T09:00:00Z"), zone)
    private val today = LocalDate.now(clock)

    private fun system(store: ScanRecordStore) = OfflineLibrarySystem(store, clock)

    @Test
    fun `대출하면 회원번호와 장서바코드가 함께 기록된다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        val system = system(store)

        val member = system.findMember("1000000001")
        val item = system.findItem("EM0000000263")
        val result = system.borrow(member, item)

        assertTrue(result is ItemResult.Borrowed)
        val record = store.readRecords(today).single()
        assertEquals(RecordMode.BORROW, record.mode)
        assertEquals("1000000001", record.memberBarcode)
        assertEquals("EM0000000263", record.itemBarcode)
    }

    @Test
    fun `반납은 회원번호 없이 기록된다`() = runTest {
        val store = ScanRecordStore(tempFolder.root, zone)
        val system = system(store)

        system.giveBack(system.findItem("EM0000000263"))

        val record = store.readRecords(today).single()
        assertEquals(RecordMode.RETURN, record.mode)
        assertEquals(null, record.memberBarcode)
    }

    @Test
    fun `장서 목록이 없으므로 제목 자리에 바코드를 보여준다`() = runTest {
        val item = system(ScanRecordStore(tempFolder.root, zone)).findItem("EM0000000263")
        assertEquals("EM0000000263", item.displayTitle)
        assertEquals(null, item.displaySubtitle)
    }

    @Test
    fun `회원 목록이 없으므로 이름 자리에 회원번호를 보여주고 권수는 모른다`() = runTest {
        val member = system(ScanRecordStore(tempFolder.root, zone)).findMember("1000000001")
        assertEquals("1000000001", member.displayName)
        assertEquals(null, member.remainingLoans)
    }

    @Test
    fun `검증하지 않으므로 같은 책을 연달아 대출해도 둘 다 기록된다`() = runTest {
        // 중복 대출 여부는 키오스크가 알 수 없다. 사서가 코라시스에서 확인하도록 그대로 남긴다.
        val store = ScanRecordStore(tempFolder.root, zone)
        val system = system(store)
        val member = system.findMember("1000000001")

        system.borrow(member, system.findItem("EM0000000263"))
        system.borrow(member, system.findItem("EM0000000263"))

        assertEquals(2, store.readRecords(today).size)
    }
}
