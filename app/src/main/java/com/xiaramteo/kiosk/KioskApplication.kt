package com.xiaramteo.kiosk

import android.app.Application
import android.util.Log
import com.xiaramteo.kiosk.data.LibrarySystem
import com.xiaramteo.kiosk.data.OfflineLibrarySystem
import com.xiaramteo.kiosk.data.local.BundledData
import com.xiaramteo.kiosk.data.local.CatalogStore
import com.xiaramteo.kiosk.data.local.MemberDirectoryStore
import com.xiaramteo.kiosk.data.local.ScanRecordStore
import com.xiaramteo.kiosk.report.DailyReportMailer
import com.xiaramteo.kiosk.report.DailyReportSender
import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.MailConfigLoader
import com.xiaramteo.kiosk.update.ListUpdater
import com.xiaramteo.kiosk.update.MailboxUpdates
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
        CatalogStore.inDirectory(getExternalFilesDir(null)!!, "catalog")
    }

    /**
     * 회원 목록. 이름은 사서가 내보낼 때 가려서 넣는다(예: "구*린").
     * 로비에 둔 기기라 전체 이름을 두지 않는 쪽이 분실·어깨너머 노출 모두에 안전하다.
     */
    val memberDirectoryStore: MemberDirectoryStore by lazy {
        MemberDirectoryStore.inDirectory(getExternalFilesDir(null)!!, "members")
    }

    val librarySystem: LibrarySystem by lazy {
        OfflineLibrarySystem(scanRecordStore, catalogStore, memberDirectoryStore)
    }

    /**
     * 메일 설정은 화면이 아니라 기기의 JSON 파일에서 읽는다.
     * 앱 전용 외부 저장소라 adb push 로 넣을 수 있고 다른 앱은 들여다볼 수 없다.
     */
    val mailConfigLoader: MailConfigLoader by lazy {
        MailConfigLoader(File(getExternalFilesDir(null), "kiosk-mail.json"))
    }

    val reportSender: DailyReportSender by lazy { DailyReportSender(scanRecordStore) }

    /**
     * 사서가 메일로 보낸 목록을 받아 간다. 도서관에 설치해 둔 기기라 목록을 바꾸러
     * 매번 찾아갈 수 없어서, 이미 쓰는 메일 계정의 받은편지함을 통로로 쓴다.
     */
    val mailboxUpdates: MailboxUpdates by lazy {
        MailboxUpdates(
            updater = ListUpdater(getExternalFilesDir(null)!!),
            mailer = DailyReportMailer(),
            // 확인 시각은 앱 전용 내부 저장소에 둔다. 사서가 볼 일도 지울 일도 없는 값이다.
            lastCheckedAt = File(filesDir, "mailbox-checked-at"),
        )
    }

    override fun onCreate() {
        super.onCreate()
        // 앱에 같이 넣어 둔 목록을 꺼내 놓는다. 기기에 이미 있는 파일은 그대로 둔다.
        getExternalFilesDir(null)?.let { dir ->
            BundledData.unpackMissing(assets, dir).forEach { Log.i("Kiosk", "번들 목록을 꺼냈습니다: $it") }
        }

        // 기기를 재부팅해도 예약이 살아 있도록 실행할 때마다 다시 걸어 둔다.
        // 발송 시각은 작업이 돌 때마다 설정 파일에서 다시 읽으므로 여기서는 알 필요가 없다.
        DailyReportWorker.schedule(this)
    }
}
