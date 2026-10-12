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

data class SettingsUiState(
    val config: MailConfigResult = MailConfigResult.Missing(""),
    val pendingDays: Int = 0,
    val sending: Boolean = false,
    val message: SettingsMessage? = null,
)

sealed interface SettingsMessage {
    data class SendOk(val summary: String) : SettingsMessage
    data object SendNone : SettingsMessage
    data object SendNotReady : SettingsMessage
    data class SendFailed(val reason: String) : SettingsMessage
}

/**
 * 사서가 설정 상태를 확인하고 즉시 발송을 시험해 보는 화면의 상태.
 *
 * 설정은 화면에서 고치지 않고 기기의 JSON 파일에서 읽는다([MailConfigLoader]).
 * 파일을 바꾼 뒤 이 화면을 다시 열면 [refresh] 가 새로 읽는다.
 */
class SettingsViewModel(
    private val loader: MailConfigLoader,
    private val recordStore: ScanRecordStore,
    private val sender: DailyReportSender,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(config = loader.load()) }
        viewModelScope.launch {
            val days = recordStore.unsentDates(upTo = LocalDate.now()).size
            _state.update { it.copy(pendingDays = days) }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** 설정이 맞는지 사서가 바로 확인할 수 있도록 밀린 기록을 지금 보낸다. */
    fun sendNow() {
        if (_state.value.sending) return
        val settings = (_state.value.config as? MailConfigResult.Loaded)?.settings
        if (settings == null) {
            _state.update { it.copy(message = SettingsMessage.SendNotReady) }
            return
        }
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            val outcome = runCatching { sender.sendPending(settings, LocalDate.now()) }
                .getOrElse { SendOutcome.Failed(LocalDate.now(), it) }

            _state.update {
                it.copy(
                    sending = false,
                    message = when (outcome) {
                        is SendOutcome.Sent -> SettingsMessage.SendOk(outcome.dates.joinToString(", "))
                        SendOutcome.NothingToSend -> SettingsMessage.SendNone
                        SendOutcome.NotConfigured -> SettingsMessage.SendNotReady
                        is SendOutcome.Failed -> SettingsMessage.SendFailed(
                            outcome.cause.message ?: outcome.cause::class.simpleName.orEmpty()
                        )
                    },
                )
            }
            refresh()
        }
    }

    class Factory(
        private val loader: MailConfigLoader,
        private val recordStore: ScanRecordStore,
        private val sender: DailyReportSender,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(loader, recordStore, sender) as T
    }
}
