package com.xiaramteo.kiosk.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.ui.KioskUiState
import com.xiaramteo.kiosk.ui.messageRes

@Composable
fun MemberScanScreen(
    state: KioskUiState.MemberScan,
    onBarcode: (String) -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var manualEntry by remember { mutableStateOf(false) }
    var memberNumber by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        KioskTopBar(
            title = stringResource(R.string.member_scan_title),
            onBack = onHome,
            onHome = onHome,
        )

        ScanPane(
            hint = stringResource(R.string.member_scan_hint),
            onBarcode = onBarcode,
            busy = state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(visible = state.error != null) {
                Column {
                    ErrorBanner(stringResource(state.error?.messageRes() ?: R.string.error_generic))
                    Spacer(Modifier.height(20.dp))
                }
            }
            if (manualEntry) {
                OutlinedTextField(
                    value = memberNumber,
                    onValueChange = { memberNumber = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.member_number_label)) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                BigButton(
                    text = stringResource(R.string.button_next),
                    onClick = { onBarcode(memberNumber); memberNumber = "" },
                    enabled = memberNumber.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { manualEntry = true }) {
                    Text(
                        stringResource(R.string.member_manual_entry),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
