package com.xiaramteo.kiosk.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 가로면 좌우로, 세로면 위아래로 두 영역을 배치한다.
 *
 * 키오스크 거치 방향이 운영 중에 바뀔 수 있어(실제로 세로 → 가로로 한 번 바뀌었다)
 * 한쪽 방향에서만 쓸 수 있는 레이아웃을 두지 않는다.
 *
 * [firstWeight]·[secondWeight] 는 두 영역이 차지할 비율이다.
 */
@Composable
fun AdaptivePane(
    modifier: Modifier = Modifier,
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    gap: Dp = 16.dp,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        if (isLandscape) {
            Row(
                modifier = Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                first(Modifier.weight(firstWeight).fillMaxHeight())
                second(Modifier.weight(secondWeight).fillMaxHeight())
            }
        } else {
            Column(
                modifier = Modifier.matchParentSize(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                first(Modifier.weight(firstWeight).fillMaxWidth())
                second(Modifier.weight(secondWeight).fillMaxWidth())
            }
        }
    }
}

/** 지금 화면이 가로인지. 기기 방향이 아니라 실제로 주어진 공간의 모양으로 판단한다. */
val BoxWithConstraintsScope.isLandscape: Boolean
    get() = maxWidth > maxHeight
