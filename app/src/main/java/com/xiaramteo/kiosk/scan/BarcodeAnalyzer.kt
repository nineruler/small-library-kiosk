package com.xiaramteo.kiosk.scan

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * 웹캠 프레임에서 바코드를 읽어 [onBarcode]로 넘긴다.
 *
 * 같은 바코드가 연속 프레임마다 다시 올라오지 않도록 [debounceMillis] 동안 같은 값은 무시한다.
 * 책 등록번호와 회원증은 1D 바코드(EAN-13/CODE-128/CODABAR)가 대부분이지만,
 * QR 회원증을 쓰는 곳도 있어 QR까지 함께 본다.
 */
class BarcodeAnalyzer(
    private val debounceMillis: Long = 1_500,
    private val onBarcode: (String) -> Unit,
) : ImageAnalysis.Analyzer {

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

    private var lastValue: String? = null
    private var lastValueAt = 0L

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstNotNullOfOrNull { it.rawValue?.trim()?.ifBlank { null } }
                    ?.let(::emit)
            }
            .addOnCompleteListener { imageProxy.close() }
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
