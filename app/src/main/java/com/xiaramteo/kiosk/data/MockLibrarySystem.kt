package com.xiaramteo.kiosk.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * 코라시스 연동이 정해지기 전까지 쓰는 메모리 기반 가짜 구현.
 *
 * 실제 서버처럼 보이도록 짧은 지연을 넣고, 대출 상태를 프로세스 안에서 유지한다.
 * 앱을 재시작하면 초기 데이터로 돌아간다.
 */
class MockLibrarySystem(
    private val latencyMillis: Long = 350,
    private val loanPeriodDays: Long = 14,
) : LibrarySystem {

    private val mutex = Mutex()

    private val members = mutableMapOf(
        "1000000001" to Member("M001", "1000000001", "김하늘", loanCount = 1, loanLimit = 5),
        "1000000002" to Member("M002", "1000000002", "박도윤", loanCount = 5, loanLimit = 5),
        "1000000003" to Member("M003", "1000000003", "이서아", loanCount = 0, loanLimit = 5, suspended = true),
    )

    /**
     * 앞의 다섯 권은 ISBN 을 바코드로 쓰는 가상 데이터이고, 마지막 한 권은 실제 자이아람터
     * 장서 라벨("EM" + 10자리, CODE-128)을 리더기로 읽어 넣은 실물 값이다.
     * 실제 등록번호 체계를 코드가 다룰 수 있는지 확인하는 용도로 남겨 둔다.
     */
    private val items = listOf(
        BookItem("I001", "9788936434120", "소년이 온다", "한강", "813.7-한11ㅅ"),
        BookItem("I002", "9788932917245", "참을 수 없는 존재의 가벼움", "밀란 쿤데라", "863-쿤26ㅊ"),
        BookItem("I003", "9788954682152", "완전한 행복", "정유정", "813.7-정66ㅇ"),
        BookItem("I004", "9791196769321", "아기 돼지 삼형제", "그림책편집부", "아813-ㄱ14ㅇ"),
        BookItem("I005", "9788937473135", "데미안", "헤르만 헤세", "853-헤53ㄷ"),
        BookItem("I006", "EM0000000263", "어두워지면 일어나라", "샬레인 해리스", "843-해239ㅇ"),
    ).associateBy { it.barcode }.toMutableMap()

    /** 책 바코드 -> 대출한 회원 ID. 비어 있으면 서가에 있는 책이다. */
    private val loans = mutableMapOf("9788937473135" to "M001")

    /** 연체 상황을 보여주기 위한 고정 데이터. */
    private val dueDates = mutableMapOf("9788937473135" to LocalDate.now().minusDays(3))

    override suspend fun findMember(barcode: String): Member {
        delay(latencyMillis)
        return mutex.withLock { members[barcode.trim()] }
            ?: throw LibrarySystemException(KioskError.UNKNOWN_MEMBER)
    }

    override suspend fun findItem(barcode: String): BookItem {
        delay(latencyMillis)
        return mutex.withLock { items[barcode.trim()] }
            ?: throw LibrarySystemException(KioskError.UNKNOWN_ITEM)
    }

    override suspend fun borrow(member: Member, item: BookItem): ItemResult {
        delay(latencyMillis)
        return mutex.withLock {
            val current = members[member.barcode] ?: return@withLock ItemResult.Failed(item, KioskError.UNKNOWN_MEMBER)
            when {
                current.suspended -> ItemResult.Failed(item, KioskError.MEMBER_SUSPENDED)
                loans.containsKey(item.barcode) -> ItemResult.Failed(item, KioskError.ALREADY_LOANED)
                current.remainingLoans <= 0 -> ItemResult.Failed(item, KioskError.LIMIT_EXCEEDED)
                else -> {
                    loans[item.barcode] = current.memberId
                    val due = LocalDate.now().plusDays(loanPeriodDays)
                    dueDates[item.barcode] = due
                    members[current.barcode] = current.copy(loanCount = current.loanCount + 1)
                    ItemResult.Borrowed(item, due)
                }
            }
        }
    }

    override suspend fun giveBack(item: BookItem): ItemResult {
        delay(latencyMillis)
        return mutex.withLock {
            val borrowerId = loans.remove(item.barcode)
                ?: return@withLock ItemResult.Failed(item, KioskError.NOT_LOANED)
            val due = dueDates.remove(item.barcode)
            members.entries.firstOrNull { it.value.memberId == borrowerId }?.let { (key, borrower) ->
                members[key] = borrower.copy(loanCount = (borrower.loanCount - 1).coerceAtLeast(0))
            }
            val overdue = due?.let { java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: 0
            ItemResult.Returned(item, overdue.coerceAtLeast(0).toInt())
        }
    }
}
