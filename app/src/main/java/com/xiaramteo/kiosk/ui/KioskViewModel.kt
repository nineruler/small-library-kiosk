package com.xiaramteo.kiosk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xiaramteo.kiosk.data.BookItem
import com.xiaramteo.kiosk.data.ItemResult
import com.xiaramteo.kiosk.data.KioskError
import com.xiaramteo.kiosk.data.LibrarySystem
import com.xiaramteo.kiosk.data.LibrarySystemException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 키오스크 전체 흐름을 담당하는 상태 머신.
 *
 * 바코드는 카메라 분석 스레드에서 쏟아져 들어오므로, 한 번에 한 건만 처리하도록
 * [scanLock]으로 직렬화하고 처리 중에는 [KioskUiState.ItemScan.busy]를 세워 중복 입력을 막는다.
 */
class KioskViewModel(
    private val library: LibrarySystem,
    private val idleTimeout: IdleTimeout = IdleTimeout(),
) : ViewModel() {

    private val _state = MutableStateFlow<KioskUiState>(KioskUiState.Home)
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    /** 결과 화면에서 "몇 초 뒤 처음으로" 를 보여주기 위한 남은 시간. */
    private val _secondsUntilHome = MutableStateFlow<Int?>(null)
    val secondsUntilHome: StateFlow<Int?> = _secondsUntilHome.asStateFlow()

    private val scanLock = Mutex()
    private var idleJob: Job? = null

    fun startBorrow() = moveTo(KioskUiState.MemberScan())

    fun startReturn() = moveTo(KioskUiState.ItemScan(mode = KioskMode.RETURN))

    fun goHome() {
        idleJob?.cancel()
        _secondsUntilHome.value = null
        _state.value = KioskUiState.Home
    }

    /** 회원증을 다시 찍고 싶을 때처럼, 한 단계 뒤로. */
    fun goBack() {
        when (val current = _state.value) {
            is KioskUiState.ItemScan ->
                if (current.mode == KioskMode.BORROW) moveTo(KioskUiState.MemberScan()) else goHome()
            else -> goHome()
        }
    }

    /** 카메라/직접 입력 어느 쪽에서 들어온 바코드든 이 함수로 모인다. */
    fun onBarcode(raw: String) {
        val value = raw.trim()
        if (value.isEmpty()) return
        viewModelScope.launch {
            if (!scanLock.tryLock()) return@launch
            try {
                when (val current = _state.value) {
                    is KioskUiState.MemberScan -> handleMemberScan(value)
                    is KioskUiState.ItemScan -> handleItemScan(current, value)
                    else -> Unit
                }
            } finally {
                scanLock.unlock()
            }
        }
    }

    private suspend fun handleMemberScan(barcode: String) {
        _state.value = KioskUiState.MemberScan(busy = true)
        try {
            val member = library.findMember(barcode)
            if (member.suspended) {
                _state.value = KioskUiState.MemberScan(error = KioskError.MEMBER_SUSPENDED)
                restartIdleTimer()
                return
            }
            moveTo(KioskUiState.ItemScan(mode = KioskMode.BORROW, member = member))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = KioskUiState.MemberScan(error = e.toKioskError())
            restartIdleTimer()
        }
    }

    private suspend fun handleItemScan(current: KioskUiState.ItemScan, barcode: String) {
        if (current.scanned.any { it.barcode == barcode }) {
            _state.value = current.copy(error = KioskError.DUPLICATE_SCAN)
            restartIdleTimer()
            return
        }
        _state.value = current.copy(busy = true, error = null)
        try {
            val item = library.findItem(barcode)
            _state.value = current.copy(scanned = current.scanned + item, busy = false, error = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = current.copy(busy = false, error = e.toKioskError())
        }
        restartIdleTimer()
    }

    /** 목록에서 잘못 찍은 책을 뺀다. */
    fun removeScanned(item: BookItem) {
        _state.update { current ->
            if (current is KioskUiState.ItemScan) {
                current.copy(scanned = current.scanned.filterNot { it.barcode == item.barcode }, error = null)
            } else {
                current
            }
        }
        restartIdleTimer()
    }

    /** "다 했어요" — 모아둔 책을 코라시스에 한 권씩 기록한다. */
    fun submit() {
        val current = _state.value as? KioskUiState.ItemScan ?: return
        if (current.scanned.isEmpty()) return
        idleJob?.cancel()
        _state.value = KioskUiState.Submitting(current.mode)
        viewModelScope.launch {
            val results = current.scanned.map { item ->
                try {
                    when (current.mode) {
                        KioskMode.BORROW -> {
                            val member = current.member
                                ?: return@map ItemResult.Failed(item, KioskError.UNKNOWN_MEMBER)
                            library.borrow(member, item)
                        }
                        KioskMode.RETURN -> library.giveBack(item)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ItemResult.Failed(item, e.toKioskError())
                }
            }
            moveTo(
                KioskUiState.Finished(
                    mode = current.mode,
                    memberName = current.member?.name,
                    results = results,
                )
            )
        }
    }

    private fun moveTo(next: KioskUiState) {
        _state.value = next
        restartIdleTimer()
    }

    /**
     * 이용자가 자리를 떠난 채 남은 화면이 다음 사람에게 보이지 않도록,
     * 일정 시간 입력이 없으면 처음 화면으로 돌아간다.
     */
    private fun restartIdleTimer() {
        idleJob?.cancel()
        val seconds = idleTimeout.secondsFor(_state.value)
        if (seconds == null) {
            _secondsUntilHome.value = null
            return
        }
        idleJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                _secondsUntilHome.value = remaining
                delay(1_000)
            }
            _secondsUntilHome.value = null
            _state.value = KioskUiState.Home
        }
    }

    class Factory(
        private val library: LibrarySystem,
        private val idleTimeout: IdleTimeout = IdleTimeout(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            KioskViewModel(library, idleTimeout) as T
    }
}

/** 화면별 무입력 복귀 시간(초). null이면 그 화면에서는 자동 복귀하지 않는다. */
data class IdleTimeout(
    val scanSeconds: Int? = 90,
    val finishedSeconds: Int? = 20,
) {
    fun secondsFor(state: KioskUiState): Int? = when (state) {
        KioskUiState.Home, is KioskUiState.Submitting -> null
        is KioskUiState.MemberScan, is KioskUiState.ItemScan -> scanSeconds
        is KioskUiState.Finished -> finishedSeconds
    }
}

private fun Exception.toKioskError(): KioskError = when (this) {
    is LibrarySystemException -> error
    is java.io.IOException -> KioskError.NETWORK
    else -> KioskError.GENERIC
}
