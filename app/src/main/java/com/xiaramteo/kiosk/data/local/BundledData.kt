package com.xiaramteo.kiosk.data.local

import android.content.res.AssetManager
import android.util.Log
import java.io.File

/**
 * 앱에 같이 넣어 둔 소장자료·회원 목록을 기기 저장소로 꺼내 놓는다.
 *
 * 사서가 adb 를 쓸 줄 몰라도 설치만 하면 목록이 들어 있게 하려는 것이다.
 * **이미 있는 파일은 건드리지 않는다.** 사서가 새로 내보내 넣은 최신 목록을
 * 앱 업데이트가 예전 것으로 덮어쓰면 안 되기 때문이다. 번들된 목록은 어디까지나
 * 처음 설치했을 때의 출발점이고, 그 뒤로는 기기에 넣은 파일이 늘 이긴다.
 */
object BundledData {

    private const val TAG = "BundledData"
    private const val DIR = "datas"

    /** [target] 에 없는 파일만 꺼내 놓는다. 꺼낸 파일 이름을 돌려준다. */
    fun unpackMissing(assets: AssetManager, target: File): List<String> {
        val names = runCatching { assets.list(DIR)?.toList() }
            .onFailure { Log.w(TAG, "번들 목록을 읽지 못했습니다", it) }
            .getOrNull()
            .orEmpty()

        return names.filter { name ->
            val out = File(target, name)
            if (out.exists()) return@filter false
            runCatching {
                assets.open("$DIR/$name").use { input ->
                    out.outputStream().use(input::copyTo)
                }
            }.onFailure {
                Log.w(TAG, "$name 을 꺼내지 못했습니다", it)
                out.delete()
            }.isSuccess
        }
    }
}
