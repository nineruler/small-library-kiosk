package com.xiaramteo.kiosk.data

import java.time.LocalDate

/**
 * 도서관 회원. [barcode]는 회원증에 인쇄된 바코드 값이다.
 *
 * 코라시스에서 회원 목록을 받아올 수 없는 상태에서는 바코드 말고는 아는 것이 없다.
 * 그래서 이름과 대출 권수는 없을 수 있고, 화면은 [displayName] 으로 대신 표시한다.
 */
data class Member(
    val memberId: String,
    val barcode: String,
    val name: String? = null,
    val loanCount: Int? = null,
    val loanLimit: Int? = null,
    val suspended: Boolean = false,
) {
    /** 이름을 모르면 회원번호로 대신 부른다. */
    val displayName: String get() = name ?: barcode

    /** 한도를 모르면 null. 화면은 이 값이 없으면 권수 안내를 숨긴다. */
    val remainingLoans: Int?
        get() = if (loanCount != null && loanLimit != null) {
            (loanLimit - loanCount).coerceAtLeast(0)
        } else {
            null
        }
}

/**
 * 장서 한 권. [barcode]는 책에 붙은 등록 바코드(ISBN이 아닌 관내 등록번호)다.
 *
 * 장서 목록 없이 운영할 때는 바코드만 알 수 있으므로 서지 항목은 모두 비어 있을 수 있다.
 */
data class BookItem(
    val itemId: String,
    val barcode: String,
    val title: String? = null,
    val author: String? = null,
    val callNumber: String? = null,
) {
    /** 제목을 모르면 바코드를 대신 보여준다. */
    val displayTitle: String get() = title ?: barcode

    /** 지은이·청구기호를 모르면 null. 화면은 이 값이 없으면 보조 줄을 숨긴다. */
    val displaySubtitle: String?
        get() = listOfNotNull(author, callNumber).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

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
