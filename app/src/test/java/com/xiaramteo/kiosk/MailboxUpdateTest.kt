package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.update.ListUpdater
import com.xiaramteo.kiosk.update.MailboxUpdates
import com.xiaramteo.kiosk.update.UpdateOutcome
import com.xiaramteo.kiosk.update.UpdateTarget
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * 도서관에 설치해 둔 기기라 잘못 바꾸면 고치러 갈 수 없다. 두 가지가 반드시 지켜져야 한다 —
 * 암호가 맞는 메일만 열고, 읽히는 것을 확인하기 전에는 기존 목록을 건드리지 않는다.
 */
class MailboxUpdateTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val token = "a9f3k2"
    private fun updater() = ListUpdater(tempFolder.root)

    // --- 제목의 암호 ---

    @Test
    fun `암호가 맞아야 연다`() {
        assertTrue(MailboxUpdates.subjectMatches("KIOSK-UPDATE $token", token))
        assertTrue(MailboxUpdates.subjectMatches("kiosk-update $token", token))
        assertTrue(MailboxUpdates.subjectMatches("Re: KIOSK-UPDATE $token 회원 추가", token))
    }

    @Test
    fun `암호가 틀리면 열지 않는다`() {
        assertFalse(MailboxUpdates.subjectMatches("KIOSK-UPDATE 틀린암호", token))
        assertFalse(MailboxUpdates.subjectMatches("KIOSK-UPDATE", token))
        assertFalse(MailboxUpdates.subjectMatches("목록 바꿔 주세요", token))
        assertFalse(MailboxUpdates.subjectMatches(null, token))
        // 암호 뒤에 글자가 이어지면 다른 암호다.
        assertFalse(MailboxUpdates.subjectMatches("KIOSK-UPDATE ${token}9", token))
    }

    @Test
    fun `암호를 정해 두지 않으면 어떤 메일도 열지 않는다`() {
        // 암호 없이 받아 가면 주소만 아는 누구나 목록을 갈아 끼울 수 있다.
        assertFalse(MailboxUpdates.subjectMatches("KIOSK-UPDATE", ""))
        assertFalse(MailboxUpdates.subjectMatches("KIOSK-UPDATE ", "   "))
    }

    // --- 얼마나 자주 보는가 ---

    @Test
    fun `한 시간이 지나야 메일함을 다시 본다`() {
        val now = Instant.parse("2026-11-06T10:00:00Z")
        val hour = Duration.ofHours(1)

        // 작업은 15분마다 깨어나지만 메일함은 그때마다 열지 않는다.
        assertFalse(MailboxUpdates.isDue(now, now.minus(Duration.ofMinutes(15)), hour))
        assertFalse(MailboxUpdates.isDue(now, now.minus(Duration.ofMinutes(59)), hour))
        assertTrue(MailboxUpdates.isDue(now, now.minus(hour), hour))
        assertTrue(MailboxUpdates.isDue(now, now.minus(Duration.ofHours(3)), hour))
    }

    @Test
    fun `처음에는 바로 본다`() {
        val now = Instant.parse("2026-11-06T10:00:00Z")
        assertTrue(MailboxUpdates.isDue(now, null, Duration.ofHours(1)))
    }

    @Test
    fun `기기 시간이 뒤로 가도 멈추지 않는다`() {
        // 시간대를 바꾸거나 손으로 맞추면 적어 둔 시각이 미래가 된다. 영영 안 보면 곤란하다.
        val now = Instant.parse("2026-11-06T10:00:00Z")
        assertTrue(MailboxUpdates.isDue(now, now.plus(Duration.ofDays(1)), Duration.ofHours(1)))
    }

    // --- 어떤 첨부를 받는가 ---

    @Test
    fun `목록 파일만 받는다`() {
        val u = updater()
        assertEquals(UpdateTarget.CATALOG to "xlsx", u.targetFor("catalog.xlsx"))
        assertEquals(UpdateTarget.MEMBERS to "csv", u.targetFor("members.csv"))
        // 코라시스에서 날짜를 붙여 내려받는 경우가 흔하다.
        assertEquals(UpdateTarget.CATALOG to "xlsx", u.targetFor("catalog_261103.xlsx"))
        assertEquals(UpdateTarget.MEMBERS to "xlsx", u.targetFor("MEMBERS.XLSX"))

        assertNull(u.targetFor("자이아람터_소장도서.xlsx"))
        assertNull(u.targetFor("catalog.pdf"))
        assertNull(u.targetFor(null))
    }

    // --- 검증하고 바꾸기 ---

    private val goodMembers = "회원명,회원번호\n구*현,73223826000040\n윤*희,73223826000157\n"

    @Test
    fun `읽히는 회원 목록은 바꾼다`() = runTest {
        val outcome = updater().apply(UpdateTarget.MEMBERS, "csv", "members.csv", goodMembers.toByteArray())

        assertEquals(UpdateOutcome.Applied(UpdateTarget.MEMBERS, "members.csv", 2), outcome)
        assertEquals(goodMembers, File(tempFolder.root, "members.csv").readText())
    }

    @Test
    fun `읽히지 않는 파일은 기존 목록을 건드리지 않는다`() = runTest {
        val live = File(tempFolder.root, "members.csv").apply { writeText(goodMembers) }

        val outcome = updater()
            .apply(UpdateTarget.MEMBERS, "csv", "members.csv", "이건 회원 목록이 아니다".toByteArray())

        assertTrue("거절해야 한다", outcome is UpdateOutcome.Rejected)
        assertEquals("기존 목록이 그대로여야 한다", goodMembers, live.readText())
        assertFalse(File(tempFolder.root, "members.incoming.csv").exists())
    }

    @Test
    fun `빈 첨부도 기존 목록을 건드리지 않는다`() = runTest {
        val live = File(tempFolder.root, "members.csv").apply { writeText(goodMembers) }

        val outcome = updater().apply(UpdateTarget.MEMBERS, "csv", "members.csv", ByteArray(0))

        assertTrue(outcome is UpdateOutcome.Rejected)
        assertEquals(goodMembers, live.readText())
    }

    @Test
    fun `확장자가 바뀌어 오면 예전 파일을 치운다`() = runTest {
        // xlsx 가 csv 보다 먼저 읽히므로, 둘이 남아 있으면 새로 보낸 csv 가 묻힌다.
        val old = File(tempFolder.root, "members.xlsx")
        XlsxFixture.write(old, listOf(listOf("회원명", "회원번호"), listOf("구*현", "73223826000040")))

        val outcome = updater().apply(UpdateTarget.MEMBERS, "csv", "members.csv", goodMembers.toByteArray())

        assertTrue(outcome is UpdateOutcome.Applied)
        assertFalse("예전 xlsx 가 남아 있으면 안 된다", old.exists())
        assertTrue(File(tempFolder.root, "members.csv").exists())
    }

    @Test
    fun `엑셀 소장자료도 받는다`() = runTest {
        val source = File(tempFolder.root, "보낸파일.xlsx")
        XlsxFixture.write(
            source,
            listOf(
                listOf("소장자료 목록", "", ""),
                listOf("등록번호", "서명", "저자"),
                listOf("EM0000000001", "소년이 온다", "한강"),
                listOf("EM0000000002", "종이학 살인사건", "치넨 미키토"),
            ),
        )

        val outcome = updater()
            .apply(UpdateTarget.CATALOG, "xlsx", "catalog_261103.xlsx", source.readBytes())

        assertEquals(
            UpdateOutcome.Applied(UpdateTarget.CATALOG, "catalog_261103.xlsx", 2),
            outcome,
        )
        // 보낸 이름이 무엇이든 앱이 찾는 이름으로 놓여야 한다.
        assertTrue(File(tempFolder.root, "catalog.xlsx").exists())
    }

    @Test
    fun `실패 이유를 사서가 읽을 수 있는 말로 돌려준다`() = runTest {
        val outcome = updater()
            .apply(UpdateTarget.MEMBERS, "csv", "members.csv", "이름,전화번호\n구자현,010\n".toByteArray())

        val rejected = outcome as UpdateOutcome.Rejected
        assertTrue("무엇이 잘못됐는지 말해야 한다", rejected.reason.contains("회원번호"))
        assertTrue(rejected.line.contains("members.csv"))
    }
}
