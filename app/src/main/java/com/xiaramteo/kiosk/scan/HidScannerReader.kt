package com.xiaramteo.kiosk.scan

import android.view.KeyEvent

/**
 * HID(키보드 에뮬레이션) 방식 바코드 리더기 입력을 모은다.
 *
 * 이런 리더기는 OS에 외장 키보드로 잡혀서, 바코드를 읽으면 문자를 아주 빠르게 연속 입력한 뒤
 * Enter 를 보낸다. 그래서 Enter 가 올 때까지 문자를 모았다가 한 건의 바코드로 넘긴다.
 *
 * 사람이 키보드로 천천히 치는 것과 구분하려고 [maxGapMillis] 간격 제한을 둔다. 리더기는 문자
 * 사이가 수 ms 인 반면 사람은 훨씬 느리므로, 간격이 벌어지면 모아둔 내용을 버리고 다시 시작한다.
 * 덕분에 리더기가 중간에 읽다 만 입력이 다음 스캔에 섞이지 않는다.
 *
 * 화면 어디에 있든 동작해야 하므로 Activity 의 dispatchKeyEvent 에서 가장 먼저 호출한다.
 * 키오스크에는 물리 키보드가 없고 회원번호 직접 입력은 소프트 키보드(InputConnection)를 쓰므로,
 * 하드웨어 키 이벤트를 가로채도 화면 입력과 부딪히지 않는다.
 */
class HidScannerReader(
    private val maxGapMillis: Long = 200,
    private val onBarcode: (String) -> Unit,
) {
    private val buffer = StringBuilder()
    private var lastKeyAt = 0L

    /** [KeyEvent] 어댑터. 소비했으면 true 를 돌려 화면으로 내려가지 않게 한다. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            // 소비한 키의 ACTION_UP 도 함께 삼켜야 짝이 맞는다.
            return event.action == KeyEvent.ACTION_UP && isScannerKey(event.keyCode, event.unicodeChar)
        }
        return onKey(event.keyCode, event.unicodeChar, event.eventTime)
    }

    /** 테스트와 어댑터가 함께 쓰는 본체. Android 타입에 의존하지 않는다. */
    fun onKey(keyCode: Int, unicodeChar: Int, eventTimeMillis: Long): Boolean {
        if (!isScannerKey(keyCode, unicodeChar)) return false

        if (eventTimeMillis - lastKeyAt > maxGapMillis) buffer.setLength(0)
        lastKeyAt = eventTimeMillis

        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            val value = buffer.toString().trim()
            buffer.setLength(0)
            if (value.isNotEmpty()) {
                android.util.Log.i(TAG, "리더기 입력: $value")
                onBarcode(value)
            }
            return true
        }
        buffer.append(unicodeChar.toChar())
        return true
    }

    private fun isScannerKey(keyCode: Int, unicodeChar: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            unicodeChar in PRINTABLE_RANGE

    private companion object {
        const val TAG = "HidScanner"

        /** 공백부터 ~ 까지의 ASCII 출력 문자. 바코드 값은 이 범위를 벗어나지 않는다. */
        val PRINTABLE_RANGE = 0x20..0x7E
    }
}
