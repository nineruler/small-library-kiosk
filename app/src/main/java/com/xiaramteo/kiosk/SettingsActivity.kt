package com.xiaramteo.kiosk

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaramteo.kiosk.settings.SettingsMessage
import com.xiaramteo.kiosk.settings.SettingsViewModel
import com.xiaramteo.kiosk.ui.screen.PinGate
import com.xiaramteo.kiosk.ui.screen.SettingsScreen
import com.xiaramteo.kiosk.ui.theme.KioskTheme

/**
 * 사서용 설정 화면.
 *
 * 이용자 흐름과 완전히 분리해 두려고 별도 Activity 로 뒀다. 키오스크 상태 머신은 건드리지 않는다.
 */
class SettingsActivity : ComponentActivity() {

    private val app: KioskApplication get() = application as KioskApplication

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(app.mailConfigLoader, app.scanRecordStore, app.reportSender)
    }

    override fun onResume() {
        super.onResume()
        // 설정 파일을 바꾸고 돌아왔을 수 있으니 다시 읽는다.
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KioskTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val sendNoneText = stringResource(R.string.settings_send_none)
                val notReadyText = stringResource(R.string.settings_send_not_ready)

                LaunchedEffect(state.message) {
                    val message = state.message ?: return@LaunchedEffect
                    val text = when (message) {
                        is SettingsMessage.SendOk ->
                            getString(R.string.settings_send_ok, message.summary)
                        SettingsMessage.SendNone -> sendNoneText
                        SettingsMessage.SendNotReady -> notReadyText
                        is SettingsMessage.SendFailed ->
                            getString(R.string.settings_send_failed, message.reason)
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    viewModel.consumeMessage()
                }

                if (state.unlocked) {
                    SettingsScreen(
                        state = state,
                        onReload = viewModel::refresh,
                        onSendNow = viewModel::sendNow,
                        onSelectDate = viewModel::selectDate,
                        onClose = { finish() },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                } else {
                    PinGate(
                        onSubmit = viewModel::submitPin,
                        onCancel = { finish() },
                        wrong = state.pinWrong,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}
