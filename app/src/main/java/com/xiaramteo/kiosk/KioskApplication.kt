package com.xiaramteo.kiosk

import android.app.Application
import com.xiaramteo.kiosk.data.LibrarySystem
import com.xiaramteo.kiosk.data.OfflineLibrarySystem
import com.xiaramteo.kiosk.data.local.CatalogStore
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.report.DailyReportSender
import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.MailConfigLoader
import com.xiaramteo.kiosk.settings.MailConfigResult
import java.io.File

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

    /**
     * 코라시스에서 내려받아 넣어 둔 소장자료 목록. 있으면 제목을 보여주고 바코드를 검증한다.
     */
    val catalogStore: CatalogStore by lazy {
        CatalogStore(File(getExternalFilesDir(null), "catalog.csv"))
    }

    val librarySystem: LibrarySystem by lazy {
        OfflineLibrarySystem(scanRecordStore, catalogStore)
    }

    /**
     * 메일 설정은 화면이 아니라 기기의 JSON 파일에서 읽는다.
     * 앱 전용 외부 저장소라 adb push 로 넣을 수 있고 다른 앱은 들여다볼 수 없다.
     */
    val mailConfigLoader: MailConfigLoader by lazy {
        MailConfigLoader(File(getExternalFilesDir(null), "kiosk-mail.json"))
    }

    val reportSender: DailyReportSender by lazy { DailyReportSender(scanRecordStore) }

    override fun onCreate() {
        super.onCreate()
        // 기기를 재부팅해도 예약이 살아 있도록 실행할 때마다 다시 걸어 둔다.
        // 설정 파일이 아직 없으면 기본 시각으로 걸어 두고, 작업이 돌 때 다시 읽는다.
        val settings = (mailConfigLoader.load() as? MailConfigResult.Loaded)?.settings
        DailyReportWorker.schedule(this, settings ?: com.xiaramteo.kiosk.settings.MailSettings())
    }
}
