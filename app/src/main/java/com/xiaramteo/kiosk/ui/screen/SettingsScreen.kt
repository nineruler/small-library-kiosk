package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.settings.RecipientError
import com.xiaramteo.kiosk.settings.SettingsUiState
import java.time.LocalTime

/**
 * 사서가 메일 발송을 설정하는 화면.
 *
 * 이용자가 쓰는 화면이 아니라 사서가 가끔 여는 화면이라, 키오스크 본화면과 달리 보통 크기로 둔다.
 * 비밀번호는 저장만 하고 되읽어 보여주지 않는다.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEnabledChange: (Boolean) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSendAtChange: (LocalTime) -> Unit,
    onAddRecipient: (String) -> Unit,
    onRemoveRecipient: (String) -> Unit,
    onClearRecipientError: () -> Unit,
    onSave: () -> Unit,
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

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = state.draft.enabled, onCheckedChange = onEnabledChange)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_enabled), style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = if (state.pendingDays > 0) {
                stringResource(R.string.settings_pending, state.pendingDays)
            } else {
                stringResource(R.string.settings_pending_none)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionTitle(stringResource(R.string.settings_recipients))
        RecipientEditor(
            recipients = state.draft.recipients,
            error = state.recipientError,
            onAdd = onAddRecipient,
            onRemove = onRemoveRecipient,
            onClearError = onClearRecipientError,
        )

        SectionTitle(stringResource(R.string.settings_smtp))
        OutlinedTextField(
            value = state.draft.username,
            onValueChange = onUsernameChange,
            label = { Text(stringResource(R.string.settings_username)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.draft.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.settings_password)) },
            placeholder = if (state.hasStoredPassword && state.draft.password.isEmpty()) {
                { Text(stringResource(R.string.settings_password_saved)) }
            } else {
                null
            },
            supportingText = { Text(stringResource(R.string.settings_password_help)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.draft.host,
                onValueChange = onHostChange,
                label = { Text(stringResource(R.string.settings_host)) },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = state.draft.port.toString(),
                onValueChange = onPortChange,
                label = { Text(stringResource(R.string.settings_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        SectionTitle(stringResource(R.string.settings_send_at))
        SendTimePicker(value = state.draft.sendAt, onChange = onSendAtChange)

        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton(
                text = stringResource(R.string.settings_save),
                onClick = onSave,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = onSendNow,
                enabled = !state.sending,
                modifier = Modifier.weight(1f).height(80.dp),
            ) {
                if (state.sending) {
                    CircularProgressIndicator(Modifier.height(24.dp))
                } else {
                    Text(stringResource(R.string.settings_send_now))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun RecipientEditor(
    recipients: List<String>,
    error: RecipientError?,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var input by remember { mutableStateOf("") }

    fun submit() {
        onAdd(input)
        input = ""
    }

    Column {
        if (recipients.isEmpty()) {
            Text(
                stringResource(R.string.settings_recipient_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
        }
        recipients.forEach { address ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(address, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemove(address) }) {
                        Text(stringResource(R.string.settings_recipient_remove))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; onClearError() },
                label = { Text(stringResource(R.string.settings_recipient_hint)) },
                isError = error != null,
                supportingText = error?.let {
                    {
                        Text(
                            stringResource(
                                when (it) {
                                    RecipientError.INVALID -> R.string.settings_recipient_invalid
                                    RecipientError.DUPLICATE -> R.string.settings_recipient_duplicate
                                }
                            )
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = ::submit,
                enabled = input.isNotBlank(),
                modifier = Modifier.height(56.dp),
            ) {
                Text(stringResource(R.string.settings_recipient_add))
            }
        }
    }
}

@Composable
private fun SendTimePicker(value: LocalTime, onChange: (LocalTime) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = value.hour.toString(),
            onValueChange = { raw ->
                raw.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 0..23 }
                    ?.let { onChange(value.withHour(it)) }
            },
            label = { Text("시") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
        OutlinedTextField(
            value = value.minute.toString(),
            onValueChange = { raw ->
                raw.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 0..59 }
                    ?.let { onChange(value.withMinute(it)) }
            },
            label = { Text("분") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(120.dp),
        )
    }
}
