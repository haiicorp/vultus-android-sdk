package com.example.vultus.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.haii.vultus.VultusCaptureCondition
import io.haii.vultus.VultusGuidance
import io.haii.vultus.VultusStability

/**
 * 측정 안내. SDK 가 정해 주는 안내 하나([VultusGuidance])를 띄운다 — 측정 전에는 촬영 조건을, 측정 중에는
 * 촬영 조건 · 흔들림 · 신호 가운데 가장 앞선 것을 알린다.
 *
 * **안내 전용이다** — 측정은 그대로 이어진다. 고칠 것이 없으면 아무것도 띄우지 않는다.
 */
@Composable
fun GuidanceNotice(guidance: VultusGuidance, modifier: Modifier = Modifier) {
    val text = when (val g = guidance) {
        VultusGuidance.None -> return
        is VultusGuidance.Condition -> captureMessage(g.condition.cause) ?: return
        is VultusGuidance.Motion -> when (g.stability.cause) {
            VultusStability.Cause.FACE_MOTION -> "잠시 움직이지 말아 주세요."
            else -> "기기가 흔들리고 있습니다. 기기를 고정하거나 두 손으로 잡아 주세요."
        }
        VultusGuidance.PoorSignal -> "신호가 약합니다. 조명과 자세를 확인해 주세요."
    }

    Text(
        text = text,
        color = Color.White,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xD9E65100), RoundedCornerShape(12.dp))
            .padding(8.dp),
    )
}

private fun captureMessage(cause: VultusCaptureCondition.Cause): String? = when (cause) {
    VultusCaptureCondition.Cause.NONE -> null
    VultusCaptureCondition.Cause.NO_FACE -> "얼굴이 보이지 않습니다. 화면 안에 얼굴을 맞춰 주세요."
    VultusCaptureCondition.Cause.OFF_FRAME -> "얼굴이 화면 밖으로 나갔습니다. 가운데로 와 주세요."
    VultusCaptureCondition.Cause.TOO_FAR -> "조금 더 가까이 와 주세요."
    VultusCaptureCondition.Cause.TOO_CLOSE -> "조금 더 멀리 떨어져 주세요."
    VultusCaptureCondition.Cause.HEAD_TILTED -> "기기를 눈높이에 두고 정면을 봐 주세요."
    VultusCaptureCondition.Cause.HEAD_TURNED -> "화면을 정면으로 봐 주세요."
    VultusCaptureCondition.Cause.TOO_DARK -> "주변이 어둡습니다. 밝은 곳에서 측정해 주세요."
    VultusCaptureCondition.Cause.FRAMING -> "카메라를 맞추고 있습니다. 그대로 계셔 주세요."
}
