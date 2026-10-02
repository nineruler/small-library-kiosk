package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.data.ItemResult
import com.xiaramteo.kiosk.ui.KioskMode
import com.xiaramteo.kiosk.ui.KioskUiState
import com.xiaramteo.kiosk.ui.messageRes
import java.time.format.DateTimeFormatter

private val DueDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)")

@Composable
fun SubmittingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(72.dp), strokeWidth = 6.dp)
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.processing), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun FinishedScreen(
    state: KioskUiState.Finished,
    secondsUntilHome: Int?,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleRes = when (state.mode) {
        KioskMode.BORROW -> R.string.result_borrow_title
        KioskMode.RETURN -> R.string.result_return_title
    }

    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (state.failed.isEmpty()) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = if (state.failed.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = state.memberName?.let { stringResource(R.string.member_welcome, it) }
                ?: stringResource(titleRes),
            style = MaterialTheme.typography.headlineLarge,
        )
        if (state.memberName != null) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                stringResource(R.string.result_success_count, state.succeeded.size),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.failed.isNotEmpty()) {
                Text(
                    stringResource(R.string.result_failure_count, state.failed.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.results, key = { it.item.barcode }) { result -> ResultRow(result) }
            if (state.failed.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.result_failure_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        secondsUntilHome?.let {
            Text(
                stringResource(R.string.auto_home_in, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
        BigButton(
            text = stringResource(R.string.button_home),
            onClick = onHome,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultRow(result: ItemResult) {
    val isFailure = result is ItemResult.Failed
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isFailure) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(result.item.title, style = MaterialTheme.typography.titleMedium)
            val detail = when (result) {
                is ItemResult.Borrowed ->
                    stringResource(R.string.result_due_date, result.dueDate.format(DueDateFormat))
                is ItemResult.Returned ->
                    if (result.overdueDays > 0) {
                        stringResource(R.string.result_overdue, result.overdueDays)
                    } else {
                        stringResource(R.string.result_returned_ok)
                    }
                is ItemResult.Failed -> stringResource(result.error.messageRes())
            }
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFailure) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
