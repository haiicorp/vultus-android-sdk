package com.example.vultus.presentation.common

import androidx.compose.ui.graphics.Color
import io.haii.vultus.VultusCaptureCondition

/**
 * 얼굴 크기 색 — 지표와 오버레이가 같은 것을 쓴다. 크기 판단은 앱이 하지 않고 **SDK 의 촬영 조건**을 따른다 —
 * 문턱을 앱에 두면 SDK 의 안내와 어긋난다.
 */
object FaceSizeTarget {
    /** 쓸 만한 크기. */
    val ON_TARGET = Color.Green

    /** 작다 — 더 가까이(자동 맞춤이 맞추는 중이어도 같다). */
    val TOO_SMALL = Color.Yellow

    /** 너무 가깝거나 화면 밖에 걸렸다 — "가까이" 와 다른 색으로 알린다. 색만 보고 더 다가오지 않게. */
    val TOO_CLOSE = Color(0xFFFF9800)

    /** 크기와 무관한 원인(얼굴 없음 · 자세 · 어두움) — 크기로는 판단하지 않는다. */
    val OTHER = Color.White

    fun color(cause: VultusCaptureCondition.Cause): Color = when (cause) {
        VultusCaptureCondition.Cause.NONE -> ON_TARGET
        VultusCaptureCondition.Cause.TOO_CLOSE, VultusCaptureCondition.Cause.OFF_FRAME -> TOO_CLOSE
        VultusCaptureCondition.Cause.TOO_FAR, VultusCaptureCondition.Cause.FRAMING -> TOO_SMALL
        else -> OTHER
    }
}
