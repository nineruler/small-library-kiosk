package com.xiaramteo.kiosk.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaramteo.kiosk.data.local.CatalogResult
import com.xiaramteo.kiosk.data.local.CatalogStore
import com.xiaramteo.kiosk.data.local.DaySummary
import com.xiaramteo.kiosk.data.local.MemberDirectoryResult
import com.xiaramteo.kiosk.data.local.MemberDirectoryStore
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
    val selectedRecords: List<RecordView> = emptyList(),
)

/**
 * 기록 한 줄에 목록에서 찾은 책 제목과 회원명을 붙인 것.
 *
 * 기록 파일에는 바코드만 남긴다(메일로 나가는 내용에 이름이 섞이면 안 되기 때문).
 * 사서가 화면에서 볼 때만 목록을 뒤져 사람이 읽을 수 있는 말로 바꿔 준다.
 * 목록에 없는 바코드면 [title]·[memberName] 이 null 이고, 화면은 바코드를 그대로 보여준다.
 */
data class RecordView(
    val record: ScanRecord,
    val title: String? = null,
    val memberName: String? = null,
)

sealed interface SettingsMessage {
    data class SendOk(val summary: String) : SettingsMessage
    data object SendNone : SettingsMessage
    data object SendNotReady : SettingsMessage
    data class SendFailed(val reason: String) : SettingsMessage
    data object Deleted : SettingsMessage
    data object DeleteFailed : SettingsMessage
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
    private val catalog: CatalogStore? = null,
    private val directory: MemberDirectoryStore? = null,
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
            val records = withNames(recordStore.readRecords(date).map(::RecordView))
            _state.update {
                if (it.selectedDate == date) it.copy(selectedRecords = records) else it
            }
        }
    }

    /** 바코드만 남은 기록에 목록에서 찾은 제목과 이름을 붙인다. 목록이 없으면 그대로 둔다. */
    private suspend fun withNames(views: List<RecordView>): List<RecordView> {
        if (views.isEmpty()) return views
        val items = (catalog?.load() as? CatalogResult.Loaded)?.items.orEmpty()
        val members = (directory?.load() as? MemberDirectoryResult.Loaded)?.members.orEmpty()
        if (items.isEmpty() && members.isEmpty()) return views
        return views.map { view ->
            view.copy(
                title = items[view.record.itemBarcode]?.title,
                memberName = view.record.memberBarcode?.let { members[it] }?.name,
            )
        }
    }

    /**
     * 그날 [index] 번째 기록 한 건을 지운다. 잘못 찍힌 것을 사서가 걷어내기 위한 것이다.
     * 되돌릴 수 없으므로 화면에서 한 번 더 묻고 호출한다.
     */
    fun deleteRecord(index: Int) {
        val date = _state.value.selectedDate ?: return
        viewModelScope.launch {
            val removed = runCatching { recordStore.deleteRecord(date, index) }.getOrDefault(false)
            _state.update {
                it.copy(
                    message = if (removed) SettingsMessage.Deleted else SettingsMessage.DeleteFailed,
                )
            }
            // 마지막 한 건이었다면 그날이 통째로 사라지므로 선택도 비우고 다시 읽는다.
            if (removed) _state.update { it.copy(selectedDate = null, selectedRecords = emptyList()) }
            refresh()
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
        private val catalog: CatalogStore? = null,
        private val directory: MemberDirectoryStore? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(loader, recordStore, sender, catalog, directory) as T
    }
}
