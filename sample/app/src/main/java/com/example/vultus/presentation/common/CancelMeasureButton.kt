package com.example.vultus.presentation.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 측정 취소 버튼 — 측정이 진행 중일 때 앱 정보 버튼 자리에 놓는다. 누르면 그 측정을 버린다(전송하지 않는다 — 보내는 중이면 전송을 끊는다).
 * 무엇을 부를지는 화면이 넘긴다(그 길의 세션).
 */
@Composable
fun CancelMeasureButton(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick = onCancel,
        modifier = modifier.size(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(imageVector = Icons.Default.Close, contentDescription = "측정 취소")
    }
}
