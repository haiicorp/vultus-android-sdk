package com.example.vultus.presentation.defaultway

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Dispatchers
import io.haii.vultus.VultusMeasureState
import io.haii.vultus.VultusStartMode
import io.haii.vultus.VultusExternalCameraStatus
import com.example.vultus.camera.UsbCameraPermission
import com.example.vultus.camera.externalCameraAdapters
import androidx.compose.runtime.DisposableEffect
import com.example.vultus.utils.InstallationIdProvider
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vultus.MainViewModel
import com.example.vultus.presentation.common.AppInfoButton
import com.example.vultus.presentation.common.CancelMeasureButton
import com.example.vultus.presentation.common.GuidanceNotice
import com.example.vultus.presentation.common.MEASURE_SECONDS
import com.example.vultus.presentation.common.MeasureButton
import com.example.vultus.presentation.common.MeasurePreviewBox
import com.example.vultus.presentation.common.MeasureSignalBox
import com.example.vultus.presentation.common.MetricsRow
import com.example.vultus.presentation.common.ResultSummaryText
import com.example.vultus.utils.rememberInstallationId
import io.haii.vultus.Vultus
import io.haii.vultus.VultusFaceGuide
import io.haii.vultus.VultusPreviewFill
import io.haii.vultus.VultusFramingState
import io.haii.vultus.VultusPreviewView
import io.haii.vultus.VultusDefaultSession
import io.haii.vultus.VultusError
import io.haii.vultus.VultusMeasureOutcome
import io.haii.vultus.VultusSnapshot
import io.haii.vultus.getOrElse
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * **Default 길 측정 화면** — 카메라 · 프리뷰 · 자동 맞춤을 SDK 가 쥔다.
 *
 * 1. 프리뷰 뷰를 놓고 `Vultus.openDefaultSession` 에 **화면 수명**과 함께 넘겨 세션을 연다. 카메라 선택 · 미러 · 회전은 SDK 가 정한다
 * 2. 상태를 세션의 **스냅숏** 하나로 읽는다 — 상태 · 진행 · 안내 · BPM 이 같은 순간의 값이다. 그래프 · 얼굴 · 표정은 따로 온다
 * 3. `session.startMeasure` 로 측정하고 결말을 돌려받는다 · `session.stopMeasure` 로 멈춘다 · `session.cancelMeasure` 로 버린다
 * 4. (거치형) 외장(USB) 카메라 어댑터를 붙이고 USB 권한을 묻는다 — 카메라 설정 · 3A 락 · 줌을 SDK 가 USB 로 건다
 *
 * 화면을 이루는 조각(`common`)은 값만 받아 그린다 — 어느 길인지 모른다.
 * ⚠️ **SDK 패키지에는 Compose 의존성이 없다** — 아래 `AndroidView` 가 SDK 프리뷰 뷰를 Compose 에 얹는 예제다.
 */
