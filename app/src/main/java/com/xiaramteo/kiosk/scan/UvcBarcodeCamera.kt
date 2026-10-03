package com.xiaramteo.kiosk.scan

import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.common.InputImage
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "UvcScan"

/**
 * USB 웹캠 미리보기 + 바코드 인식.
 *
 * 이 기기는 camera2가 외장 카메라를 노출하지 않으므로 CameraX를 쓸 수 없다.
 * UVCAndroid(libusb/libuvc)가 usbfs를 통해 유저스페이스에서 웹캠을 직접 열고,
 * 프레임을 NV21로 받아 [BarcodeDecoder]에 그대로 넘긴다.
 *
 * 웹캠을 처음 쓸 때 시스템이 USB 접근 권한을 묻는다. "이 기기에 항상 사용"을 체크하면
 * 다음부터는 묻지 않는다.
 */
@Composable
fun UvcBarcodeCamera(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnError by rememberUpdatedState(onError)

    val decoder = remember { BarcodeDecoder { value -> currentOnBarcode(value) } }
    // 콜백이 Compose 바깥(USB 스레드)에서 들어오므로 가변 상태는 원자 참조로 들고 있는다.
    val surfaceRef = remember { AtomicReference<Surface?>(null) }
    val helperRef = remember { AtomicReference<ICameraHelper?>(null) }
    val frameSize = remember { AtomicReference<Size?>(null) }

    val surfaceView = remember {
        SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surfaceRef.set(holder.surface)
                    helperRef.get()?.takeIf { it.isCameraOpened }?.addSurface(holder.surface, false)
                }

                override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hgt: Int) = Unit

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    surfaceRef.getAndSet(null)?.let { helperRef.get()?.removeSurface(it) }
                }
            })
        }
    }

    DisposableEffect(Unit) {
        val helper = CameraHelper()
        helperRef.set(helper)

        helper.setStateCallback(object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice) {
                Log.i(TAG, "웹캠 연결됨: ${device.productName}")
                helper.selectDevice(device)
            }

            override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                helper.openCamera()
            }

            override fun onCameraOpen(device: UsbDevice) {
                logSupportedFormats(helper)
                logCameraControls(helper)
                selectScanningSize(helper)?.let(helper::setPreviewSize)
                helper.startPreview()
                surfaceRef.get()?.let { helper.addSurface(it, false) }

                // getPreviewSize() 는 요청값을 돌려줄 뿐 실제 협상 결과와 다르다
                // (1280x720 을 요청해도 이 웹캠은 1280x800 으로 스트리밍한다).
                // ML Kit 에 잘못된 해상도를 넘기면 프레임을 엉뚱하게 해석하므로,
                // NV21 버퍼 길이(w*h*3/2)로 지원 목록에서 실제 해상도를 역산한다.
                val sizeByBufferLength = runCatching { helper.supportedSizeList }
                    .getOrNull().orEmpty()
                    .associateBy { it.width * it.height * 3 / 2 }

                helper.setFrameCallback({ buffer ->
                    val length = buffer.remaining()
                    val size = frameSize.get()?.takeIf { it.width * it.height * 3 / 2 == length }
                        ?: sizeByBufferLength[length]?.also {
                            frameSize.set(it)
                            Log.i(TAG, "실제 프레임 해상도: ${it.width}x${it.height}")
                        }
                        ?: return@setFrameCallback
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    // 화면의 안내 네모에 대응하는 가운데만 넘긴다. 자세한 이유는 cropNv21 주석 참고.
                    val frame = cropNv21(bytes, size.width, size.height, 0.8f, 0.5f)
                    decoder.decode(
                        InputImage.fromByteArray(
                            frame.bytes,
                            frame.width,
                            frame.height,
                            0,
                            InputImage.IMAGE_FORMAT_NV21,
                        )
                    )
                }, UVCCamera.PIXEL_FORMAT_NV21)
            }

            override fun onCameraClose(device: UsbDevice) {
                surfaceRef.get()?.let { helper.removeSurface(it) }
            }

            override fun onDeviceClose(device: UsbDevice) = Unit

            override fun onDetach(device: UsbDevice) {
                Log.w(TAG, "웹캠 연결 해제됨")
            }

            override fun onCancel(device: UsbDevice) {
                currentOnError(IllegalStateException("USB 권한이 거부되었습니다"))
            }

            override fun onError(device: UsbDevice, e: CameraException) {
                Log.e(TAG, "웹캠 오류", e)
                currentOnError(e)
            }
        })

        // 이미 꽂혀 있는 웹캠은 onAttach 가 오지 않으므로 직접 고른다.
        helper.deviceList.firstOrNull()?.let(helper::selectDevice)

        onDispose {
            helper.release()
            decoder.close()
            helperRef.set(null)
        }
    }

    AndroidView(factory = { surfaceView }, modifier = modifier)
}

