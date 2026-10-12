package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.settings.MailConfigLoader
import com.xiaramteo.kiosk.settings.MailConfigResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalTime

class MailConfigLoaderTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private fun loaderFor(json: String?): MailConfigLoader {
        val file = File(tempFolder.root, "kiosk-mail.json")
        json?.let { file.writeText(it) }
        return MailConfigLoader(file)
    }

    private val valid = """
        {
          "enabled": true,
          "smtp": {
            "host": "smtp.gmail.com",
            "port": 587,
            "username": "kiosk@example.com",
            "appPassword": "abcd efgh ijkl mnop"
          },
          "recipients": ["a@example.com", "b@example.com"],
          "sendAt": "18:30"
        }
    """.trimIndent()

    @Test
    fun `정상 설정을 읽는다`() {
        val result = loaderFor(valid).load()
        val settings = (result as MailConfigResult.Loaded).settings
        assertEquals("smtp.gmail.com", settings.host)
        assertEquals(587, settings.port)
        assertEquals("kiosk@example.com", settings.username)
        assertEquals(listOf("a@example.com", "b@example.com"), settings.recipients)
        assertEquals(LocalTime.of(18, 30), settings.sendAt)
        assertTrue(settings.isSendable)
    }

    @Test
    fun `앱 비밀번호의 띄어쓰기를 지운다`() {
        // 구글이 네 자씩 띄워 보여줘서 그대로 붙여 넣는 경우가 많다.
        val settings = (loaderFor(valid).load() as MailConfigResult.Loaded).settings
        assertEquals("abcdefghijklmnop", settings.password)
    }

    @Test
    fun `파일이 없으면 기대 경로를 알려준다`() {
        val result = loaderFor(null).load()
        assertTrue(result is MailConfigResult.Missing)
        assertTrue((result as MailConfigResult.Missing).expectedPath.endsWith("kiosk-mail.json"))
    }

    @Test
    fun `생략한 값은 기본값으로 채운다`() {
        val result = loaderFor(
            """{"smtp":{"username":"k@example.com","appPassword":"pw"},"recipients":["a@example.com"]}"""
        ).load()
        val settings = (result as MailConfigResult.Loaded).settings
        assertEquals("smtp.gmail.com", settings.host)
        assertEquals(587, settings.port)
        assertEquals(LocalTime.of(18, 0), settings.sendAt)
        assertTrue("enabled 기본값은 켜짐", settings.enabled)
    }

    @Test
    fun `잘못된 설정은 이유를 돌려준다`() {
        fun reasonOf(json: String) =
            (loaderFor(json).load() as MailConfigResult.Invalid).reason

        assertTrue(reasonOf("{ this is not json").contains("JSON"))
        assertTrue(reasonOf("""{"recipients":["a@example.com"]}""").contains("smtp"))
        assertTrue(
            reasonOf("""{"smtp":{"username":"k@example.com","appPassword":"pw"},"recipients":[]}""")
                .contains("받는 사람")
        )
        assertTrue(
            reasonOf("""{"smtp":{"username":"k@example.com","appPassword":"pw"},"recipients":["틀린주소"]}""")
                .contains("올바르지 않습니다")
        )
        assertTrue(
            reasonOf("""{"smtp":{"username":"없음","appPassword":"pw"},"recipients":["a@example.com"]}""")
                .contains("username")
        )
        assertTrue(
            reasonOf("""{"smtp":{"username":"k@example.com","appPassword":""},"recipients":["a@example.com"]}""")
                .contains("appPassword")
        )
        assertTrue(
            reasonOf("""{"smtp":{"username":"k@example.com","appPassword":"pw"},"recipients":["a@example.com"],"sendAt":"여섯시"}""")
                .contains("sendAt")
        )
    }

    @Test
    fun `enabled 가 false 면 보내지 않는다`() {
        val json = valid.replace("\"enabled\": true", "\"enabled\": false")
        val settings = (loaderFor(json).load() as MailConfigResult.Loaded).settings
        assertTrue(!settings.isSendable)
    }
}
