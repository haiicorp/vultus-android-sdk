package com.example.vultus.presentation.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vultus.MainViewModel
import io.haii.vultus.VultusPreviewTransform

/** 검출 결과가 아직 없을 때 가이드를 그리는 데 쓰는 프레임 크기(세로 모드 기준). */
private const val DEFAULT_FRAME_WIDTH = 480
private const val DEFAULT_FRAME_HEIGHT = 640

@Composable
fun MeasureFaceOverlay(
    modifier: Modifier = Modifier
) {
    val mvm = viewModel<MainViewModel>()
    val faceBox by remember { mvm.faceBox }
    val faceInfo by remember { mvm.faceInfo }
    val imageWidth by remember { mvm.imageWidth }
    val imageHeight by remember { mvm.imageHeight }
    val isMirrorMode by remember { mvm.isMirrorMode }
    val sdkTransform by remember { mvm.previewTransform }
    val captureCause by remember { mvm.captureCause }

    Canvas(modifier = modifier) {
        // 얼굴 가이드는 SDK 프리뷰 뷰(`faceGuide`)가 그린다. 여기서는 얼굴 박스만 그린다.
        // **좌표는 분석 프레임 기준이고, 뒤집을지는 SDK 뷰가 알려준 값을 쓴다.**
        // 프리뷰 뷰가 준 변환이 있으면 그것이 정답이다 — 같은 뷰 크기에서 나온 값이다.
        val transform = sdkTransform ?: VultusPreviewTransform.of(
            imageWidth = if (imageWidth > 0) imageWidth else DEFAULT_FRAME_WIDTH,
            imageHeight = if (imageHeight > 0) imageHeight else DEFAULT_FRAME_HEIGHT,
            viewWidth = size.width.toInt(),
            viewHeight = size.height.toInt(),
            mirrored = isMirrorMode,
        )

        // 얼굴이 없으면(빈 상자) 그리지 않는다 — 크기 0 인 상자도 외곽선은 점으로 찍힌다.
        if (!faceBox.isEmpty) transform.rect(faceBox).also {
            // 지표와 같은 색을 쓴다 — 화면 어디를 보든 거리를 알 수 있게.
            // 초록=쓸 만함, 노랑=더 가까이, 주황=너무 가까움.
            drawRect(
                color = FaceSizeTarget.color(captureCause),
                topLeft = Offset(it.left, it.top), size = Size(it.width(), it.height()), style = Stroke(width = 2.dp.toPx())
            )
        }

        faceInfo?.also { fi ->
            // draw landmark points
            val landmarkPoints = fi.allLandmarks
                .map { transform.point(it.position.x, it.position.y) }
                .map { Offset(it.x, it.y) }

            drawPoints(
                points = landmarkPoints, pointMode = PointMode.Points, color = Color.Yellow, strokeWidth = 3.dp.toPx()
            )

            // draw contour lines
            for (contour in fi.allContours) {
                val contourPoints = contour.points
                    .map { transform.point(it.x, it.y) }
                    .map { Offset(it.x, it.y) }

                if (contourPoints.size >= 2) { // Need at least two points to draw a line
                    val path = Path().apply {
                        // Move to the first point
                        moveTo(contourPoints[0].x, contourPoints[0].y)
                        // Draw lines to subsequent points
                        for (i in 1 until contourPoints.size) {
                            lineTo(contourPoints[i].x, contourPoints[i].y)
                        }
                    }
                    drawPath(
                        path = path, color = Color.Blue, style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
        }
    }
}