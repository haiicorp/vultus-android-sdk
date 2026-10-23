package com.example.vultus.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vultus.MainViewModel
import io.haii.vultus.VultusSignalClarity
import io.haii.vultus.VultusDisplayBpm
import io.haii.vultus.VultusMeasureState
import io.haii.vultus.VultusFramingState
import io.haii.vultus.VultusPreviewFill
import io.haii.vultus.VultusPreviewTransform
import java.util.Locale

/** 지표 줄 — BPM · FPS · 선명도 · 얼굴 크기 · 줌과 화면 배율 · 표정. */
@Composable
fun MetricsRow(
    state: VultusMeasureState,
    display: VultusDisplayBpm,
    clarity: VultusSignalClarity,
    framing: VultusFramingState,
    mvm: MainViewModel,
) {
    val side = mvm.faceBox.value.let { maxOf(it.width(), it.height()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Metric("BPM", displayBpmText(display), tint = displayBpmTint(display))
        Metric("FPS", display.fps.takeIf { it > 0 }?.toString() ?: "--")
        Metric(
            title = "선명도",
            value = if (state.isMeasuring) clarityText(clarity) else "--",
            tint = if (state.isMeasuring) clarityTint(clarity) else Color.White,
        )
        // 얼굴 bbox 긴 변(px). 색은 SDK 촬영 조건을 따른다 — 쓸 만하면 초록, 작으면 노랑, 너무 가까우면 주황.
        Metric(
            title = "얼굴",
            value = if (side > 0f) side.toInt().toString() else "--",
            tint = if (side <= 0f) Color.White else FaceSizeTarget.color(mvm.captureCause.value),
        )
        // 자동 맞춤이 얼굴 크기를 나눈 몫 — 카메라 줌 · 화면 배율(프리뷰를 늘린 정도). 둘을 곱한 것이 화면의 크기다.
        Metric("줌·화면", framingText(framing, mvm.previewTransform.value, mvm.previewSize.value), valueSize = 12.sp, weight = 1.5f)
        Metric("표정", if (mvm.emotionIndex.value >= 0) mvm.emotionLabel.value else "--")
    }
}

/** "줌 · 화면 배율". 화면 배율은 SDK 가 알려 준 변환의 배율 ÷ 같은 뷰의 기본(덮기) 배율이다. */
private fun framingText(
    framing: VultusFramingState,
    transform: VultusPreviewTransform?,
    viewSize: android.util.Size,
): String {
    val zoom = (framing as? VultusFramingState.Framed)?.zoomRatio ?: return "--"
    val display = transform?.takeIf { viewSize.width > 0 && viewSize.height > 0 }?.let { t ->
        val base = VultusPreviewTransform.of(
            t.imageWidth, t.imageHeight, viewSize.width, viewSize.height,
            VultusPreviewFill.FILL, t.mirrored, t.rotationDegrees,
        )
        t.scale / base.scale
    }
    return "%.2f·%s".format(zoom, display?.let { "%.2f".format(it) } ?: "--")
}

/** 새 표시용 BPM — 값이 없는 구간에는 숫자를 그리지 않는다(SDK 문서의 권고). */
private fun displayBpmText(value: VultusDisplayBpm): String =
    if (value.state == VultusDisplayBpm.State.WARMING_UP) "--" else value.bpm.toString()

/** 아직 흔들리는 구간은 흐리게 — 상태를 색으로도 드러낸다. */
private fun displayBpmTint(value: VultusDisplayBpm): Color = when (value.state) {
    VultusDisplayBpm.State.WARMING_UP -> Color.White
    VultusDisplayBpm.State.ESTIMATING -> Color.Yellow
    VultusDisplayBpm.State.STABLE -> Color.Green
}

/** 선명도 점수. */
private fun clarityText(clarity: VultusSignalClarity): String =
    String.format(Locale.US, "%.2f", clarity.score)

/**
 * 선명도 수준은 색으로만 — 흰색 아직 정해지지 않음 · 노랑 나쁨 · 파랑 보통 · 초록 좋음. 측정 초반(`WARMING_UP`)에는
 * 수준이 정해지지 않는다 — 그동안의 점수는 참고용이다.
 */
private fun clarityTint(clarity: VultusSignalClarity): Color = when (clarity.level) {
    VultusSignalClarity.Level.WARMING_UP -> Color.White
    VultusSignalClarity.Level.POOR -> Color.Yellow
    VultusSignalClarity.Level.FAIR -> Color(0xFF0A84FF)
    VultusSignalClarity.Level.GOOD -> Color.Green
}

@Composable
private fun RowScope.Metric(
    title: String,
    value: String,
    tint: Color = Color.White,
    valueSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    weight: Float = 1f,
) {
    Column(
        modifier = Modifier.weight(weight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = tint,
            fontSize = valueSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
