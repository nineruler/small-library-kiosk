package com.xiaramteo.kiosk.settings

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeParseException

/** 설정 파일을 읽은 결과. 화면이 사서에게 상태를 그대로 보여줄 수 있도록 이유를 담는다. */
sealed interface MailConfigResult {
    /** 파일이 아직 없다. 발송을 시도하지 않는다. */
    data class Missing(val expectedPath: String) : MailConfigResult
    /** 파일은 있으나 읽을 수 없다. [reason] 을 그대로 보여준다. */
    data class Invalid(val reason: String) : MailConfigResult
    data class Loaded(val settings: MailSettings) : MailConfigResult
}

/**
 * 메일 설정을 기기의 JSON 파일에서 읽는다.
 *
 * 화면에서 입력받지 않는 이유는 두 가지다. 로비에 둔 태블릿에서 긴 메일 주소와 앱 비밀번호
 * 16자리를 손으로 치기 번거롭고, 한글 입력기가 켜져 있으면 영문 입력이 자모로 바뀌어 버린다.
 * 파일로 두면 adb 나 USB 로 한 번 넣고 끝난다.
 *
 * 읽는 위치는 앱 전용 외부 저장소라 다른 앱이 들여다볼 수 없고, adb push 로 바로 넣을 수 있다.
 *
 * 주의: 앱 비밀번호가 평문으로 들어 있는 파일이다. 기기를 분실하면 그 계정으로 메일을 보낼 수
 * 있으므로, 발신 전용 계정을 쓰고 받은편지함은 비워 두는 것을 전제로 한다.
 */
class MailConfigLoader(private val configFile: File) {

    val path: String get() = configFile.absolutePath

    fun load(): MailConfigResult {
        if (!configFile.exists()) return MailConfigResult.Missing(path)

        val text = runCatching { configFile.readText() }
            .getOrElse { return MailConfigResult.Invalid("파일을 읽지 못했습니다: ${it.message}") }

        return runCatching { parse(text) }
            .getOrElse { error ->
                MailConfigResult.Invalid(
                    when (error) {
                        is JSONException -> "JSON 형식이 올바르지 않습니다: ${error.message}"
                        else -> error.message ?: "설정을 읽지 못했습니다"
                    }
                )
            }
    }

    private fun parse(text: String): MailConfigResult {
        val root = JSONObject(text)
        val smtp = root.optJSONObject("smtp")
            ?: return MailConfigResult.Invalid("smtp 항목이 없습니다")

        val recipients = root.optJSONArray("recipients")
            ?.let { array -> (0 until array.length()).map { array.getString(it).trim() } }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val invalid = recipients.filterNot { MailSettings.isValidEmail(it) }
        if (invalid.isNotEmpty()) {
            return MailConfigResult.Invalid("받는 사람 주소가 올바르지 않습니다: ${invalid.joinToString()}")
        }
        if (recipients.isEmpty()) {
            return MailConfigResult.Invalid("recipients 에 받는 사람을 한 명 이상 적어 주세요")
        }

        val username = smtp.optString("username").trim()
        if (!MailSettings.isValidEmail(username)) {
            return MailConfigResult.Invalid("smtp.username 이 올바른 메일 주소가 아닙니다")
        }

        // 구글이 앱 비밀번호를 네 자씩 띄워 보여줘서 그대로 붙여 넣는 경우가 많다.
        val password = smtp.optString("appPassword").replace(" ", "")
        if (password.isEmpty()) {
            return MailConfigResult.Invalid("smtp.appPassword 가 비어 있습니다")
        }

        val sendAt = root.optString("sendAt").ifBlank { null }?.let { raw ->
            try {
                LocalTime.parse(raw)
            } catch (e: DateTimeParseException) {
                return MailConfigResult.Invalid("sendAt 은 \"18:00\" 형식이어야 합니다 (받은 값: $raw)")
            }
        } ?: MailSettings.DEFAULT_SEND_AT

        return MailConfigResult.Loaded(
            MailSettings(
                host = smtp.optString("host").ifBlank { MailSettings.DEFAULT_HOST },
                port = smtp.optInt("port", MailSettings.DEFAULT_PORT),
                username = username,
                password = password,
                recipients = recipients,
                sendAt = sendAt,
                enabled = root.optBoolean("enabled", true),
                adminPin = root.optString("adminPin").trim(),
            )
        )
    }
}
