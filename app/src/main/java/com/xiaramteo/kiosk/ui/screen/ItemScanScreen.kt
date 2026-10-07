package com.xiaramteo.kiosk.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.data.BookItem
import com.xiaramteo.kiosk.ui.KioskMode
import com.xiaramteo.kiosk.ui.KioskUiState
import com.xiaramteo.kiosk.ui.messageRes

@Composable
fun ItemScanScreen(
    state: KioskUiState.ItemScan,
    onRemove: (BookItem) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (state.mode) {
        KioskMode.BORROW -> R.string.borrow_scan_title
        KioskMode.RETURN -> R.string.return_scan_title
    }

    Column(modifier = modifier.fillMaxSize()) {
        KioskTopBar(title = stringResource(titleRes), onBack = onBack, onHome = onHome)

        state.member?.let { member ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    // 장서·회원 목록이 없으면 이름을 모르므로 회원번호로 대신 부른다.
                    Text(
                        text = member.name
                            ?.let { stringResource(R.string.member_welcome, it) }
                            ?: stringResource(R.string.member_welcome_number, member.barcode),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    val remaining = member.remainingLoans
                    Text(
                        text = if (member.loanCount != null && remaining != null) {
                            stringResource(R.string.member_loan_status, member.loanCount, remaining)
                        } else {
                            stringResource(R.string.member_verified)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 책 등록 라벨은 카메라로 해상되지 않아(막대가 촘촘함) 이 단계는 리더기 전용이다.
        ScannerPanel(
            hint = stringResource(R.string.scan_hint_multiple),
            subHint = stringResource(R.string.scan_hint_multiple_sub),
            busy = state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f)
                .padding(horizontal = 24.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            AnimatedVisibility(visible = state.error != null) {
                Column {
                    ErrorBanner(stringResource(state.error?.messageRes() ?: R.string.error_generic))
                    Spacer(Modifier.height(12.dp))
                }
            }
            Text(
                stringResource(R.string.scanned_list_title, state.scanned.size),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))
            ScannedList(
                items = state.scanned,
                onRemove = onRemove,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }

        BigButton(
            text = stringResource(R.string.button_done),
            onClick = onSubmit,
            enabled = state.scanned.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun ScannedList(
    items: List<BookItem>,
    onRemove: (BookItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.scanned_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val listState = rememberLazyListState()
    // 새로 찍은 책이 목록 아래에 묻히지 않도록 끝으로 따라 내려간다.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.barcode }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.displayTitle, style = MaterialTheme.typography.titleMedium)
                        item.displaySubtitle?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { onRemove(item) }) {
                        Text(stringResource(R.string.button_remove))
                    }
                }
            }
        }
    }
}
