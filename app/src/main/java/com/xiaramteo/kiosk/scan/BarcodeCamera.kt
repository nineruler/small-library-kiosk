package com.xiaramteo.kiosk.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.remember
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 웹캠 미리보기와 바코드 인식을 함께 띄우는 컴포저블.
 *
 * [onBarcode]는 메인 스레드가 아닌 분석 스레드에서 호출될 수 있으므로,
 * 받는 쪽(ViewModel)에서 스레드 안전하게 처리한다.
 */
@Composable
fun BarcodeCamera(
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    onError: (Throwable) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnError by rememberUpdatedState(onError)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val decoder = remember { BarcodeDecoder { value -> currentOnBarcode(value) } }
    val analyzer = remember { BarcodeAnalyzer(decoder) }
    // 화면을 떠날 때 카메라를 반드시 놓아주려고 바인딩한 provider 를 들고 있는다.
    // bindToLifecycle 은 Activity 생명주기에 묶이므로, 이 컴포저블이 사라져도
    // 직접 unbind 하지 않으면 카메라가 계속 열린 채로 남는다.
    val boundProvider = remember { AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            boundProvider.getAndSet(null)?.unbindAll()
            decoder.close()
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(Unit) {
        val provider = runCatching { awaitCameraProvider(context) }
            .getOrElse { currentOnError(it); return@LaunchedEffect }

        val selector: CameraSelector = provider.preferredKioskCamera()
            ?: run { currentOnError(IllegalStateException("no camera available")); return@LaunchedEffect }

        val preview = Preview.Builder().build().apply {
            surfaceProvider = previewView.surfaceProvider
        }
        // 1280x720 정도면 책 등록번호 바코드를 읽기 충분하고 프레임 처리도 가볍다.
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(1280, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        )
                    )
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply { setAnalyzer(analysisExecutor, analyzer) }

        runCatching {
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            boundProvider.set(provider)
        }.onFailure(currentOnError)
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** 카메라 권한이 이미 있는지 확인한다. */
fun hasCameraPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

/** CameraX의 ListenableFuture를 코루틴에서 기다린다. */
private suspend fun awaitCameraProvider(context: android.content.Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            {
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        continuation.invokeOnCancellation { future.cancel(false) }
    }
