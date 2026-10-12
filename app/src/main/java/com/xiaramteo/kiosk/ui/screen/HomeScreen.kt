package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R

/**
 * 세로로 선 10인치 태블릿 기준. 위쪽에 인사말, 아래 절반씩을 대출/반납 버튼이 차지한다.
 * 서서 쓰는 화면이라 손이 닿기 쉬운 아래쪽에 큰 선택지를 둔다.
 */
@Composable
fun HomeScreen(
    onBorrow: () -> Unit,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // 사서 설정으로 가는 숨은 입구. 이용자가 실수로 누르지 않도록 길게 누르기로 둔다.
        Text(
            text = stringResource(R.string.library_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onOpenSettings,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.home_greeting),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))

        ActionCard(
            icon = Icons.AutoMirrored.Filled.MenuBook,
            title = stringResource(R.string.action_borrow),
            description = stringResource(R.string.action_borrow_desc),
            container = MaterialTheme.colorScheme.primary,
            onContainer = MaterialTheme.colorScheme.onPrimary,
            onClick = onBorrow,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.height(28.dp))
        ActionCard(
            icon = Icons.Default.AssignmentReturn,
            title = stringResource(R.string.action_return),
            description = stringResource(R.string.action_return_desc),
            container = MaterialTheme.colorScheme.secondary,
            onContainer = MaterialTheme.colorScheme.onSecondary,
            onClick = onReturn,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    description: String,
    container: Color,
    onContainer: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
    }
}
