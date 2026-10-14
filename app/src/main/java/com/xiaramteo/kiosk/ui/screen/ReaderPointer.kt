package com.xiaramteo.kiosk.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R

/** 리더기가 화면 기준 어느 쪽에 놓여 있는지. 설치 환경에 따라 달라진다. */
enum class ReaderSide { LEFT, RIGHT, BOTTOM }

/**
 * 바코드 리더기가 어디 있는지 손으로 짚어 주는 안내.
 *
 * 리더기는 화면 밖 실물이라 이용자가 어디에 대야 하는지 모른다. 특히 처음 오는 이용자는
 * 화면만 보고 카드를 어디에 가져가야 할지 헤맨다. 그래서 설치 방향에 맞춰 화살표가
 * 리더기 쪽을 가리키게 하고, 천천히 움직여 시선을 끈다.
 */
@Composable
fun ReaderPointer(
    modifier: Modifier = Modifier,
    side: ReaderSide = ReaderSide.RIGHT,
) {
    val transition = rememberInfiniteTransition(label = "reader-pointer")
    // 화살표가 리더기 쪽으로 밀려갔다 돌아오며 방향을 반복해 알린다.
    val nudge by transition.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "nudge",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.reader_here),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.reader_here_sub),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        ReaderArrow(side)
    }
}

/**
 * 실물 리더기 쪽을 가리키는 화살표. 그쪽으로 밀려갔다 돌아오며 방향을 반복해 알린다.
 * 멈춘 화살표보다 시선을 끌고, 처음 오는 이용자가 어디에 댈지 바로 알아챈다.
 */
@Composable
fun ReaderArrow(side: ReaderSide, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "reader-arrow")
    val nudge by transition.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "nudge",
    )

    Icon(
        imageVector = when (side) {
            ReaderSide.LEFT -> Icons.Default.ArrowBack
            ReaderSide.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
            ReaderSide.BOTTOM -> Icons.Default.ArrowDownward
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = modifier
            .size(80.dp)
            .offset(
                x = when (side) {
                    ReaderSide.LEFT -> (-nudge).dp
                    ReaderSide.RIGHT -> nudge.dp
                    ReaderSide.BOTTOM -> 0.dp
                },
                y = if (side == ReaderSide.BOTTOM) nudge.dp else 0.dp,
            ),
    )
}
