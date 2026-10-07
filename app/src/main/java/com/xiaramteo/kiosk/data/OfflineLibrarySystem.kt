package com.xiaramteo.kiosk.data

import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * 코라시스에 실시간으로 쓸 수 없는 동안 쓰는 구현.
 *
 * 코라시스는 관리자 로그인에 2단계 인증을 요구해서 키오스크가 무인으로 접속할 수 없다.
 * 그래서 이 구현은 **검증하지 않고 기록만 한다.** 찍힌 바코드를 그대로 받아 적고,
 * 실제 대출 가능 여부(이미 대출 중인지, 연체인지)는 나중에 사서가 코라시스에서 확인한다.
 *
 * 장서·회원 목록이 없으므로 제목과 이름도 알 수 없다. 화면은 [BookItem.displayTitle] 처럼
 * 바코드로 대신 표시한다. 나중에 목록을 확보하면 여기에 조회를 붙이면 된다.
 */
class OfflineLibrarySystem(
    private val store: ScanRecordStore,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val loanPeriodDays: Long = 14,
) : LibrarySystem {

    override suspend fun findMember(barcode: String): Member =
        Member(memberId = barcode, barcode = barcode)

    override suspend fun findItem(barcode: String): BookItem =
        BookItem(itemId = barcode, barcode = barcode)

    override suspend fun borrow(member: Member, item: BookItem): ItemResult {
        record(RecordMode.BORROW, member.barcode, item.barcode)
        return ItemResult.Borrowed(item, LocalDate.now(clock).plusDays(loanPeriodDays))
    }

    override suspend fun giveBack(item: BookItem): ItemResult {
        record(RecordMode.RETURN, memberBarcode = null, itemBarcode = item.barcode)
        // 대출 기록이 없으니 연체 여부를 알 수 없다. 연체는 사서가 코라시스에서 확인한다.
        return ItemResult.Returned(item, overdueDays = 0)
    }

    private suspend fun record(mode: RecordMode, memberBarcode: String?, itemBarcode: String) {
        store.append(
            ScanRecord(
                recordedAt = ZonedDateTime.now(clock),
                mode = mode,
                memberBarcode = memberBarcode,
                itemBarcode = itemBarcode,
            )
        )
    }
}
