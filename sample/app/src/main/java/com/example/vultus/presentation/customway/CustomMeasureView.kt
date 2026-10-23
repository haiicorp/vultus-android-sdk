package com.example.vultus.presentation.customway

import io.haii.vultus.VultusExternalCameraStatus
import com.example.vultus.camera.UsbCameraPermission
import com.example.vultus.camera.externalCameraAdapters
import androidx.compose.runtime.DisposableEffect
import androidx.camera.core.ExperimentalLensFacing

import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import io.haii.vultus.VultusMeasureState
import io.haii.vultus.VultusStartMode
import com.example.vultus.utils.InstallationIdProvider
import androidx.compose.ui.platform.LocalContext
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
import io.haii.vultus.VultusCustomSession
import io.haii.vultus.VultusMeasureOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull

/**
 * **Custom 길 측정 화면** — 앱이 카메라를 쥐고, SDK 는 프레임을 받아 측정한다.
 *
 * 1. `ImageAnalysis` 를 만들 때 [VultusCustomSession.prepareImageAnalysis] 를 거치고, 분석기로 [VultusCustomSession.imageAnalyzer] 를
 *    건다. 바인딩한 `Camera` 를 [VultusCustomSession.attachCamera] 로 알린다(3A 락 · 줌 상태 · 타임스탬프 기준) — [bindCamera]
 * 2. 프리뷰를 SDK 프리뷰 뷰에 그리고 [VultusCustomSession.attachPreview] 로 붙인다 — 가이드 · 표시 맞춤 · 오버레이 변환
 * 3. 자동 맞춤 **권고**([VultusCustomSession.framingAdvice])를 따를지 정한다. SDK 는 앱의 카메라를 바꾸지 않는다 —
 *    이 예제는 [VultusCustomSession.applyFramingZoom] 으로 권고 줌을 건다
 * 4. 상태를 세션의 **스냅숏** 하나로 읽는다 — Default 길과 같은 값이다
 * 5. `session.startMeasure` 로 측정하고 결말을 돌려받는다 · `session.stopMeasure` 로 멈춘다 · `session.cancelMeasure` 로 버린다
 *
 * 6. (거치형) 외장(USB) 카메라가 연결돼 있으면 그것을, 없으면 전면을 바인딩한다 — 초기화 때 적은 외장 카메라 설정(표시 회전 · 미러)을
 *    프리뷰 뷰에 걸고, 어댑터를 붙이고 USB 권한을 묻는다 — 카메라 설정 · 3A 락 · 줌을 SDK 가 USB 로 건다
 *
 * 화면을 이루는 조각(`common`)은 값만 받아 그린다 — 어느 길인지 모른다.
 */
