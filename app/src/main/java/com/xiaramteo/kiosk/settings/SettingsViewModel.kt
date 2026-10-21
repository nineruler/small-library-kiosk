package com.xiaramteo.kiosk.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaramteo.kiosk.data.local.DaySummary
import com.xiaramteo.kiosk.data.local.ScanRecord
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
    /** 비밀번호를 통과했는지. 설정에 비밀번호가 없으면 처음부터 통과로 둔다. */
    val unlocked: Boolean = false,
    val pinWrong: Boolean = false,
    val days: List<DaySummary> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedRecords: List<ScanRecord> = emptyList(),
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
        val config = loader.load()
        val pin = (config as? MailConfigResult.Loaded)?.settings?.adminPin.orEmpty()
        _state.update {
            // 비밀번호를 정해 두지 않았으면 잠그지 않는다. 설정 전에 사서가 갇히면 곤란하다.
            it.copy(config = config, unlocked = it.unlocked || pin.isEmpty())
        }
        viewModelScope.launch {
            val pending = recordStore.unsentDates(upTo = LocalDate.now()).size
            val days = recordStore.daySummaries()
            _state.update { current ->
                val selected = current.selectedDate ?: days.firstOrNull()?.date
                current.copy(pendingDays = pending, days = days, selectedDate = selected)
            }
            _state.value.selectedDate?.let { selectDate(it) }
        }
    }

    /** 입력한 비밀번호를 확인한다. */
    fun submitPin(pin: String) {
        val expected = (_state.value.config as? MailConfigResult.Loaded)?.settings?.adminPin.orEmpty()
        if (expected.isNotEmpty() && pin == expected) {
            _state.update { it.copy(unlocked = true, pinWrong = false) }
        } else {
            _state.update { it.copy(pinWrong = true) }
        }
    }

    fun selectDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
        viewModelScope.launch {
            val records = recordStore.readRecords(date)
            _state.update {
                if (it.selectedDate == date) it.copy(selectedRecords = records) else it
            }
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
