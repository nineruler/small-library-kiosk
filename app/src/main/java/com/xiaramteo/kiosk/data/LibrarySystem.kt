package com.xiaramteo.kiosk.data

/**
 * 도서관 관리 시스템(코라시스) 연동 지점.
 *
 * 실제 연동 방식(REST API / 웹 자동화 / DB)이 정해지면 이 인터페이스의 구현만 갈아끼우면 되도록
 * 화면·상태 코드는 이 타입에만 의존한다. 모든 함수는 호출 스레드를 막지 않는 suspend 함수이며,
 * 실패는 [LibrarySystemException]으로 던진다.
 */
interface LibrarySystem {

    /** 회원증 바코드(또는 직접 입력한 회원번호)로 회원을 조회한다. */
    suspend fun findMember(barcode: String): Member

    /** 책 바코드로 장서를 조회한다. */
    suspend fun findItem(barcode: String): BookItem

    /** 책 한 권을 대출 처리한다. */
    suspend fun borrow(member: Member, item: BookItem): ItemResult

    /** 책 한 권을 반납 처리한다. 반납은 회원 인증 없이 가능하다. */
    suspend fun giveBack(item: BookItem): ItemResult
}
