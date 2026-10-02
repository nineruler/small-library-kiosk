package com.xiaramteo.kiosk.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 키오스크는 밝은 실내에서 서서 쓰는 화면이라 다크 모드를 따라가지 않고
 * 고대비 라이트 팔레트 하나로 고정한다. 글자도 기본보다 크게 잡는다.
 */
private val KioskColors = lightColorScheme(
    primary = Color(0xFF1B5E4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBDF),
    onPrimaryContainer = Color(0xFF05291E),
    secondary = Color(0xFFB4541E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0CF),
    onSecondaryContainer = Color(0xFF3B1402),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF1C1B17),
    surface = Color.White,
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFEDE7DC),
    onSurfaceVariant = Color(0xFF4B463D),
    outline = Color(0xFF7D776C),
)

private val KioskTypography = Typography().let { base ->
    base.copy(
        displayLarge = base.displayLarge.copy(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontSize = 48.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 22.sp, lineHeight = 30.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 18.sp, lineHeight = 26.sp),
        labelLarge = base.labelLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun KioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KioskColors,
        typography = KioskTypography,
        content = content,
    )
}
