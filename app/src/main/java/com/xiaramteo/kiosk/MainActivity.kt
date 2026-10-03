package com.xiaramteo.kiosk

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xiaramteo.kiosk.scan.HidScannerReader
import com.xiaramteo.kiosk.ui.KioskScreen
import com.xiaramteo.kiosk.ui.KioskViewModel
import com.xiaramteo.kiosk.ui.theme.KioskTheme

class MainActivity : ComponentActivity() {

    private val viewModel: KioskViewModel by viewModels {
        KioskViewModel.Factory((application as KioskApplication).librarySystem)
    }

    /**
     * HID 바코드 리더기는 외장 키보드로 잡히므로 화면이 아니라 Activity 에서 받는다.
     * 어느 화면에 있든 스캔이 동작해야 하기 때문이다.
     */
    private val scannerReader by lazy {
        HidScannerReader { barcode -> runOnUiThread { viewModel.onBarcode(barcode) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 이용자가 보는 동안 화면이 꺼지면 안 되고, 상태바/내비바는 숨겨 둔다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            KioskTheme {
                KioskScreen(
                    viewModel = viewModel,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }

    /**
     * 리더기 입력은 포커스된 뷰보다 먼저 가로채야 어느 화면에서든 동작한다.
     *
     * androidx ComponentActivity 가 자체 override 에 @RestrictTo 를 달아 두어 린트가 막지만,
     * Activity 단에서 키를 가로채는 표준적인 방법이고 super 호출로 기존 동작도 그대로 유지한다.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        scannerReader.onKeyEvent(event) || super.dispatchKeyEvent(event)
}
