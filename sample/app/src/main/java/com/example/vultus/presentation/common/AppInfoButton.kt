package com.example.vultus.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.haii.vultus.Vultus

/**
 * 앱 정보 버튼 — 누르면 설치 식별자와 SDK 버전을 띄운다.
 *
 * 식별자 · 버전은 팝업으로 뺐다. 프리뷰를 가리는 것이 적을수록 자세를 잡기 쉽고, 이 둘은 측정 중에 볼 값이
 * 아니라 사후에 대조할 값이다.
 */
@Composable
fun AppInfoButton(installationId: String, modifier: Modifier = Modifier) {
    var infoShown by remember { mutableStateOf(false) }

    FilledTonalIconButton(
        onClick = { infoShown = true },
        modifier = modifier.size(50.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(imageVector = Icons.Default.Info, contentDescription = "앱 정보")
    }

    if (infoShown) {
        AppInfoDialog(
            installationId = installationId.ifEmpty { "--" },
            onDismiss = { infoShown = false },
        )
    }
}

/**
 * 설치 식별자와 SDK 버전.
 *
 * `installationId` 는 측정 meta 의 appendix 로도 함께 나가므로 서버 기록과 맞춰 볼 수
 * 있다. **그래서 복사할 수 있어야 한다** — 눈으로 옮겨 적다 틀리면 엉뚱한 세션을 본다.
 */
@Composable
private fun AppInfoDialog(
    installationId: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
        title = { Text("앱 정보") },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "SampleAppIdentifier", fontSize = 11.sp)
                    Text(text = installationId, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Vultus", fontSize = 11.sp)
                    Text(text = Vultus.sdkVersion, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
    )
}
