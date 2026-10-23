package com.example.vultus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.example.vultus.presentation.common.PreparingScreen
import com.example.vultus.presentation.common.summarize
import com.example.vultus.presentation.customway.CustomMeasureView
import io.haii.vultus.Vultus
import io.haii.vultus.VultusCustomSession
import io.haii.vultus.VultusMeasureOutcome
import io.haii.vultus.VultusOpenResult

/**
 * **Custom 길** — 앱이 카메라를 쥐고 SDK 세션에 프레임을 넘긴다. 두 길이 갈리는 곳은 이 Activity 와
 * [DefaultWayActivity] 뿐이다.
 *
 * 세션은 이 화면의 것이다 — 들어올 때 열고 떠날 때 닫는다.
 * 1. `Vultus.openCustomSession()` 으로 세션을 연다 — 열린 세션은 하나라 Default 세션이 열려 있는 동안에는 열리지 않는다
 * 2. 측정 화면을 띄운다 — [CustomMeasureView] 가 카메라를 바인딩해 세션에 잇고, 세션의 스냅숏을 읽고,
 *    `session.startMeasure` 로 측정해 결말을 받는다
 * 3. 떠날 때 진행 중인 측정을 버리고(`session.cancelMeasure()`) `session.close()` 하고 카메라를 놓는다
 */
class CustomWayActivity : ComponentActivity() {
    private val mvm: MainViewModel by viewModels()

    /** 측정 결과 요약. 버튼 위에 한 줄로 뜬다 — 시작 거절 · 전송 완료 · 취소 · 실패가 모두 여기로 모인다. */
    private var resultSummary by mutableStateOf<String?>(null)

    private var session: VultusCustomSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. 세션
        val opened = Vultus.openCustomSession()
        val session = (opened as? VultusOpenResult.Success)?.session
        this.session = session

        // 2. 측정 화면 — 자동 시작이면 열리자마자 버튼 없이 시작한다(비대화형)
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)
        setContent {
            val screenModifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
            if (session == null) {
                val code = (opened as? VultusOpenResult.Failure)?.error?.code
                PreparingScreen(message = "세션을 열지 못했습니다 — code $code", modifier = screenModifier, showProgress = false)
            } else {
                CustomMeasureView(
                    session = session,
                    autoStart = autoStart,
                    resultSummary = resultSummary,
                    onResultSummary = { resultSummary = it },
                    onOutcome = ::onOutcome,
                    modifier = screenModifier,
                )
            }
        }
    }

    /** 측정의 결말 — 요약을 띄우고, 측정이 돌았으면 화면 값을 비운다. */
    private suspend fun onOutcome(outcome: VultusMeasureOutcome) {
        if (outcome !is VultusMeasureOutcome.Refused) mvm.reset()
        resultSummary = summarize(outcome)
    }

    /** 3. 떠날 때는 **곧바로** 세션을 닫는다 — 뒤이어 Default 화면이 열 때 "다른 길이 도는 중" 이 되지 않게. */
    override fun onPause() {
        super.onPause()
        if (isFinishing) closeSession()
    }

    override fun onDestroy() {
        closeSession()
        super.onDestroy()
    }

    /** 진행 중인 측정을 버리고 세션을 닫고 카메라를 놓는다 — 다음 화면이 카메라와 세션을 새로 잡을 수 있게. 닫기만으로는 멈춘 측정(정리 · 전송 중)이 끝까지 보내진다. */
    private fun closeSession() {
        val closing = session ?: return
        session = null
        val provider = ProcessCameraProvider.getInstance(this)
        provider.addListener({ runCatching { provider.get().unbindAll() } }, ContextCompat.getMainExecutor(this))
        closing.cancelMeasure()
        closing.close()
    }
}
