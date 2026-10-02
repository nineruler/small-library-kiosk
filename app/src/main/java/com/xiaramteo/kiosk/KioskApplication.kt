package com.xiaramteo.kiosk

import android.app.Application
import com.xiaramteo.kiosk.data.LibrarySystem
import com.xiaramteo.kiosk.data.MockLibrarySystem

/**
 * 의존성을 한곳에서 묶는 자리.
 *
 * 코라시스 연동 방식이 정해지면 [librarySystem]만 실제 구현으로 바꾸면 되고,
 * 화면·상태 코드는 손대지 않는다.
 */
class KioskApplication : Application() {

    val librarySystem: LibrarySystem by lazy { MockLibrarySystem() }
}
