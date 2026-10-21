package com.xiaramteo.kiosk.settings

import java.time.LocalTime

/**
 * 하루치 대출/반납 기록을 메일로 보내기 위한 설정.
 *
 * 비밀번호는 Google 계정 비밀번호가 아니라 **앱 비밀번호 16자리**다.
 * Google 이 "Less Secure Apps" 접근을 없애서 일반 계정 비밀번호로는 SMTP 인증이 되지 않는다.
 */
data class MailSettings(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val username: String = "",
    /** 앱 비밀번호. 화면에 되읽어 보여주지 않는다. */
    val password: String = "",
    /** 받는 사람. 여러 명을 둘 수 있고 순서는 보여주는 순서다. */
    val recipients: List<String> = emptyList(),
    val sendAt: LocalTime = DEFAULT_SEND_AT,
    val enabled: Boolean = false,
    /**
     * 사서 화면에 들어갈 때 묻는 비밀번호. 비어 있으면 묻지 않는다.
     * 로비에 둔 기기라 대출 기록을 아무나 들여다보지 못하게 하려는 것이다.
     */
    val adminPin: String = "",
) {
    /** 보낼 준비가 된 설정인지. 하나라도 비면 발송을 시도하지 않는다. */
    val isSendable: Boolean
        get() = enabled &&
            host.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            recipients.isNotEmpty()

    companion object {
        const val DEFAULT_HOST = "smtp.gmail.com"
        const val DEFAULT_PORT = 587
        val DEFAULT_SEND_AT: LocalTime = LocalTime.of(18, 0)

        /** 아주 느슨한 형식 검사. 오타로 통째로 빠지는 것만 막는다. */
        fun isValidEmail(value: String): Boolean {
            val trimmed = value.trim()
            val at = trimmed.indexOf('@')
            return at > 0 &&
                at == trimmed.lastIndexOf('@') &&
                trimmed.indexOf('.', at) > at + 1 &&
                !trimmed.endsWith('.') &&
                trimmed.none { it.isWhitespace() }
        }
    }
}
