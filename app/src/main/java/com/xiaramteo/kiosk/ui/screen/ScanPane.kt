package com.xiaramteo.kiosk.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xiaramteo.kiosk.R
import com.xiaramteo.kiosk.scan.BarcodeCamera
import com.xiaramteo.kiosk.scan.hasCameraPermission

/**
 * 웹캠 화면 + 바코드를 맞출 네모 가이드.
 *
 * 권한이 없으면 권한 요청 안내를, 카메라를 열지 못하면 웹캠 연결 확인 문구를 대신 보여준다.
 */
@Composable
fun ScanPane(
    hint: String,
    onBarcode: (String) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var cameraError by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(granted) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when {
            !granted -> PermissionNotice(onRequest = { launcher.launch(Manifest.permission.CAMERA) })
            cameraError -> CenteredNotice(stringResource(R.string.camera_error))
            else -> {
                BarcodeCamera(
                    onBarcode = onBarcode,
                    onError = { cameraError = true },
                    modifier = Modifier.fillMaxSize(),
                )
                // 바코드를 맞출 자리를 눈으로 알려주는 가이드 테두리.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(180.dp)
                        .border(4.dp, Color(0xCCFFFFFF), RoundedCornerShape(16.dp))
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(20.dp)
                        .background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }

        if (busy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0x66000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.processing),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionNotice(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        BigButton(stringResource(R.string.camera_permission_grant), onClick = onRequest)
    }
}

@Composable
private fun CenteredNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(32.dp),
    )
}
