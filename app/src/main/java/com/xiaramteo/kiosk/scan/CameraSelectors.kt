package com.xiaramteo.kiosk.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * 키오스크는 태블릿에 USB 웹캠을 물려 쓰는 구성이라 외장 카메라를 1순위로 고른다.
 * 웹캠이 아직 꽂히지 않았거나 개발 기기에서 돌릴 때를 대비해 후면 -> 전면 순으로 물러선다.
 */
@OptIn(ExperimentalLensFacing::class)
fun ProcessCameraProvider.preferredKioskCamera(): CameraSelector? {
    val candidates = listOf(
        CameraSelector.LENS_FACING_EXTERNAL,
        CameraSelector.LENS_FACING_BACK,
        CameraSelector.LENS_FACING_FRONT,
    )
    return candidates.firstNotNullOfOrNull { facing ->
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        // 웹캠이 뽑혀 있으면 hasCamera 가 예외를 던질 수 있어 실패는 '없음'으로 본다.
        if (runCatching { hasCamera(selector) }.getOrDefault(false)) selector else null
    }
}