@Composable
fun DefaultMeasureView(
    /** 화면이 열리자마자 버튼 없이 시작한다(비대화형). */
    autoStart: Boolean,
    resultSummary: String?,
    onResultSummary: (String?) -> Unit,
    /** 세션을 열었다(`null` 이면 닫았다) — Activity 가 떠날 때 닫는다. */
    onSession: (VultusDefaultSession?) -> Unit,
    /** 측정의 결말. */
    onOutcome: suspend (VultusMeasureOutcome) -> Unit,
    modifier: Modifier = Modifier,
    mvm: MainViewModel = viewModel(),
) {
    // 1. 세션 — 프리뷰 뷰를 만드는 자리에서 연다(아래). 열기 전에는 초기 스냅숏을 그린다.
    var session by remember { mutableStateOf<VultusDefaultSession?>(null) }

    // 2. 상태 — 스냅숏 하나
    val snapshot by remember(session) { session?.snapshot ?: MutableStateFlow(VultusSnapshot()) }.collectAsState()
    // 그래프 신호는 매 프레임 바뀐다 — 흐름째 그래프에 넘겨 그래프만 받는다.
    val signalTrace = remember(session) { session?.signalTrace ?: MutableStateFlow(FloatArray(0)) }

    // 매 프레임 바뀌는 값은 뷰모델로 — 오버레이와 지표만 읽는다.
    LaunchedEffect(session) {
        session?.detectedFace?.collect { face ->
            if (face != null) mvm.updateFaceResult(face.boundingBox, face.faceInfo, face.imageWidth, face.imageHeight)
            else mvm.clearFaceResult()
        }
    }
    LaunchedEffect(snapshot.captureCondition) { mvm.setCaptureCause(snapshot.captureCondition.cause) }
    LaunchedEffect(session) {
        session?.emotion?.collect { e -> if (e != null) mvm.updateEmotionResult(e.emotionIndex) else mvm.clearEmotionResult() }
    }

    // 4. 외장(USB) 카메라 — 꽂힌 카메라에 맞는 어댑터가 없으면 SDK 는 그 카메라에 USB 요청을 보내지 않는다. **USB 권한은 앱이 묻는다** —
    // 권한이 없으면 상태가 `NO_PERMISSION` 이고, 허용되면 어댑터를 다시 붙여 곧바로 설정이 걸리게 한다.
    // 이 샘플은 어댑터를 싣지 않는다 — 기기의 외장 카메라에 맞는 어댑터를 `externalCameraAdapters` 에 넣는다(가이드 §8.2).
    val context = LocalContext.current
    LaunchedEffect(session) {
        val s = session ?: return@LaunchedEffect
        if (externalCameraAdapters.isEmpty()) return@LaunchedEffect
        s.attachExternalCameraAdapters(externalCameraAdapters)
        s.externalCameraStatus.collect { status ->
            if (status.state == VultusExternalCameraStatus.State.NO_PERMISSION) {
                UsbCameraPermission.request(context) { s.attachExternalCameraAdapters(externalCameraAdapters) }
            }
        }
    }
    DisposableEffect(Unit) { onDispose { UsbCameraPermission.release(context) } }

    // 비대화형 시작 — 카메라가 프레임을 내기 시작하면(회전이 정해지면) 버튼 없이 시작한다. 맞춰지면 카운트다운
    // (프리뷰 가운데)을 센 뒤 시작하고, 제한 시간 안에 맞춰지지 않으면 시작하지 않는다. 화면을 떠나면 이 작업과 함께 측정도 취소된다.
    if (autoStart) {
        LaunchedEffect(session) {
            val s = session ?: return@LaunchedEffect
            s.effectiveOrientation.filterNotNull().first()
            val id = withContext(Dispatchers.IO) { InstallationIdProvider.getId(context) }
            onOutcome(
                s.startMeasure(
                    analysisWindow = MEASURE_SECONDS,
                    mode = VultusStartMode.NonInteractive(),
                    appendix = buildMap { if (id.isNotEmpty()) put("installationId", id) },
                )
            )
        }
    }

    val state = snapshot.state
    // 뒤로 가기는 막지 않는다 — 떠나면 Activity 가 진행 중인 측정을 버리고(`cancelMeasure`) 세션을 닫는다.

    val installationId = rememberInstallationId()
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier.background(Color.Black)) {
        MeasurePreviewBox(countdown = (snapshot.framing as? VultusFramingState.Countdown)?.secondsLeft) { previewModifier ->
            // 1. 카메라 · 프리뷰. ⚠️ **뷰를 만드는 자리에서 바로 연다.** `DisposableEffect` 로 옮기면 재구성마다
            // 키가 흔들려 닫기 → 열기가 왕복하고, 그 사이 카메라가 한 번 닫힌다.
            AndroidView(
                factory = { ctx ->
                    VultusPreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        fill = VultusPreviewFill.FILL
                        // 오버레이가 좌우를 뒤집어야 하는지와 좌표 변환은 **뷰가 알려준다** —
                        // 스트림 미러 여부를 CameraX 가 나중에 알려주므로 물어보면 늦는다.
                        onOverlayMirroredChanged = { mirrored -> mvm.setMirroredMode(mirrored) }
                        onOverlayTransformChanged = { transform -> mvm.setPreviewTransform(transform) }
                        // 맞추는 동안 얼굴이 렌즈 중심(광축)에서 벗어나면 목적지점(렌즈 중심) · 현재 지점(얼굴) crosshair 를
                        // 띄운다. 모양은 앱의 뷰로 바꿀 수 있다: SDK 는 뷰의 중심을 그 점에 맞추고 보이거나 숨기기만 한다.
                        // 여기서는 목적지점만 바꾸고 현재 지점은 기본 모양(×)을 쓴다.
                        showsFramingCrosshair = true
                        framingTargetCrosshairView = ComposeView(ctx).apply {
                            val side = (36 * ctx.resources.displayMetrics.density).toInt()
                            layoutParams = FrameLayout.LayoutParams(side, side)
                            setContent { SampleCrosshair() }
                        }
                    }.also { view ->
                        // 키오스크(외장 USB 카메라)와 폰(전면)을 같은 코드로 다룬다 — 초기화 때 외장 카메라
                        // 설정을 적었으므로 외장이 연결돼 있으면 외장, 없으면 전면이 열린다.
                        val opened = Vultus.openDefaultSession(view, lifecycleOwner).getOrElse { error ->
                            Log.e("DefaultMeasureView", "세션을 열지 못했습니다 — code ${error.code}")
                            onResultSummary("세션을 열지 못했습니다 — code ${error.code}")
                            return@also
                        }
                        // 얼굴을 보일 자리 — 프리뷰 폭의 65 %, 가운데. SDK 가 줌과 프리뷰 배율로 얼굴을 여기에 얹는다.
                        opened.setFaceGuide(VultusFaceGuide(widthFraction = 0.65f, centerX = 0.5f, centerY = 0.5f))
                        session = opened
                        onSession(opened)
                    }
                },
                onRelease = {
                    session?.close()
                    session = null
                    onSession(null)
                },
                modifier = previewModifier.onSizeChanged { mvm.setPreviewSize(it.width, it.height) },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricsRow(state, snapshot.displayBpm, snapshot.signalClarity, snapshot.framing, mvm)

            // 그래프는 지표창 바로 아래에 붙인다. 화면 하단에 두면 측정 자세를 잡는
            // 동안 얼굴 영역을 가린다.
            MeasureSignalBox(signalTrace)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResultSummaryText(resultSummary)

                // 측정 안내. 측정 전에는 촬영 조건을, 측정 중에는 가장 앞선 안내 하나를 띄운다.
                // 안내 전용이라 측정은 그대로 이어진다.
                GuidanceNotice(snapshot.guidance, modifier = Modifier.padding(bottom = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeasureButton(
                        state = state,
                        progress = snapshot.progress,
                        // 3. 측정 — 끝날 때까지 기다렸다가 결말을 돌려받는다. appendix 는 측정 meta 에 그대로 실린다(앱이 싣는 값이다).
                        onStart = {
                            session?.startMeasure(
                                analysisWindow = MEASURE_SECONDS,
                                appendix = buildMap { if (installationId.isNotEmpty()) put("installationId", installationId) },
                            ) ?: VultusMeasureOutcome.Refused(VultusError.NOT_INITIALIZED)
                        },
                        onStop = { session?.stopMeasure() },
                        onOutcome = onOutcome,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        onResultSummary = onResultSummary,
                    )
                    // 측정이 진행 중이면 앱 정보 자리에 취소를 둔다 — `cancelMeasure` 는 그 측정을 버리고 전송하지 않는다(보내는 중이면 전송을 끊는다).
                    if (state != VultusMeasureState.IDLE && state != VultusMeasureState.CANCELED) CancelMeasureButton(onCancel = { session?.cancelMeasure() })
                    else AppInfoButton(installationId)
                }
            }
        }
    }
}

/** 맞춤 crosshair 의 앱 모양 예제 — 고리와 가운데 점. */
@Composable
private fun SampleCrosshair() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r - 2.dp.toPx(), style = Stroke(width = 2.dp.toPx()))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = 2.5.dp.toPx())
    }
}
