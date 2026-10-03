package com.xiaramteo.kiosk

import android.view.KeyEvent
import com.xiaramteo.kiosk.scan.HidScannerReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidScannerReaderTest {

    private val scanned = mutableListOf<String>()
    private val reader = HidScannerReader(maxGapMillis = 200) { scanned += it }

    /** 리더기처럼 문자를 빠르게 밀어넣고 Enter 로 끝낸다. */
    private fun scan(text: String, startAt: Long = 0, gap: Long = 5) {
        var t = startAt
        text.forEach { c ->
            reader.onKey(KeyEvent.KEYCODE_UNKNOWN, c.code, t)
            t += gap
        }
        reader.onKey(KeyEvent.KEYCODE_ENTER, 0, t)
    }

    @Test
    fun `리더기가 보낸 바코드를 한 건으로 모은다`() {
        scan("9788936434120")
        assertEquals(listOf("9788936434120"), scanned)
    }

    @Test
    fun `연속으로 두 번 읽으면 두 건이 된다`() {
        scan("9788936434120", startAt = 0)
        scan("9788937473135", startAt = 1_000)
        assertEquals(listOf("9788936434120", "9788937473135"), scanned)
    }

    @Test
    fun `문자 사이가 벌어지면 사람이 친 것으로 보고 앞부분을 버린다`() {
        reader.onKey(KeyEvent.KEYCODE_UNKNOWN, '1'.code, 0)
        reader.onKey(KeyEvent.KEYCODE_UNKNOWN, '2'.code, 5)
        // 500ms 공백 — 여기서 앞의 "12" 는 버려진다
        reader.onKey(KeyEvent.KEYCODE_UNKNOWN, '9'.code, 505)
        reader.onKey(KeyEvent.KEYCODE_UNKNOWN, '8'.code, 510)
        reader.onKey(KeyEvent.KEYCODE_ENTER, 0, 515)
        assertEquals(listOf("98"), scanned)
    }

    @Test
    fun `읽은 내용 없이 Enter 만 오면 아무 일도 일어나지 않는다`() {
        reader.onKey(KeyEvent.KEYCODE_ENTER, 0, 0)
        assertTrue(scanned.isEmpty())
    }

    @Test
    fun `바코드 값에 쓰이지 않는 키는 화면으로 넘긴다`() {
        assertFalse(reader.onKey(KeyEvent.KEYCODE_VOLUME_UP, 0, 0))
        assertFalse(reader.onKey(KeyEvent.KEYCODE_BACK, 0, 0))
    }

    @Test
    fun `영문자가 섞인 등록번호도 그대로 읽는다`() {
        scan("JA2024-00137")
        assertEquals(listOf("JA2024-00137"), scanned)
    }
}
