package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import com.xiaramteo.kiosk.data.local.DaySummary
import com.xiaramteo.kiosk.data.local.RecordMode
import com.xiaramteo.kiosk.data.local.ScanRecord
import com.xiaramteo.kiosk.settings.RecordView
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
    onDeleteRecord: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // 화면 전체를 굴리지 않는다. 머리말은 고정해 두고 기록 칸만 남는 높이를 다 쓰게 해야
        // 한 번에 보이는 기록이 늘어난다. 기록 칸 안쪽은 LazyColumn 이 알아서 굴린다.
        // 위아래 여백은 숫자가 아니라 눈에 보이는 공백을 맞춘다. 제목 글자 위에 행간이
        // 얹히는 만큼 위가 더 커 보여서, 아래를 그만큼 더 준다(기기에서 재어 맞췄다).
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
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
            onDeleteRecord = onDeleteRecord,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onReload,
                // 옆의 BigButton 과 같은 높이로 맞춘다. 나란히 선 버튼의 키가 다르면 눈에 띈다.
                modifier = Modifier.weight(1f).height(80.dp),
            ) {
                Text(
                    stringResource(R.string.settings_reload),
                    style = MaterialTheme.typography.titleLarge,
                )
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
    }
}

/** 날짜 목록과 그날의 기록을 나란히 보여준다. 사서가 무엇이 남았는지 직접 확인하는 자리다. */
@Composable
private fun RecordBrowser(
    days: List<DaySummary>,
    selectedDate: LocalDate?,
    records: List<RecordView>,
    onSelectDate: (LocalDate) -> Unit,
    onDeleteRecord: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 어느 기록을 지울지 묻는 중인지. 화면 안에서만 쓰는 상태라 ViewModel 로 올리지 않는다.
    var pendingDelete by remember(selectedDate) { mutableStateOf<Int?>(null) }

    pendingDelete?.let { index ->
        records.getOrNull(index)?.let { record ->
            DeleteConfirmDialog(
                view = record,
                onConfirm = { pendingDelete = null; onDeleteRecord(index) },
                onDismiss = { pendingDelete = null },
            )
        }
    }

    if (days.isEmpty()) {
        Text(
            stringResource(R.string.records_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
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
                    itemsIndexed(records) { index, record ->
                        RecordRow(record) { pendingDelete = index }
                    }
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

/**
 * 기록 한 줄. 책 제목과 회원명만 보여준다.
 *
 * 바코드와 회원번호는 사서가 읽어도 알아볼 수 없는 숫자열이라 줄만 길어지고,
 * 로비 태블릿이라 뒤에 선 사람에게도 보인다. 목록에 없어서 제목을 못 찾은 때만
 * 어쩔 수 없이 바코드를 그대로 올린다 — 그래야 어느 기록인지 가릴 수 있다.
 */
@Composable
private fun RecordRow(view: RecordView, onDelete: () -> Unit) {
    val record = view.record
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Column(Modifier.weight(1f)) {
            Text(
                text = view.title ?: record.itemBarcode,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            view.memberName?.let { name ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onDelete) {
            Text(
                stringResource(R.string.records_delete),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * 기록은 코라시스에 옮겨 적기 전까지 유일한 원본이라 되돌릴 수 없다.
 * 그래서 무엇을 지우는지 그대로 보여주고 한 번 더 묻는다.
 */
@Composable
private fun DeleteConfirmDialog(
    view: RecordView,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val record = view.record
    val mode = stringResource(
        if (record.mode == RecordMode.BORROW) R.string.records_borrow else R.string.records_return
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.records_delete_title)) },
        text = {
            Column {
                Text(
                    "${record.recordedAt.format(TIME_ONLY)}  $mode  ${view.title ?: record.itemBarcode}",
                    style = MaterialTheme.typography.titleMedium,
                )
                view.memberName?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.records_delete_warning),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.records_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.records_delete_cancel))
            }
        },
    )
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
        Column(Modifier.padding(16.dp)) {
            when (config) {
                is MailConfigResult.Loaded -> {
                    // 설정이 제대로 읽혔는지는 내용이 보이는 것으로 충분하다. 제목 한 줄과
                    // 세로로 쌓인 세 항목이 화면 높이를 꽤 먹어서, 가로로 나란히 둔다.
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Field(
                            stringResource(R.string.settings_sender),
                            config.settings.username,
                            Modifier.weight(1.2f),
                        )
                        Field(
                            stringResource(R.string.settings_recipients),
                            config.settings.recipients.joinToString("\n"),
                            Modifier.weight(1.2f),
                        )
                        Field(
                            stringResource(R.string.settings_send_at),
                            config.settings.sendAt.toString(),
                            Modifier.weight(0.6f),
                        )
                    }
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
                    Spacer(Modifier.height(10.dp))
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
private fun Field(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.Unspecified)
    }
}
