package com.xiaramteo.kiosk.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

/** 내장 카메라(CameraX) 프레임을 [BarcodeDecoder]에 넘기는 어댑터. */
class BarcodeAnalyzer(private val decoder: BarcodeDecoder) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        decoder.decode(input) { imageProxy.close() }
    }
}
