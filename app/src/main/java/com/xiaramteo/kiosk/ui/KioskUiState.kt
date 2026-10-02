package com.xiaramteo.kiosk.ui

import com.xiaramteo.kiosk.data.BookItem
import com.xiaramteo.kiosk.data.ItemResult
import com.xiaramteo.kiosk.data.KioskError
import com.xiaramteo.kiosk.data.Member

/** 키오스크가 지금 무엇을 하고 있는지. 화면은 이 상태 하나만 보고 그린다. */
sealed interface KioskUiState {

    data object Home : KioskUiState

    /** 대출 첫 단계: 회원증 인식. */
    data class MemberScan(
        val busy: Boolean = false,
        val error: KioskError? = null,
    ) : KioskUiState

    /** 책 바코드를 여러 권 모으는 단계. [member]가 null이면 반납이다. */
    data class ItemScan(
        val mode: KioskMode,
        val member: Member? = null,
        val scanned: List<BookItem> = emptyList(),
        val busy: Boolean = false,
        val error: KioskError? = null,
    ) : KioskUiState

    /** 코라시스에 대출/반납을 기록하는 중. */
    data class Submitting(val mode: KioskMode) : KioskUiState

    data class Finished(
        val mode: KioskMode,
        val memberName: String?,
        val results: List<ItemResult>,
    ) : KioskUiState {
        val succeeded: List<ItemResult> get() = results.filter { it !is ItemResult.Failed }
        val failed: List<ItemResult.Failed> get() = results.filterIsInstance<ItemResult.Failed>()
    }
}

enum class KioskMode { BORROW, RETURN }
