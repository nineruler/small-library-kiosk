package com.xiaramteo.kiosk.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R

/**
 * 바코드 리더기만 쓰는 화면의 안내 패널.
 *
 * 책에 붙은 등록 라벨은 막대가 촘촘해서, 고정 초점 웹캠이나 태블릿 카메라로는 해상되지 않는다
 * (실기기 측정 결과 막대 경계가 번져 디코딩 불가). 그래서 도서 단계에서는 카메라를 아예 띄우지
 * 않고 리더기만 안내한다. 보여줄 영상이 없는 자리를 비워 두는 대신, 아이콘을 천천히 깜빡여
 * "지금 읽을 준비가 됐다"는 것을 알린다.
 */
@Composable
fun ScannerPanel(
    hint: String,
    modifier: Modifier = Modifier,
    subHint: String? = null,
    busy: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "scanner-ready")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200), RepeatMode.Reverse),
        label = "pulse",
    )

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(84.dp), strokeWidth = 7.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.processing),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp).alpha(pulse),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                subHint?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
