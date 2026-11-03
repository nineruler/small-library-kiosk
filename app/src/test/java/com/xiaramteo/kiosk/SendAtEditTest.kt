package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.settings.MailConfigLoader
import com.xiaramteo.kiosk.settings.MailConfigResult
import com.xiaramteo.kiosk.ui.screen.parseSendAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

class SendAtEditTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun configFile(body: String): File =
        File(tempFolder.root, "kiosk-mail.json").apply { writeText(body) }

    private val full = """
        {
          "enabled": true,
          "smtp": {
            "host": "smtp.example.com",
            "port": 587,
            "username": "sender@example.com",
            "appPassword": "abcd efgh ijkl mnop"
          },
          "recipients": ["one@example.com"],
          "sendAt": "18:00",
          "adminPin": "8130",
          "낯선항목": "건드리지 말 것"
        }
    """.trimIndent()

    @Test
    fun `발송 시각만 바뀌고 나머지는 그대로 남는다`() {
        val file = configFile(full)
        val loader = MailConfigLoader(file)

        assertTrue(loader.updateSendAt(LocalTime.of(22, 0)))

        val settings = (loader.load() as MailConfigResult.Loaded).settings
        assertEquals(LocalTime.of(22, 0), settings.sendAt)
        // 앱 비밀번호와 받는 사람, 비밀번호가 함께 살아 있어야 한다.
        assertEquals("abcdefghijklmnop", settings.password)
        assertEquals(listOf("one@example.com"), settings.recipients)
        assertEquals("8130", settings.adminPin)
        // 우리가 모르는 항목도 지우지 않는다.
        assertTrue(file.readText().contains("낯선항목"))
    }

    @Test
    fun `적을 때는 초 없이 HH mm 으로 남긴다`() {
        val file = configFile(full)
        MailConfigLoader(file).updateSendAt(LocalTime.of(9, 5))
        assertTrue(file.readText().contains("\"sendAt\": \"09:05\""))
    }

    @Test
    fun `파일이 없으면 적지 않고 실패를 알린다`() {
        val missing = File(tempFolder.root, "없는파일.json")
        assertFalse(MailConfigLoader(missing).updateSendAt(LocalTime.of(22, 0)))
        assertFalse(missing.exists())
    }

    @Test
    fun `깨진 파일은 건드리지 않는다`() {
        val file = configFile("{ 이건 JSON 이 아니다")
        assertFalse(MailConfigLoader(file).updateSendAt(LocalTime.of(22, 0)))
        assertEquals("{ 이건 JSON 이 아니다", file.readText())
    }

    @Test
    fun `네 자리를 다 쳐야 시각이 된다`() {
        assertNull(parseSendAt(""))
        assertNull(parseSendAt("22"))
        assertNull(parseSendAt("220"))
        assertEquals(LocalTime.of(22, 0), parseSendAt("2200"))
        assertEquals(LocalTime.of(0, 0), parseSendAt("0000"))
        assertEquals(LocalTime.of(9, 5), parseSendAt("0905"))
    }

    @Test
    fun `없는 시각은 거른다`() {
        assertNull(parseSendAt("2400"))
        assertNull(parseSendAt("1870"))
        assertNull(parseSendAt("9999"))
    }
}
