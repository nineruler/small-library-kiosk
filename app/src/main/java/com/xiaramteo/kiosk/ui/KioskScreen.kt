package com.xiaramteo.kiosk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xiaramteo.kiosk.ui.screen.FinishedScreen
import com.xiaramteo.kiosk.ui.screen.HomeScreen
import com.xiaramteo.kiosk.ui.screen.ItemScanScreen
import com.xiaramteo.kiosk.ui.screen.MemberScanScreen
import com.xiaramteo.kiosk.ui.screen.SubmittingScreen

/** 상태 하나로 화면을 고르는 단일 진입점. 별도의 내비게이션 그래프를 두지 않는다. */
@Composable
fun KioskScreen(viewModel: KioskViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val secondsUntilHome by viewModel.secondsUntilHome.collectAsStateWithLifecycle()

    Surface(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "kiosk-screen",
        ) { current ->
            when (current) {
                KioskUiState.Home -> HomeScreen(
                    onBorrow = viewModel::startBorrow,
                    onReturn = viewModel::startReturn,
                )

                is KioskUiState.MemberScan -> MemberScanScreen(
                    state = current,
                    onBarcode = viewModel::onBarcode,
                    onHome = viewModel::goHome,
                )

                is KioskUiState.ItemScan -> ItemScanScreen(
                    state = current,
                    onRemove = viewModel::removeScanned,
                    onSubmit = viewModel::submit,
                    onBack = viewModel::goBack,
                    onHome = viewModel::goHome,
                )

                is KioskUiState.Submitting -> SubmittingScreen()

                is KioskUiState.Finished -> FinishedScreen(
                    state = current,
                    secondsUntilHome = secondsUntilHome,
                    onHome = viewModel::goHome,
                )
            }
        }
    }
}
