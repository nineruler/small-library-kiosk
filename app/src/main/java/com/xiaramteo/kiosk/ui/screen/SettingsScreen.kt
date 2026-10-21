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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.xiaramteo.kiosk.data.local.DaySummary
import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.settings.MailConfigResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
    onSelectDate: (LocalDate) -> Unit,
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
        ConfigCard(state.config)

        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.pendingDays > 0) {
                stringResource(R.string.settings_pending, state.pendingDays)
            } else {
                stringResource(R.string.settings_pending_none)
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.records_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        RecordBrowser(
            days = state.days,
            selectedDate = state.selectedDate,
            records = state.selectedRecords,
            onSelectDate = onSelectDate,
            modifier = Modifier.fillMaxWidth().height(380.dp),
        )

        Spacer(Modifier.height(24.dp))
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

/** 날짜 목록과 그날의 기록을 나란히 보여준다. 사서가 무엇이 남았는지 직접 확인하는 자리다. */
@Composable
private fun RecordBrowser(
    days: List<DaySummary>,
    selectedDate: LocalDate?,
    records: List<ScanRecord>,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) {
        Text(
            stringResource(R.string.records_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(days, key = { it.date }) { day ->
                DayRow(day, selected = day.date == selectedDate) { onSelectDate(day.date) }
            }
        }
        Card(
            modifier = Modifier.weight(1.4f).fillMaxHeight(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (records.isEmpty()) {
                Text(
                    stringResource(R.string.records_pick_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.padding(12.dp)) {
                    items(records) { record -> RecordRow(record) }
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DaySummary, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(day.date.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.records_day_summary, day.borrowed, day.returned),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = if (day.sent) {
                    stringResource(R.string.records_sent)
                } else {
                    stringResource(R.string.records_unsent)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (day.sent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun RecordRow(record: ScanRecord) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            record.recordedAt.format(TIME_ONLY),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(
                if (record.mode == RecordMode.BORROW) {
                    R.string.records_borrow
                } else {
                    R.string.records_return
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (record.mode == RecordMode.BORROW) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondary
            },
        )
        Text(
            record.itemBarcode,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        record.memberBarcode?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val TIME_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