@Composable
fun CustomMeasureView(
    session: VultusCustomSession,
    /** 화면이 열리자마자 버튼 없이 시작한다(비대화형). */
    autoStart: Boolean,
    resultSummary: String?,
    onResultSummary: (String?) -> Unit,
    /** 측정의 결말. */
    onOutcome: suspend (VultusMeasureOutcome) -> Unit,
    modifier: Modifier = Modifier,
    mvm: MainViewModel = viewModel(),
) {
    // 4. 상태 — 스냅숏 하나
    val snapshot by session.snapshot.collectAsState()
    val state = snapshot.state

    // 매 프레임 바뀌는 값은 뷰모델로 — 오버레이와 지표만 읽는다.
    LaunchedEffect(session) {
        session.detectedFace.collect { face ->
            if (face != null) mvm.updateFaceResult(face.boundingBox, face.faceInfo, face.imageWidth, face.imageHeight)
            else mvm.clearFaceResult()
        }
    }
    LaunchedEffect(snapshot.captureCondition) { mvm.setCaptureCause(snapshot.captureCondition.cause) }
    LaunchedEffect(session) {
        session.emotion.collect { e -> if (e != null) mvm.updateEmotionResult(e.emotionIndex) else mvm.clearEmotionResult() }
    }

    val context = LocalContext.current

    // 비대화형 시작 — 카메라가 프레임을 내기 시작하면(회전이 정해지면) 버튼 없이 시작한다. 맞춰지면 카운트다운
    // (프리뷰 가운데)을 센 뒤 시작하고, 제한 시간 안에 맞춰지지 않으면 시작하지 않는다. 화면을 떠나면 이 작업과 함께 측정도 취소된다.
    if (autoStart) {
        LaunchedEffect(session) {
            session.effectiveOrientation.filterNotNull().first()
            val id = withContext(Dispatchers.IO) { InstallationIdProvider.getId(context) }
            onOutcome(
                session.startMeasure(
                    analysisWindow = MEASURE_SECONDS,
                    mode = VultusStartMode.NonInteractive(),
                    appendix = buildMap { if (id.isNotEmpty()) put("installationId", id) },
                )
            )
        }
    }

    // 뒤로 가기는 막지 않는다 — 떠나면 Activity 가 진행 중인 측정을 버리고(`cancelMeasure`) 세션을 닫는다.

    val installationId = rememberInstallationId()
    val lifecycleOwner = LocalLifecycleOwner.current
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<VultusPreviewView?>(null) }

    // 6. 외장(USB) 카메라 — 어댑터를 붙여 두면 꽂힌 카메라에 맞는 것이 쓰인다. 연결된 외장 카메라가 없으면 SDK 는 USB 요청을 보내지
    // 않는다. **USB 권한은 앱이 묻는다** — 권한이 없으면 상태가 `NO_PERMISSION` 이고, 허용되면 어댑터를 다시 붙여 곧바로 설정이 걸리게 한다.
    // 이 샘플은 어댑터를 싣지 않는다 — 기기의 외장 카메라에 맞는 어댑터를 `externalCameraAdapters` 에 넣는다(가이드 §8.2).
    LaunchedEffect(session) {
        if (externalCameraAdapters.isEmpty()) return@LaunchedEffect
        session.attachExternalCameraAdapters(externalCameraAdapters)
        session.externalCameraStatus.collect { status ->
            if (status.state == VultusExternalCameraStatus.State.NO_PERMISSION) {
                UsbCameraPermission.request(context) { session.attachExternalCameraAdapters(externalCameraAdapters) }
            }
        }
    }
    DisposableEffect(session) { onDispose { UsbCameraPermission.release(context) } }
    // 분석과 프리뷰가 다르게 섰으면(기기를 눕혔을 때 등) 오버레이가 그 차만큼 돌린다 — Custom 길은 앱이 넘긴다.
    LaunchedEffect(session, previewView) {
        val view = previewView ?: return@LaunchedEffect
        session.effectiveOrientation.collect { view.setEffectiveOrientation(it) }
    }

    // 3. 권고를 따른다 — 줌을 걸지 말지는 앱이 정한다. 권고는 줌을 바꿔도 되는 때(측정 전 — 비대화형 시작이 맞춤을 기다리는 동안 포함)에만
    // 오므로 오는 대로 건다.
    LaunchedEffect(session, camera) {
        val cam = camera ?: return@LaunchedEffect
        session.framingAdvice.filterNotNull().collect { advice -> session.applyFramingZoom(cam, advice) }
    }

    Box(modifier = modifier.background(Color.Black)) {
        MeasurePreviewBox(countdown = (snapshot.framing as? VultusFramingState.Countdown)?.secondsLeft) { previewModifier ->
            // 1 · 2. 카메라 · 프리뷰 — 앱이 바인딩하고 세션에 잇는다
            AndroidView(
                factory = { ctx ->
                    VultusPreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        fill = VultusPreviewFill.FILL
                        // 얼굴을 보일 자리 — Default 길 예제와 같게 프리뷰 폭의 65 %, 가운데.
                        faceGuide = VultusFaceGuide(widthFraction = 0.65f, centerX = 0.5f, centerY = 0.5f)
                        // 맞추는 동안 얼굴이 렌즈 중심에서 벗어나면 SDK 의 기본 crosshair(목적지점 + · 현재 지점 ×)를 띄운다.
                        showsFramingCrosshair = true
                        onOverlayMirroredChanged = { mirrored -> mvm.setMirroredMode(mirrored) }
                        onOverlayTransformChanged = { transform -> mvm.setPreviewTransform(transform) }
                    }.also { view ->
                        session.attachPreview(view)
                        previewView = view
                        bindCamera(view, lifecycleOwner, session) { camera = it }
                    }
                },
                onRelease = {
                    session.attachPreview(null)
                    previewView = null
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
            // 그래프 신호는 매 프레임 바뀐다 — 흐름째 넘겨 그래프만 받는다.
            MeasureSignalBox(session.signalTrace)

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
                        // 5. 측정 — 끝날 때까지 기다렸다가 결말을 돌려받는다. appendix 는 측정 meta 에 그대로 실린다(앱이 싣는 값이다).
                        onStart = {
                            session.startMeasure(
                                analysisWindow = MEASURE_SECONDS,
                                appendix = buildMap { if (installationId.isNotEmpty()) put("installationId", installationId) },
                            )
                        },
                        onStop = { session.stopMeasure() },
                        onOutcome = onOutcome,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        onResultSummary = onResultSummary,
                    )
                    // 측정이 진행 중이면 앱 정보 자리에 취소를 둔다 — `cancelMeasure` 는 그 측정을 버리고 전송하지 않는다(보내는 중이면 전송을 끊는다).
                    if (state != VultusMeasureState.IDLE && state != VultusMeasureState.CANCELED) CancelMeasureButton(onCancel = { session.cancelMeasure() })
                    else AppInfoButton(installationId)
                }
            }
        }
    }
}

