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
import com.xiaramteo.kiosk.report.DailyReportWorker
import com.xiaramteo.kiosk.settings.SettingsMessage
import com.xiaramteo.kiosk.settings.SettingsViewModel
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
        SettingsViewModel.Factory(app.settingsStore, app.scanRecordStore, app.reportSender)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            KioskTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current

                val savedText = stringResource(R.string.settings_saved)
                val sendNoneText = stringResource(R.string.settings_send_none)
                val notReadyText = stringResource(R.string.settings_send_not_ready)

                LaunchedEffect(state.message) {
                    val message = state.message ?: return@LaunchedEffect
                    val text = when (message) {
                        SettingsMessage.Saved -> savedText
                        is SettingsMessage.SendOk ->
                            getString(R.string.settings_send_ok, message.summary)
                        SettingsMessage.SendNone -> sendNoneText
                        SettingsMessage.SendNotReady -> notReadyText
                        is SettingsMessage.SendFailed ->
                            getString(R.string.settings_send_failed, message.reason)
                    }
                    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
                    // 설정이 바뀌면 예약도 새 시각으로 다시 건다.
                    DailyReportWorker.schedule(context, app.settingsStore.current())
                    viewModel.consumeMessage()
                }

                SettingsScreen(
                    state = state,
                    onEnabledChange = viewModel::setEnabled,
                    onHostChange = viewModel::setHost,
                    onPortChange = viewModel::setPort,
                    onUsernameChange = viewModel::setUsername,
                    onPasswordChange = viewModel::setPassword,
                    onSendAtChange = viewModel::setSendAt,
                    onAddRecipient = viewModel::addRecipient,
                    onRemoveRecipient = viewModel::removeRecipient,
                    onClearRecipientError = viewModel::clearRecipientError,
                    onSave = viewModel::save,
                    onSendNow = viewModel::sendNow,
                    onClose = { finish() },
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}
