package com.xiaramteo.kiosk

import com.xiaramteo.kiosk.settings.MailSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MailSettingsTest {

    @Test
    fun `보낼 준비가 되려면 켜짐 계정 비밀번호 수신자가 모두 있어야 한다`() {
        val full = MailSettings(
            username = "kiosk@example.com",
            password = "apppassword",
            recipients = listOf("a@example.com"),
            enabled = true,
        )
        assertTrue(full.isSendable)

        assertFalse("꺼져 있으면 보내지 않는다", full.copy(enabled = false).isSendable)
        assertFalse("수신자가 없으면 보내지 않는다", full.copy(recipients = emptyList()).isSendable)
        assertFalse("비밀번호가 없으면 보내지 않는다", full.copy(password = "").isSendable)
        assertFalse("보내는 계정이 없으면 보내지 않는다", full.copy(username = "").isSendable)
    }

    @Test
    fun `메일 주소 형식을 느슨하게 거른다`() {
        listOf(
            "a@example.com",
            "sdt.parkxi.small.lib@gmail.com",
            "a.b+tag@sub.example.co.kr",
        ).forEach { assertTrue(it, MailSettings.isValidEmail(it)) }

        listOf(
            "", "example.com", "@example.com", "a@", "a@com",
            "a@@example.com", "a b@example.com", "a@example.",
        ).forEach { assertFalse(it, MailSettings.isValidEmail(it)) }
    }
}
