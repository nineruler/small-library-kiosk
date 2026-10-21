package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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

/**
 * 사서 화면에 들어가기 전 비밀번호를 묻는다.
 *
 * 시스템 키보드를 쓰지 않고 자체 숫자판을 그리는 이유가 두 가지다.
 * 기기의 한글 입력기가 켜져 있으면 영문·숫자 입력이 자모로 바뀌어 버리고(실기기에서 겪었다),
 * 키오스크에서는 큰 숫자판이 누르기도 쉽다.
 */
@Composable
fun PinGate(
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    wrong: Boolean = false,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (wrong) {
                stringResource(R.string.pin_wrong)
            } else {
                stringResource(R.string.pin_hint)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (wrong) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Spacer(Modifier.height(20.dp))
        // 입력한 자릿수만 점으로 보여준다. 어깨너머로 숫자가 읽히지 않게.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(pin.length.coerceAtLeast(1)) { index ->
                Surface(
                    modifier = Modifier.size(18.dp),
                    shape = CircleShape,
                    color = if (index < pin.length) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {}
            }
        }

        Spacer(Modifier.height(28.dp))
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> PinKey(digit.toString()) { pin += digit } }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PinKey(stringResource(R.string.pin_clear), wide = true) { pin = "" }
            PinKey("0") { pin += "0" }
            PinKey(
                text = stringResource(R.string.pin_enter),
                wide = true,
                enabled = pin.isNotEmpty(),
            ) { onSubmit(pin); pin = "" }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_close)) }
    }
}

@Composable
private fun PinKey(
    text: String,
    wide: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(if (wide) 150.dp else 96.dp).height(72.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}
