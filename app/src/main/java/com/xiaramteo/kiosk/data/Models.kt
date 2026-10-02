package com.xiaramteo.kiosk.data

import java.time.LocalDate

/** 도서관 회원. [barcode]는 회원증에 인쇄된 바코드 값이다. */
data class Member(
    val memberId: String,
    val barcode: String,
    val name: String,
    val loanCount: Int,
    val loanLimit: Int,
    val suspended: Boolean = false,
) {
    val remainingLoans: Int get() = (loanLimit - loanCount).coerceAtLeast(0)
}

/** 장서 한 권. [barcode]는 책에 붙은 등록 바코드(ISBN이 아닌 관내 등록번호)다. */
data class BookItem(
    val itemId: String,
    val barcode: String,
    val title: String,
    val author: String,
    val callNumber: String,
)

/** 대출/반납 처리 결과. 실패 사유는 UI가 문구를 고를 수 있도록 [KioskError]로 표현한다. */
sealed interface ItemResult {
    val item: BookItem

    data class Borrowed(override val item: BookItem, val dueDate: LocalDate) : ItemResult
    data class Returned(override val item: BookItem, val overdueDays: Int) : ItemResult
    data class Failed(override val item: BookItem, val error: KioskError) : ItemResult
}

/** 키오스크가 사용자에게 보여줄 수 있는 실패 사유. */
enum class KioskError {
    UNKNOWN_MEMBER,
    MEMBER_SUSPENDED,
    UNKNOWN_ITEM,
    ALREADY_LOANED,
    NOT_LOANED,
    LIMIT_EXCEEDED,
    DUPLICATE_SCAN,
    NETWORK,
    GENERIC,
}

/** 연동 계층이 던지는 예외. 화면단에서 [error]만 보고 문구를 고른다. */
class LibrarySystemException(val error: KioskError, cause: Throwable? = null) :
    Exception(error.name, cause)
