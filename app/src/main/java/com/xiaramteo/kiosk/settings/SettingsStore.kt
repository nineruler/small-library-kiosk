package com.xiaramteo.kiosk.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * 메일 설정 저장소.
 *
 * 앱 비밀번호가 들어가므로 평문 SharedPreferences 가 아니라 Keystore 로 암호화된 저장소를 쓴다.
 * 저장소에도 코드에도 자격증명을 넣지 않고, 사서가 기기에서 직접 입력하게 하려는 것이다.
 *
 * 받는 사람은 여러 명이라 한 줄 문자열로 합쳐 저장하되, 주소에 쓰이지 않는 개행으로 구분한다.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<MailSettings> = _settings.asStateFlow()

    fun current(): MailSettings = _settings.value

    fun save(settings: MailSettings) {
        prefs.edit()
            .putString(KEY_HOST, settings.host)
            .putInt(KEY_PORT, settings.port)
            .putString(KEY_USERNAME, settings.username)
            .putString(KEY_PASSWORD, settings.password)
            .putString(KEY_RECIPIENTS, settings.recipients.joinToString("\n"))
            .putString(KEY_SEND_AT, settings.sendAt.toString())
            .putBoolean(KEY_ENABLED, settings.enabled)
            .apply()
        _settings.value = settings
    }

    private fun load(): MailSettings {
        val stored = prefs.getString(KEY_RECIPIENTS, null).orEmpty()
        return MailSettings(
            host = prefs.getString(KEY_HOST, MailSettings.DEFAULT_HOST).orEmpty(),
            port = prefs.getInt(KEY_PORT, MailSettings.DEFAULT_PORT),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty(),
            recipients = stored.split("\n").map { it.trim() }.filter { it.isNotEmpty() },
            sendAt = prefs.getString(KEY_SEND_AT, null)
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: MailSettings.DEFAULT_SEND_AT,
            enabled = prefs.getBoolean(KEY_ENABLED, false),
        )
    }

    private companion object {
        const val FILE_NAME = "kiosk_mail_settings"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_RECIPIENTS = "recipients"
        const val KEY_SEND_AT = "send_at"
        const val KEY_ENABLED = "enabled"
    }
}
