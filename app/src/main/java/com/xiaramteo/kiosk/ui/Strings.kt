package com.xiaramteo.kiosk.ui

import androidx.annotation.StringRes
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.data.KioskError

/** 실패 사유를 이용자가 읽을 문구로 옮긴다. */
@StringRes
fun KioskError.messageRes(): Int = when (this) {
    KioskError.UNKNOWN_MEMBER -> R.string.error_unknown_member
    KioskError.MEMBER_SUSPENDED -> R.string.error_member_suspended
    KioskError.UNKNOWN_ITEM -> R.string.error_unknown_item
    KioskError.ALREADY_LOANED -> R.string.error_already_loaned
    KioskError.NOT_LOANED -> R.string.error_not_loaned
    KioskError.LIMIT_EXCEEDED -> R.string.error_limit_exceeded
    KioskError.DUPLICATE_SCAN -> R.string.error_duplicate_scan
    KioskError.NETWORK -> R.string.error_network
    KioskError.GENERIC -> R.string.error_generic
}
