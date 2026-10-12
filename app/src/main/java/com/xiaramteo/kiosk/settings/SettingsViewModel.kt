package com.xiaramteo.kiosk.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.report.DailyReportSender
import com.xiaramteo.kiosk.report.SendOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** 받는 사람 입력에 대한 즉각 피드백. */
enum class RecipientError { INVALID, DUPLICATE }

data class SettingsUiState(
    val draft: MailSettings = MailSettings(),
    /** 이미 저장된 비밀번호가 있는지. 값 자체는 화면에 되읽어 보여주지 않는다. */
    val hasStoredPassword: Boolean = false,
    val pendingDays: Int = 0,
    val recipientError: RecipientError? = null,
    val sending: Boolean = false,
    val message: SettingsMessage? = null,
)

sealed interface SettingsMessage {
    data object Saved : SettingsMessage
    data class SendOk(val summary: String) : SettingsMessage
    data object SendNone : SettingsMessage
    data object SendNotReady : SettingsMessage
    data class SendFailed(val reason: String) : SettingsMessage
}

class SettingsViewModel(
    private val store: SettingsStore,
    private val recordStore: ScanRecordStore,
    private val sender: DailyReportSender,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            draft = store.current(),
            hasStoredPassword = store.current().password.isNotEmpty(),
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refreshPending()
    }

    fun setEnabled(value: Boolean) = edit { it.copy(enabled = value) }
    fun setHost(value: String) = edit { it.copy(host = value.trim()) }
    fun setUsername(value: String) = edit { it.copy(username = value.trim()) }
    fun setPassword(value: String) = edit { it.copy(password = value.trim()) }
    fun setSendAt(value: LocalTime) = edit { it.copy(sendAt = value) }

    fun setPort(value: String) = edit { current ->
        current.copy(port = value.filter(Char::isDigit).toIntOrNull() ?: current.port)
    }

    /** 받는 사람을 추가한다. 형식이 틀리거나 이미 있으면 거절하고 이유를 알린다. */
    fun addRecipient(raw: String) {
        val value = raw.trim()
        val error = when {
            !MailSettings.isValidEmail(value) -> RecipientError.INVALID
            _state.value.draft.recipients.any { it.equals(value, ignoreCase = true) } ->
                RecipientError.DUPLICATE
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(recipientError = error) }
            return
        }
        _state.update {
            it.copy(
                draft = it.draft.copy(recipients = it.draft.recipients + value),
                recipientError = null,
            )
        }
    }

    fun removeRecipient(value: String) = edit { current ->
        current.copy(recipients = current.recipients.filterNot { it == value })
    }

    fun clearRecipientError() = _state.update { it.copy(recipientError = null) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        store.save(_state.value.draft)
        _state.update {
            it.copy(hasStoredPassword = it.draft.password.isNotEmpty(), message = SettingsMessage.Saved)
        }
    }

    /** 저장한 뒤 밀린 기록을 바로 보낸다. 설정이 맞는지 사서가 즉시 확인하기 위한 것이다. */
    fun sendNow() {
        if (_state.value.sending) return
        store.save(_state.value.draft)
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = runCatching {
                sender.sendPending(store.current(), LocalDate.now())
            }.getOrElse { SendOutcome.Failed(LocalDate.now(), it) }

            _state.update {
                it.copy(
                    sending = false,
                    message = when (outcome) {
                        is SendOutcome.Sent ->
                            SettingsMessage.SendOk(outcome.dates.joinToString(", "))
                        SendOutcome.NothingToSend -> SettingsMessage.SendNone
                        SendOutcome.NotConfigured -> SettingsMessage.SendNotReady
                        is SendOutcome.Failed ->
                            SettingsMessage.SendFailed(
                                outcome.cause.message ?: outcome.cause::class.simpleName.orEmpty()
                            )
                    },
                )
            }
            refreshPending()
        }
    }

    private fun refreshPending() = viewModelScope.launch {
        val days = recordStore.unsentDates(upTo = LocalDate.now()).size
        _state.update { it.copy(pendingDays = days) }
    }

    private fun edit(block: (MailSettings) -> MailSettings) =
        _state.update { it.copy(draft = block(it.draft)) }

    class Factory(
        private val store: SettingsStore,
        private val recordStore: ScanRecordStore,
        private val sender: DailyReportSender,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(store, recordStore, sender) as T
    }
}
