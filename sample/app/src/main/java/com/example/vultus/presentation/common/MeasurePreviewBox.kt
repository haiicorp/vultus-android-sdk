package com.example.vultus.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.google.accompanist.permissions.ExperimentalPermissionsApi

/**
 * 프리뷰 자리. 프리뷰는 **정사각형** — 화면의 가로 · 세로 중 짧은 쪽의 90 % 를 한 변으로 화면 정중앙에 둔다.
 * 오버레이는 프리뷰와 같은 상자에 그린다(변환이 그 뷰 크기 기준이다).
 *
 * 카메라 권한을 얻은 뒤에 [preview] 를 놓는다 — 요청은 앱이 한다. 비대화형 시작의 카운트다운([countdown], 남은 초)은
 * 프리뷰 가운데에 크게 그린다.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeasurePreviewBox(
    modifier: Modifier = Modifier,
    countdown: Int? = null,
    preview: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * PREVIEW_SIDE_FRACTION
        Box(modifier = Modifier.size(side)) {
            RequestPermissions {
                preview(Modifier.fillMaxSize())
            }
            MeasureFaceOverlay(modifier = Modifier.fillMaxSize())
            if (countdown != null) {
                Text(
                    text = "$countdown",
                    modifier = Modifier.align(Alignment.Center),
                    style = TextStyle(
                        color = Color.White,
                        fontSize = (side.value * 0.3f).sp,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 12f),
                    ),
                )
            }
        }
    }
}

/** 프리뷰 한 변 — 화면의 가로 · 세로 중 짧은 쪽에 대한 비율. */
private const val PREVIEW_SIDE_FRACTION = 0.9f