/**
 * 바코드 인식에 쓸 해상도를 고른다.
 *
 * 현장 웹캠(ABKO APC900)은 고정 초점에 포커스 제어도 없어서, 책을 가까이 대면 오히려 흐려진다.
 * 거리를 좁힐 수 없으니 그 거리에서 바코드에 충분한 픽셀이 실리도록 해상도를 최대로 올린다.
 * 바코드가 프레임의 일부만 차지하는 상황이라 해상도가 곧 인식률이다.
 *
 * Size.type 은 UVC 디스크립터 값(UVC_VS_FRAME_MJPEG=7, UVC_VS_FRAME_UNCOMPRESSED=5)이다.
 * UVCCamera.FRAME_FORMAT_MJPEG(=1) 과는 다른 상수이니 섞지 말 것.
 *
 * MJPEG(type 7)를 먼저 보는 이유는 대역폭이다. 이 웹캠의 비압축 1080p 는 5fps 까지 떨어지는 반면
 * MJPEG 1080p 는 30fps 가 나온다. 손에 든 책을 읽으려면 프레임률도 함께 필요하다.
 */
private fun selectScanningSize(helper: ICameraHelper): Size? {
    val sizes = runCatching { helper.supportedSizeList }.getOrNull().orEmpty()
    if (sizes.isEmpty()) return null
    // 10fps 밑으로 떨어지면 책을 대는 순간을 놓친다.
    val usable = sizes.filter { it.fps >= 15 }.ifEmpty { sizes }
    return usable
        .sortedWith(
            compareByDescending<Size> { it.type == UVCCamera.UVC_VS_FRAME_MJPEG }
                .thenByDescending { it.width.toLong() * it.height }
                .thenByDescending { it.fps }
        )
        .firstOrNull()
}

/**
 * 이 웹캠이 포커스/줌을 소프트웨어로 제어할 수 있는지 확인한다.
 * 고정 초점 웹캠은 최소 초점 거리보다 가까우면 흐려지는데, 제어가 가능하면 코드로 맞출 수 있다.
 */
private fun logCameraControls(helper: ICameraHelper) {
    val control = runCatching { helper.uvcControl }.getOrNull() ?: run {
        Log.w(TAG, "UVCControl 없음")
        return
    }
    runCatching {
        Log.i(TAG, "자동초점 지원=${control.isFocusAutoEnable}")
        Log.i(TAG, "수동초점 지원=${control.isFocusAbsoluteEnable} 범위=${control.updateFocusAbsoluteLimit()?.toList()}")
        Log.i(TAG, "줌 지원=${control.isZoomAbsoluteEnable}")
    }.onFailure { Log.w(TAG, "컨트롤 조회 실패", it) }
}

/** 이 웹캠이 어떤 포맷과 해상도를 내보내는지 한 번 남긴다. 기기별 대응을 판단하는 근거가 된다. */
private fun logSupportedFormats(helper: ICameraHelper) {
    runCatching {
        helper.supportedSizeList.forEach { size ->
            Log.i(TAG, "지원 해상도: ${size.width}x${size.height} @${size.fps}fps type=${size.type}")
        }
        // getPreviewSize() 는 실제 스트리밍 해상도가 아니므로 참고용으로만 남긴다.
    }.onFailure { Log.w(TAG, "포맷 목록 조회 실패", it) }
}
