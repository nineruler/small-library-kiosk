package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.local.MemberDirectoryResult
import com.xiaramteo.kiosk.data.local.MemberDirectoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

class MemberDirectoryStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun storeOf(text: String?, charset: Charset = Charsets.UTF_8): MemberDirectoryStore {
        val file = File(tempFolder.root, "members.csv")
        text?.let { file.writeBytes(it.toByteArray(charset)) }
        return MemberDirectoryStore(file)
    }

    private suspend fun membersOf(text: String, charset: Charset = Charsets.UTF_8) =
        (storeOf(text, charset).load() as MemberDirectoryResult.Loaded).members

    @Test
    fun `회원번호로 회원을 찾는다`() = runTest {
        val members = membersOf(
            """
            회원번호,회원명
            1000000001,구*린
            1000000002,박*윤
            """.trimIndent()
        )
        assertEquals(2, members.size)
        assertEquals("구*린", members.getValue("1000000001").name)
        assertEquals("구*린", members.getValue("1000000001").displayName)
    }

    @Test
    fun `가린 이름을 그대로 보여준다`() = runTest {
        // 가리는 것은 사서가 내보낼 때 하고, 앱은 받은 대로 쓴다.
        val members = membersOf("회원번호,회원명\n1000000001,구*린")
        assertEquals("구*린", members.getValue("1000000001").displayName)
    }

    @Test
    fun `이름 열이 없으면 회원번호로 대신 부른다`() = runTest {
        val members = membersOf("회원번호\n1000000001")
        val member = members.getValue("1000000001")
        assertNull(member.name)
        assertEquals("1000000001", member.displayName)
    }

    @Test
    fun `열 이름 표기가 달라도 받아들인다`() = runTest {
        val members = membersOf("회원바코드,이름\n1000000001,구*린")
        assertEquals("구*린", members.getValue("1000000001").name)
    }

    @Test
    fun `대출 권수는 알 수 없다`() = runTest {
        // 실시간 대출 상태는 코라시스에만 있다. 목록으로는 알 수 없다.
        val member = membersOf("회원번호,회원명\n1000000001,구*린").getValue("1000000001")
        assertNull(member.loanCount)
        assertNull(member.remainingLoans)
    }

    @Test
    fun `엑셀이 CP949 로 저장해도 한글이 깨지지 않는다`() = runTest {
        val members = membersOf(
            "회원번호,회원명\n1000000001,구*린",
            charset = Charset.forName("CP949"),
        )
        assertEquals("구*린", members.getValue("1000000001").name)
    }

    @Test
    fun `목록 파일이 없으면 기대 경로를 알려준다`() = runTest {
        assertTrue(storeOf(null).load() is MemberDirectoryResult.Missing)
    }

    @Test
    fun `회원번호 열이 없으면 이유와 읽은 열을 알려준다`() = runTest {
        val reason = (storeOf("이름,연락처\n구*린,010").load() as MemberDirectoryResult.Invalid).reason
        assertTrue(reason.contains("회원번호"))
        assertTrue(reason.contains("이름"))
    }

    @Test
    fun `파일이 바뀌면 다시 읽는다`() = runTest {
        val file = File(tempFolder.root, "members.csv")
        file.writeText("회원번호,회원명\n1000000001,구*린")
        val store = MemberDirectoryStore(file)
        assertEquals(1, (store.load() as MemberDirectoryResult.Loaded).members.size)

        Thread.sleep(10)
        file.writeText("회원번호,회원명\n1000000001,구*린\n1000000002,박*윤")
        file.setLastModified(System.currentTimeMillis() + 1000)

        assertEquals(2, (store.load() as MemberDirectoryResult.Loaded).members.size)
    }
}
