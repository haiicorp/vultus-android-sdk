package com.example.vultus.presentation.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 측정 결과 요약 한 줄 — 시작 거절 · 전송 완료 · 취소 · 실패가 모두 여기로 모인다. 없으면 그리지 않는다. */
@Composable
fun ResultSummaryText(summary: String?, modifier: Modifier = Modifier) {
    if (summary == null) return
    Text(
        text = summary,
        color = Color.White.copy(alpha = 0.7f),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(bottom = 8.dp),
    )
}
