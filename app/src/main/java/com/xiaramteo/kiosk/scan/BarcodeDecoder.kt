package com.xiaramteo.kiosk.scan

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 프레임에서 바코드를 읽는 공통 디코더. 프레임 공급원(CameraX / USB 웹캠)과 무관하게 재사용한다.
 *
 * 같은 바코드가 연속 프레임마다 다시 올라오지 않도록 [debounceMillis] 동안 같은 값은 무시하고,
 * 인식이 끝나기 전에 들어온 프레임은 버린다(웹캠은 초당 30프레임을 쏟아내므로 밀리면 지연이 쌓인다).
 *
 * 책 등록번호와 회원증은 1D 바코드(EAN-13/CODE-128/CODABAR)가 대부분이지만,
 * QR 회원증을 쓰는 곳도 있어 QR까지 함께 본다.
 */
class BarcodeDecoder(
    private val debounceMillis: Long = 1_500,
    private val onBarcode: (String) -> Unit,
) {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE,
            )
            .build()
    )

    private val inFlight = AtomicBoolean(false)
    private var lastValue: String? = null
    private var lastValueAt = 0L

    /**
     * [image]에서 바코드를 찾는다. 앞선 프레임이 아직 처리 중이면 이 프레임은 버린다.
     * [onDone]은 처리 여부와 관계없이 호출되므로, 프레임 버퍼를 돌려주는 자리에 쓴다.
     */
    fun decode(image: InputImage, onDone: () -> Unit = {}) {
        if (!inFlight.compareAndSet(false, true)) {
            onDone()
            return
        }
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    android.util.Log.i(
                        "BarcodeDecoder",
                        "인식 ${barcodes.size}건: " +
                            barcodes.joinToString { "${it.format}/${it.rawValue}" },
                    )
                }
                barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.ifBlank { null } }?.let(::emit)
            }
            .addOnFailureListener { android.util.Log.w("BarcodeDecoder", "디코딩 실패", it) }
            .addOnCompleteListener {
                inFlight.set(false)
                onDone()
            }
    }

    private fun emit(value: String) {
        val now = System.currentTimeMillis()
        if (value == lastValue && now - lastValueAt < debounceMillis) return
        lastValue = value
        lastValueAt = now
        onBarcode(value)
    }

    fun close() = scanner.close()
}
