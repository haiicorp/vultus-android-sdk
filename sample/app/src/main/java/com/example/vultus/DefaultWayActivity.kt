package com.example.vultus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.example.vultus.presentation.common.summarize
import com.example.vultus.presentation.defaultway.DefaultMeasureView
import io.haii.vultus.VultusDefaultSession
import io.haii.vultus.VultusMeasureOutcome

/**
 * **Default 길** — SDK 가 카메라 · 프리뷰 · 자동 맞춤(줌)을 쥔다. 두 길이 갈리는 곳은 이 Activity 와
 * [CustomWayActivity] 뿐이다.
 *
 * 1. 측정 화면을 띄운다 — [DefaultMeasureView] 가 프리뷰 뷰를 놓고 `Vultus.openDefaultSession` 으로 세션을 열고, 세션의 스냅숏을 읽고,
 *    `session.startMeasure` 로 측정해 결말을 받는다
 * 2. 떠날 때 진행 중인 측정을 버리고(`session.cancelMeasure()`) `session.close()` — 화면이 없어질 때(`ON_DESTROY`)도 SDK 가 닫지만 곧바로 놓는다
 */
class DefaultWayActivity : ComponentActivity() {
    private val mvm: MainViewModel by viewModels()

    /** 측정 결과 요약. 버튼 위에 한 줄로 뜬다 — 시작 거절 · 전송 완료 · 취소 · 실패가 모두 여기로 모인다. */
    private var resultSummary by mutableStateOf<String?>(null)

    /** 측정 화면이 연 세션. */
    private var session: VultusDefaultSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 1. 측정 화면 — 자동 시작이면 열리자마자 버튼 없이 시작한다(비대화형)
        val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START, false)
        setContent {
            DefaultMeasureView(
                autoStart = autoStart,
                resultSummary = resultSummary,
                onResultSummary = { resultSummary = it },
                onSession = { session = it },
                onOutcome = ::onOutcome,
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            )
        }
    }

    /** 측정의 결말 — 요약을 띄우고, 측정이 돌았으면 화면 값을 비운다. */
    private suspend fun onOutcome(outcome: VultusMeasureOutcome) {
        if (outcome !is VultusMeasureOutcome.Refused) mvm.reset()
        resultSummary = summarize(outcome)
    }

    /**
     * 2. 떠날 때는 **곧바로** 세션을 닫는다. 화면이 없어질 때(`ON_DESTROY`)도 닫히지만 그것은 뒤로 간 한참 뒤일 수 있고, 그 사이 Custom 화면이
     * 세션을 열면 "다른 길이 도는 중" 으로 거절된다. 진행 중인 측정은 먼저 버린다 — 닫기만으로는 멈춘 측정(정리 · 전송 중)이 끝까지 보내진다.
     */
    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            session?.cancelMeasure()
            session?.close()
            session = null
        }
    }
}
