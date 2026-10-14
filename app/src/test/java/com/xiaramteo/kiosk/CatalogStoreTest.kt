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
import java.nio.charset.Charset

class CatalogStoreTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun storeOf(text: String?, charset: Charset = Charsets.UTF_8): CatalogStore {
        val file = File(tempFolder.root, "catalog.csv")
        text?.let { file.writeBytes(it.toByteArray(charset)) }
        return CatalogStore(file)
    }

    private suspend fun itemsOf(text: String, charset: Charset = Charsets.UTF_8) =
        (storeOf(text, charset).load() as CatalogResult.Loaded).items

    @Test
    fun `등록번호로 책을 찾는다`() = runTest {
        val items = itemsOf(
            """
            등록번호,서명,저자,청구기호
            EM0000000263,어두워지면 일어나라,샬레인 해리스,843-해239ㅇ
            EM0000000777,소년이 온다,한강,813.7-한11ㅅ
            """.trimIndent()
        )
        assertEquals(2, items.size)
        val book = items.getValue("EM0000000263")
        assertEquals("어두워지면 일어나라", book.title)
        assertEquals("샬레인 해리스", book.author)
        assertEquals("843-해239ㅇ", book.callNumber)
    }

    @Test
    fun `열 순서가 달라도 이름으로 찾는다`() = runTest {
        val items = itemsOf(
            """
            청구기호,서명,등록번호
            813.7-한11ㅅ,소년이 온다,EM0000000777
            """.trimIndent()
        )
        val book = items.getValue("EM0000000777")
        assertEquals("소년이 온다", book.title)
        assertEquals("813.7-한11ㅅ", book.callNumber)
        assertNull("저자 열이 없으면 비워 둔다", book.author)
    }

    @Test
    fun `열 이름 표기가 달라도 받아들인다`() = runTest {
        val items = itemsOf(
            """
            자료바코드,도서명,지은이
            EM0000000263,데미안,헤르만 헤세
            """.trimIndent()
        )
        assertEquals("데미안", items.getValue("EM0000000263").title)
    }

    @Test
    fun `서명에 쉼표가 있어도 깨지지 않는다`() = runTest {
        val items = itemsOf(
            """
            등록번호,서명,저자
            EM0000000001,"아버지, 그리고 아들",홍길동
            """.trimIndent()
        )
        assertEquals("아버지, 그리고 아들", items.getValue("EM0000000001").title)
    }

    @Test
    fun `엑셀이 CP949 로 저장해도 한글이 깨지지 않는다`() = runTest {
        // 한국어 환경 엑셀의 "CSV로 저장"은 UTF-8 이 아닌 경우가 많다.
        val items = itemsOf(
            """
            등록번호,서명,저자
            EM0000000263,소년이 온다,한강
            """.trimIndent(),
            charset = Charset.forName("CP949"),
        )
        assertEquals("소년이 온다", items.getValue("EM0000000263").title)
        assertEquals("한강", items.getValue("EM0000000263").author)
    }

    @Test
    fun `UTF-8 BOM 이 붙어 있어도 첫 열을 찾는다`() = runTest {
        val items = itemsOf("﻿등록번호,서명\nEM0000000263,데미안")
        assertEquals("데미안", items.getValue("EM0000000263").title)
    }

    @Test
    fun `목록 파일이 없으면 기대 경로를 알려준다`() = runTest {
        val result = storeOf(null).load()
        assertTrue(result is CatalogResult.Missing)
    }

    @Test
    fun `등록번호 열이 없으면 이유와 읽은 열을 알려준다`() = runTest {
        val result = storeOf("서명,저자\n소년이 온다,한강").load()
        val reason = (result as CatalogResult.Invalid).reason
        assertTrue(reason.contains("등록번호"))
        assertTrue("무엇을 읽었는지 알려줘야 고칠 수 있다", reason.contains("서명"))
    }

    @Test
    fun `등록번호가 빈 줄은 건너뛴다`() = runTest {
        val items = itemsOf(
            """
            등록번호,서명
            ,제목만 있는 줄
            EM0000000263,데미안
            """.trimIndent()
        )
        assertEquals(1, items.size)
    }

    @Test
    fun `파일이 바뀌면 다시 읽는다`() = runTest {
        val file = File(tempFolder.root, "catalog.csv")
        file.writeText("등록번호,서명\nEM0000000263,데미안")
        val store = CatalogStore(file)
        assertEquals("데미안", (store.load() as CatalogResult.Loaded).items.getValue("EM0000000263").title)

        // 사서가 새 목록을 넣은 상황. 앱을 껐다 켜지 않아도 반영돼야 한다.
        Thread.sleep(10)
        file.writeText("등록번호,서명\nEM0000000263,소년이 온다\nEM0000000999,완전한 행복")
        file.setLastModified(System.currentTimeMillis() + 1000)

        val reloaded = (store.load() as CatalogResult.Loaded).items
        assertEquals(2, reloaded.size)
        assertEquals("소년이 온다", reloaded.getValue("EM0000000263").title)
    }
}
