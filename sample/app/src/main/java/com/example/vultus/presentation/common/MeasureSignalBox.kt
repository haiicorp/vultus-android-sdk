package com.example.vultus.presentation.common

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow

/**
 * 신호 그래프. 카메라 영상 위에 얹히므로 반투명 검은 바탕에 그린다.
 *
 * 그래프 신호는 측정 중 매 프레임(30Hz) 바뀐다 — 흐름을 화면이 아니라 여기서 받고 그리는 자리에서만 읽어, 바뀔 때 화면이 아니라
 * 이 그래프만 다시 그린다.
 */
@Composable
fun MeasureSignalBox(
    signalTrace: StateFlow<FloatArray>,
    modifier: Modifier = Modifier
) {
    // 그래프 신호(signalTrace)를 그린다. **비어 있으면 비워 둔다** — 측정을 시작한 직후에는 SDK 가 아직 내지
    // 않는다.
    val trace = signalTrace.collectAsState()
    val paint = remember {
        Paint().apply {
            style = Paint.Style.STROKE
            color = Color.Green.toArgb()
            strokeWidth = 10f
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        val signal = trace.value
        if (signal.size > 2) {
            // 창 안의 최소~최대로 맞추고 **큰 값을 위로** 그린다 — 캔버스 y 는 아래로 커진다. 신호 크기가 일정하지
            // 않아 창마다 맞춘다.
            val min = signal.min()
            val span = (signal.max() - min).coerceAtLeast(1e-4f)
            fun y(value: Float) = this.size.height * (1f - (value - min) / span)
            val stride = this.size.width / (signal.size - 1)
            var p1X = 0.0f
            var p1Y = y(signal[0])

            for (i in 1 until signal.size) {
                val p2X = i * stride
                val p2Y = y(signal[i])

                drawContext.canvas.nativeCanvas.drawLine(p1X, p1Y, p2X, p2Y, paint)

                p1X = p2X
                p1Y = p2Y
            }
        }
    }
}
