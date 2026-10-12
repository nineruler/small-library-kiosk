package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.settings.MailConfigResult
import com.xiaramteo.kiosk.settings.SettingsUiState

/**
 * 사서가 메일 발송 상태를 확인하고 즉시 발송을 시험하는 화면.
 *
 * 설정은 여기서 고치지 않는다. 기기의 JSON 파일을 읽어 그대로 보여주기만 한다.
 * 로비 태블릿에서 긴 주소와 앱 비밀번호를 손으로 치는 것이 번거롭고, 한글 입력기가 켜져 있으면
 * 영문이 자모로 바뀌어 버리기 때문이다.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onReload: () -> Unit,
    onSendNow: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text(stringResource(R.string.settings_close)) }
        }

        Spacer(Modifier.height(20.dp))
        ConfigCard(state.config)

        Spacer(Modifier.height(16.dp))
        Text(
            text = if (state.pendingDays > 0) {
                stringResource(R.string.settings_pending, state.pendingDays)
            } else {
                stringResource(R.string.settings_pending_none)
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onReload,
                modifier = Modifier.weight(1f).height(72.dp),
            ) {
                Text(stringResource(R.string.settings_reload))
            }
            BigButton(
                text = stringResource(R.string.settings_send_now),
                onClick = onSendNow,
                enabled = !state.sending && state.config is MailConfigResult.Loaded,
                modifier = Modifier.weight(1f),
            )
        }
        if (state.sending) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.height(24.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.settings_sending),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ConfigCard(config: MailConfigResult) {
    val ok = config is MailConfigResult.Loaded
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            when (config) {
                is MailConfigResult.Loaded -> {
                    Text(
                        stringResource(R.string.settings_config_ok),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Field(stringResource(R.string.settings_sender), config.settings.username)
                    Field(
                        stringResource(R.string.settings_recipients),
                        config.settings.recipients.joinToString("\n"),
                    )
                    Field(stringResource(R.string.settings_send_at), config.settings.sendAt.toString())
                    if (!config.settings.enabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.settings_config_disabled),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                is MailConfigResult.Missing -> {
                    Text(
                        stringResource(R.string.settings_config_missing),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.settings_howto),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Field(stringResource(R.string.settings_config_path), config.expectedPath)
                }

                is MailConfigResult.Invalid -> {
                    Text(
                        stringResource(R.string.settings_config_invalid),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(config.reason, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.Unspecified)
}