/**
 * 카메라에 프리뷰 · 분석을 바인딩하고 세션에 알린다. 초기화 때 외장 카메라 설정을 적었고 외장(USB) 카메라가 연결돼 있으면 외장을, 아니면
 * 전면을 쓴다(Default 길의 `PREFER_EXTERNAL` 과 같은 규칙).
 */
@OptIn(ExperimentalLensFacing::class)
private fun bindCamera(
    view: VultusPreviewView,
    lifecycleOwner: LifecycleOwner,
    session: VultusCustomSession,
    onBound: (Camera) -> Unit,
) {
    val context = view.context
    val future = ProcessCameraProvider.getInstance(context)
    future.addListener({
        val provider = future.get()
        val extCam = Vultus.getConfiguration().extCamConfiguration
        val external = extCam?.let {
            provider.availableCameraInfos.firstOrNull { info -> info.lensFacing == CameraSelector.LENS_FACING_EXTERNAL }
        }
        val cameraInfo = external
            ?: CameraSelector.DEFAULT_FRONT_CAMERA.filter(provider.availableCameraInfos).firstOrNull()
        if (cameraInfo == null) {
            Log.e("CustomMeasureView", "쓸 수 있는 카메라가 없습니다")
            return@addListener
        }
        val selector = CameraSelector.Builder().addCameraFilter { infos -> infos.filter { it === cameraInfo } }.build()
        // 표시 미러 · 회전 — 외장 카메라는 CameraX 가 축도 미러도 알려 주지 못해 설정에 적은 값을 뷰에 못 박는다.
        // 프리뷰 use case 에는 회전을 걸지 않는다(뷰가 한 곳에서 정한다).
        if (external != null && extCam != null) {
            view.showsMirrored = extCam.useMirrorMode
            view.overridePreviewRotation(extCam.rotationDegrees)
        } else {
            view.showsMirrored = true
            view.overridePreviewRotation(null)
        }

        val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }

        val analysisBuilder = ImageAnalysis.Builder()
            // 처리가 밀리면 최신 프레임만 — SDK 는 받는 즉시 사본을 떠 돌려준다.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // 분석은 480p — 대부분의 전면 카메라가 기본으로 내는 크기이고 Default 길도 같다.
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
        // 카메라 메타(AWB · ISO · 노출) 수집 — 바인딩 전에만 걸 수 있다.
        session.prepareImageAnalysis(analysisBuilder)
        // 권고 캡처 설정(고정 톤 곡선 · 노이즈 제거 OFF)과 30 fps 고정 — Default 길이 하는 것과 같다.
        Vultus.applyRecommendedCapture(analysisBuilder, cameraInfo)
        pinFrameRate(analysisBuilder, cameraInfo, FRAME_RATE)

        val analysisUseCase = analysisBuilder.build()
            .also { it.setAnalyzer(session.analysisExecutor, session.imageAnalyzer) }

        try {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysisUseCase)
            // 3A 락 · 줌 상태 · 타임스탬프 기준이 이 핸들에서 나온다. 바인딩 뒤에만 있다.
            session.attachCamera(camera)
            onBound(camera)
        } catch (e: Exception) {
            Log.e("CustomMeasureView", "카메라를 바인딩하지 못했습니다", e)
        }
    }, ContextCompat.getMainExecutor(context))
}

/**
 * AE 프레임률 범위를 `[fps, fps]` 로 못 박는다. **카메라가 그 범위를 신고할 때만** — 목록 밖 값을 요청하면
 * 기기가 거절하지 않고 화각을 바꿀 수 있다.
 */
@OptIn(ExperimentalCamera2Interop::class)
private fun pinFrameRate(builder: ImageAnalysis.Builder, cameraInfo: CameraInfo, fps: Int) {
    val ranges = Camera2CameraInfo.from(cameraInfo)
        .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
    if (ranges?.any { it.lower == fps && it.upper == fps } != true) {
        Log.w("CustomMeasureView", "$fps fps 를 못 박는다 — 목표 범위에 [$fps,$fps] 가 없다")
        return
    }
    Camera2Interop.Extender(builder).setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(fps, fps))
}

/** HRV 측정은 30 fps 에서 타협하지 않는다 — 어두우면 fps 를 내주지 않고 밝은 곳을 안내한다. */
private const val FRAME_RATE = 30
