package com.example.vultus

import android.graphics.RectF
import androidx.compose.runtime.State
import androidx.compose.runtime.asIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    /*=======================================================================*/
    /* FaceBox                                                               */
    /*=======================================================================*/
    private val _faceBox = mutableStateOf(RectF())
    val faceBox: State<RectF> = _faceBox
    private val _faceInfo = mutableStateOf<Face?>(null)
    val faceInfo: State<Face?> = _faceInfo
    private val _imageWidth = mutableIntStateOf(0)
    val imageWidth: State<Int> = _imageWidth.asIntState()
    private val _imageHeight = mutableIntStateOf(0)
    val imageHeight: State<Int> = _imageHeight.asIntState()
    private val _isMirrorMode = mutableStateOf(true)
    val isMirrorMode: State<Boolean> = _isMirrorMode

    fun updateFaceResult(faceBox: RectF, faceInfo: Face, imageWidth: Int, imageHeight: Int) {
        _faceBox.value = faceBox
        _faceInfo.value = faceInfo
        _imageHeight.intValue = imageHeight
        _imageWidth.intValue = imageWidth
    }

    /** 얼굴을 놓쳤다 — SDK 가 `null` 을 내면 마지막 얼굴을 지운다. */
    fun clearFaceResult() {
        _faceBox.value = RectF()
        _faceInfo.value = null
    }

    fun setMirroredMode(mirrorMode: Boolean) {
        _isMirrorMode.value = mirrorMode
    }

    /** SDK 프리뷰 뷰가 준 좌표 변환. 앱이 배율 · 미러 · 회전을 다시 조립하지 않는다. */
    private val _previewTransform = mutableStateOf<io.haii.vultus.VultusPreviewTransform?>(null)
    val previewTransform: State<io.haii.vultus.VultusPreviewTransform?> = _previewTransform

    fun setPreviewTransform(transform: io.haii.vultus.VultusPreviewTransform) {
        _previewTransform.value = transform
    }

    /** 프리뷰 뷰 크기(px). 화면 배율(자동 맞춤이 프리뷰를 늘린 몫)을 구하는 데 쓴다. */
    private val _previewSize = mutableStateOf(android.util.Size(0, 0))
    val previewSize: State<android.util.Size> = _previewSize

    fun setPreviewSize(width: Int, height: Int) {
        _previewSize.value = android.util.Size(width, height)
    }

    /** SDK 촬영 조건의 원인 — 얼굴 색을 정한다([com.example.vultus.presentation.common.FaceSizeTarget]). */
    private val _captureCause = mutableStateOf(io.haii.vultus.VultusCaptureCondition.Cause.NONE)
    val captureCause: State<io.haii.vultus.VultusCaptureCondition.Cause> = _captureCause

    fun setCaptureCause(cause: io.haii.vultus.VultusCaptureCondition.Cause) {
        _captureCause.value = cause
    }

    // FER 모델 출력값을 UI에서 표시하기 위한 상태값
    private val _emotionIndex = mutableIntStateOf(-1)
    val emotionIndex: State<Int> = _emotionIndex.asIntState()

    private val _emotionLabel = mutableStateOf("Unknown")
    val emotionLabel: State<String> = _emotionLabel

    fun updateEmotionResult(emotionIndex: Int) {
        _emotionIndex.intValue = emotionIndex
        _emotionLabel.value = emotionIndexToLabel(emotionIndex)
    }

    /** 표정이 없다 — SDK 가 `null` 을 내면(측정이 새로 시작될 때) 앞 측정의 표정을 지운다. */
    fun clearEmotionResult() {
        _emotionIndex.intValue = -1
        _emotionLabel.value = "Unknown"
    }

    private fun emotionIndexToLabel(
        emotionIndex: Int
    ): String {
        return when (emotionIndex) {
            0 -> "Neutral"
            1 -> "Happy"
            2 -> "Sad"
            3 -> "Surprised"
            4 -> "Anxious"
            5 -> "Hurt"
            6 -> "Anger"
            else -> "Unknown"
        }
    }

    /*=======================================================================*/
    /* Reset ViewModel                                                       */
    /*=======================================================================*/
    fun reset() {
        _faceBox.value = RectF()
        _imageWidth.intValue = 0
        _imageHeight.intValue = 0
        _isMirrorMode.value = true
        _faceInfo.value = null

        _emotionIndex.intValue = -1
        _emotionLabel.value = "Unknown"
    }
}