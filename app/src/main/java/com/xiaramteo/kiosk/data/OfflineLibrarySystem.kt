package com.xiaramteo.kiosk.data

import com.xiaramteo.kiosk.data.local.CatalogResult
import com.xiaramteo.kiosk.data.local.CatalogStore
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
 * 장서 목록([catalog])을 넣어 두면 제목을 보여주고 등록되지 않은 바코드를 걸러낸다.
 * 목록이 없으면 예전처럼 검증 없이 바코드를 그대로 받는다. 목록을 넣는 순간 검증이 켜지는 셈이다.
 *
 * 회원 목록은 아직 쓰지 않는다. 개인정보라 반출이 조심스러워 회원은 번호만 기록한다.
 */
class OfflineLibrarySystem(
    private val store: ScanRecordStore,
    private val catalog: CatalogStore? = null,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val loanPeriodDays: Long = 14,
) : LibrarySystem {

    override suspend fun findMember(barcode: String): Member =
        Member(memberId = barcode, barcode = barcode)

    override suspend fun findItem(barcode: String): BookItem {
        val loaded = catalog?.load() as? CatalogResult.Loaded
            // 목록이 없거나 읽히지 않으면 검증하지 않는다. 목록 문제로 대출이 막히면 안 된다.
            ?: return BookItem(itemId = barcode, barcode = barcode)

        return loaded.items[barcode]
            ?: throw LibrarySystemException(KioskError.UNKNOWN_ITEM)
    }

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
