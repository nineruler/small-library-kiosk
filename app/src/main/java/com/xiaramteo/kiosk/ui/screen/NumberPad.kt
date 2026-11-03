package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R

/**
 * 숫자만 받는 3×4 키패드.
 *
 * 시스템 키보드를 쓰지 않는다. 기기의 한글 입력기가 켜져 있으면 숫자·영문 입력이 자모로
 * 바뀌어 버려서(실기기에서 겪었다), 숫자를 받아야 하는 자리는 모두 이 키패드를 쓴다.
 * 비밀번호와 발송 시각이 같은 모양이어야 사서가 헷갈리지 않는다.
 */
@Composable
fun NumberPad(
    onDigit: (Char) -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> PadKey(digit.toString()) { onDigit(digit) } }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PadKey(stringResource(R.string.pin_clear), word = true, onClick = onClear)
            PadKey("0") { onDigit('0') }
            PadKey(
                text = stringResource(R.string.pin_enter),
                word = true,
                enabled = confirmEnabled,
                onClick = onConfirm,
            )
        }
    }
}

/**
 * 모든 키가 같은 칸을 차지해야 3열 격자로 보인다. [word] 는 "지우기"처럼 글자가 긴 키로,
 * 같은 칸에 넣기 위해 글자만 한 단계 작게 쓰고 좌우 여백을 없앤다.
 */
@Composable
private fun PadKey(
    text: String,
    word: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 96.dp, height = 72.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = if (word) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleLarge
            },
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
