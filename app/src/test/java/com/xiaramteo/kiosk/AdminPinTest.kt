package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.settings.MailConfigLoader
import com.xiaramteo.kiosk.settings.MailConfigResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdminPinTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun settingsOf(json: String) =
        (MailConfigLoader(File(tempFolder.root, "kiosk-mail.json").apply { writeText(json) })
            .load() as MailConfigResult.Loaded).settings

    private val base = """
        "smtp": {"username": "k@example.com", "appPassword": "pw"},
        "recipients": ["a@example.com"]
    """.trimIndent()

    @Test
    fun `설정 파일에서 사서 비밀번호를 읽는다`() {
        assertEquals("8130", settingsOf("""{$base, "adminPin": "8130"}""").adminPin)
    }

    @Test
    fun `비밀번호가 없으면 빈 값이다`() {
        // 비어 있으면 화면을 잠그지 않는다. 설정 전에 사서가 갇히면 곤란하다.
        assertEquals("", settingsOf("{$base}").adminPin)
    }

    @Test
    fun `앞뒤 공백은 지운다`() {
        assertEquals("8130", settingsOf("""{$base, "adminPin": "  8130  "}""").adminPin)
    }
}
