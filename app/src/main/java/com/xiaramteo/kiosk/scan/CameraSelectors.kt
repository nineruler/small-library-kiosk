package com.xiaramteo.kiosk.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider

/**
 * 카메라 미리보기에 쓸 렌즈를 고른다.
 *
 * 키오스크는 태블릿을 세워 두고 이용자가 화면을 마주 보는 구성이라, 후면 카메라는 벽이나
 * 거치대 뒤쪽을 비추게 된다. 그래서 이용자 쪽을 향하는 전면 카메라를 1순위로 쓴다.
 * 외장 USB 웹캠이 꽂혀 있으면 그쪽이 더 자유롭게 각도를 잡을 수 있으므로 먼저 본다.
 */
@OptIn(ExperimentalLensFacing::class)
fun ProcessCameraProvider.preferredKioskCamera(): CameraSelector? {
    val candidates = listOf(
        CameraSelector.LENS_FACING_EXTERNAL,
        CameraSelector.LENS_FACING_FRONT,
        CameraSelector.LENS_FACING_BACK,
    )
    return candidates.firstNotNullOfOrNull { facing ->
        val selector = CameraSelector.Builder().requireLensFacing(facing).build()
        // 웹캠이 뽑혀 있으면 hasCamera 가 예외를 던질 수 있어 실패는 '없음'으로 본다.
        if (runCatching { hasCamera(selector) }.getOrDefault(false)) selector else null
    }
}
