package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import java.time.LocalTime

/**
 * 발송 시각을 네 자리 숫자로 받는다.
 *
 * 설정 파일에서 바꿀 수 있는 값이지만, 사서가 기기 앞에서 가장 자주 손대는 항목이라
 * 화면에서도 고칠 수 있게 뒀다. 주소나 앱 비밀번호와 달리 숫자 네 자리뿐이라
 * [NumberPad] 로 받으면 한글 입력기 문제도 없다.
 */
@Composable
fun SendAtDialog(
    current: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val parsed = parseSendAt(typed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.send_at_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.send_at_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    // 다 치기 전에는 지금 설정값을 흐리게 보여준다.
                    text = display(typed, current),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (typed.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else if (typed.length == 4 && parsed == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(16.dp))
                NumberPad(
                    onDigit = { if (typed.length < 4) typed += it },
                    onClear = { typed = "" },
                    onConfirm = { parsed?.let(onConfirm) },
                    confirmEnabled = parsed != null,
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.send_at_cancel)) }
        },
    )
}

/** 치는 중에도 HH:mm 꼴로 보여 준다. 아직 안 쳤으면 지금 값을 그대로 보여 준다. */
private fun display(typed: String, current: LocalTime): String {
    if (typed.isEmpty()) return "%02d:%02d".format(current.hour, current.minute)
    val padded = typed.padEnd(4, '-')
    return "${padded.take(2)}:${padded.drop(2)}"
}

/** 네 자리를 다 쳤고 실제로 있는 시각일 때만 돌려준다. 25시나 70분은 거른다. */
internal fun parseSendAt(typed: String): LocalTime? {
    if (typed.length != 4 || !typed.all(Char::isDigit)) return null
    val hour = typed.take(2).toInt()
    val minute = typed.drop(2).toInt()
    if (hour > 23 || minute > 59) return null
    return LocalTime.of(hour, minute)
}
