package com.xiaramteo.kiosk

import android.app.Application
import com.xiaramteo.kiosk.data.LibrarySystem
import com.xiaramteo.kiosk.data.OfflineLibrarySystem
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.report.DailyReportSender
import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.SettingsStore

/**
 * 의존성을 한곳에서 묶는 자리.
 *
 * 코라시스는 관리자 로그인에 2단계 인증을 요구해 키오스크가 무인으로 접속할 수 없다.
 * 그래서 당분간은 기기에 기록만 남기고, 사서가 그 기록을 보고 코라시스에 반영한다.
 * 연동 방식이 정해지면 [librarySystem] 구현만 바꾸면 되고 화면·상태 코드는 손대지 않는다.
 */
class KioskApplication : Application() {

    /** 대출/반납 기록 원본. 발송이나 내보내기도 모두 이 저장소를 본다. */
    val scanRecordStore: ScanRecordStore by lazy { ScanRecordStore(filesDir) }

    val librarySystem: LibrarySystem by lazy { OfflineLibrarySystem(scanRecordStore) }

    /** 메일 설정. 앱 비밀번호가 들어가므로 암호화해서 보관한다. */
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }

    val reportSender: DailyReportSender by lazy { DailyReportSender(scanRecordStore) }

    override fun onCreate() {
        super.onCreate()
        // 기기를 재부팅해도 예약이 살아 있도록 실행할 때마다 다시 걸어 둔다.
        DailyReportWorker.schedule(this, settingsStore.current())
    }
}
