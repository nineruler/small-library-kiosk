package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.data.local.CatalogResult
import com.xiaramteo.kiosk.data.local.CatalogStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 코라시스가 내려주는 엑셀 구조를 그대로 흉내 낸 파일로 검증한다.
 *
 * 실제 파일(자이아람터 소장도서 1,777권)로도 확인했고 전부 읽혔다. 다만 장서 목록 전체를
 * 저장소에 두지 않으려고, 구조가 같은 축소본으로 테스트를 남긴다.
 *
 * 흉내 내는 특징
 * - 1행은 "소장자료 목록" 제목, 2행이 헤더 (첫 줄을 헤더로 보면 통째로 실패한다)
 * - 25개 열 중 네 개만 쓴다
 * - 서명에 " : " 부제가 붙는다
 * - 빈 칸은 기록되지 않는다
 */
class CatalogXlsxTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private val rows = listOf(
        listOf("소장자료 목록"),
        listOf("번호", "등록번호", "서명", "권서명", "저자", "발행자", "청구기호"),
        listOf("1", "EM0000000001", "퇴근 후 고양이랑 한잔 : 나를 위로하는 보드라운 시간", "", "진고로호", "꼼지락", "일 818-진15ㅌ"),
        listOf("2", "EM0000000003", "소년이 온다 : 2024 노벨문학상 수상작가", "", "한강", "창비", "일 813.7-한12ㅅ"),
        listOf("3", "EM0000000002", "종이학 살인사건", "", "치넨 미키토 ; 권하영", "북플라자", "일 833.6-치194ㅈ"),
    )

    private suspend fun itemsOf(useSharedStrings: Boolean = true) =
        (
            CatalogStore(
                XlsxFixture.write(File(tempFolder.root, "catalog.xlsx"), rows, useSharedStrings)
            ).load() as CatalogResult.Loaded
            ).items

    @Test
    fun `제목 행을 건너뛰고 헤더를 찾는다`() = runTest {
        val items = itemsOf()
        assertEquals(3, items.size)
        assertTrue("제목 행이 자료로 섞이면 안 된다", items.keys.none { it.contains("소장자료") })
        assertTrue("헤더 행이 자료로 섞이면 안 된다", items.keys.none { it == "등록번호" })
    }

    @Test
    fun `쓰지 않는 열이 섞여 있어도 필요한 것만 읽는다`() = runTest {
        val book = itemsOf().getValue("EM0000000003")
        assertEquals("소년이 온다 : 2024 노벨문학상 수상작가", book.title)
        assertEquals("한강", book.author)
        assertEquals("일 813.7-한12ㅅ", book.callNumber)
    }

    @Test
    fun `빈 칸이 기록되지 않아도 열 자리가 밀리지 않는다`() = runTest {
        // 권서명(D열)이 비어 있다. 칸 주소를 보지 않고 순서대로 읽으면 저자 자리가 밀린다.
        assertEquals("진고로호", itemsOf().getValue("EM0000000001").author)
    }

    @Test
    fun `문자열을 칸 안에 직접 넣은 엑셀도 읽는다`() = runTest {
        val items = itemsOf(useSharedStrings = false)
        assertEquals("한강", items.getValue("EM0000000003").author)
    }

    @Test
    fun `목록에 없는 바코드는 비어 있다`() = runTest {
        assertNull(itemsOf()["8801234567890"])
    }
}
