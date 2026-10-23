package com.example.vultus.presentation.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.vultus.R
import io.haii.vultus.VultusMeasureProgress
import io.haii.vultus.VultusMeasureState
import io.haii.vultus.VultusMeasureOutcome
import kotlinx.coroutines.launch

/**
 * 측정 시작 · 종료 버튼. 무엇을 부를지는 화면이 넘긴다(그 길의 세션).
 *
 * @param progress 진행은 **SDK 가 센다.** 앱이 벽시계로 세면 얼굴을 놓친 구간까지 진행으로 쳐서,
 *   서버가 계산하지 못하는 측정을 "다 모았다" 고 표시하게 된다.
 * @param onStart 측정을 시작하고 끝날 때까지 기다린다 — 결말(또는 거절 사유)을 돌려준다.
 * @param onOutcome 결말을 받는다.
 */
@Composable
fun MeasureButton(
    state: VultusMeasureState,
    progress: VultusMeasureProgress,
    onStart: suspend () -> VultusMeasureOutcome,
    onStop: () -> Unit,
    onOutcome: suspend (VultusMeasureOutcome) -> Unit,
    modifier: Modifier = Modifier,
    onResultSummary: (String?) -> Unit = {},
) {
    val scope = rememberCoroutineScope()

    Button(
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            // 측정 중에는 빨간색. 시작 직후에는 맥파 버퍼가 차기를 기다리느라 BPM·그래프가
            // 비어 있으므로, 색과 문구가 측정이 시작됐다는 신호가 된다.
            containerColor = if (state == VultusMeasureState.STARTED) Color.Red
            else colorResource(id = R.color.main_blue),
            disabledContainerColor = colorResource(id = R.color.disalbed_gray)
        ),
        enabled = state == VultusMeasureState.IDLE || state == VultusMeasureState.STARTED,
        shape = RoundedCornerShape(10.dp),
        onClick = {
            when (state) {
                VultusMeasureState.IDLE -> {
                    onResultSummary(null)
                    // 메인에서 불러도 된다 — 측정은 SDK 가 백그라운드에서 돌린다. 측정이 끝나면 결말이 돌아온다(시작을 거절했으면 그 사유).
                    // 이 화면을 떠나면 이 작업과 함께 측정도 취소된다.
                    scope.launch { onOutcome(onStart()) }
                }
                VultusMeasureState.STARTED -> onStop()
                else -> { /* do nothing */
                }
            }
        },
    ) {
        when (state) {
            VultusMeasureState.IDLE -> ButtonText("측정 시작")
            // 라이선스 확인 · 준비 · 비대화형 시작의 맞춤과 카운트다운을 기다린다 — 다시 누르지 않게 막아 둔다.
            VultusMeasureState.STARTING -> ButtonText("준비 중...")
            VultusMeasureState.ADJUSTING_CAMERA -> ButtonText("카메라 조정 중...")
            // 남은 시간이 아니라 **충족률**이다. 진행이 0 으로 돌아가는 것(앵커 전
            // 끊김)도 그대로 보여야 사용자가 자세를 고칠 이유를 안다. 100% 가 되는 순간 SDK 가 멈춘다.
            VultusMeasureState.STARTED -> ButtonText(
                "측정중 ${progress.percent}% (${progress.elapsedSeconds}초), 탭하여 종료"
            )
            VultusMeasureState.STOPPED -> ButtonText("정리 중...")
            VultusMeasureState.FINISHED -> ButtonText("결과 전송 중...")
            VultusMeasureState.SUBMITTING -> ButtonText("결과 전송 중...")
            VultusMeasureState.CANCELED -> ButtonText("취소됨")
        }
    }
}

@Composable
fun ButtonText(text: String, color: Color = Color.White) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Visible,
    )
}