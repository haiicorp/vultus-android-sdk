package com.example.vultus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.vultus.presentation.common.PreparingScreen
import com.example.vultus.utils.killApp
import io.haii.vultus.Vultus

/**
 * SDK 를 초기화하고 **두 길 중 하나를 고르는** 화면.
 *
 * - [DefaultWayActivity] — SDK 가 카메라를 쥔다. 앱은 프리뷰 뷰를 놓기만 한다(`VultusDefaultSession`).
 * - [CustomWayActivity] — 앱이 카메라를 쥐고 SDK 세션에 프레임을 넘긴다(`VultusCustomSession`).
 *
 * 초기화는 프로세스에 한 번이고 **여기서** 한다. 측정은 다른 화면에서 하지만 조도 · 화면 밝기는 SDK 가 측정하는
 * 화면을 따라가므로(Default 길은 세션을 열 때, Custom 길은 `attachPreview` 가 알린다) 초기화 화면에 묶이지 않는다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupVultus()

        setContent {
            var backPressedTime by remember { mutableLongStateOf(0L) }
            BackHandler {
                if (System.currentTimeMillis() > backPressedTime + 2000) {
                    backPressedTime = System.currentTimeMillis()
                    Toast.makeText(this, "\'뒤로\' 버튼을 한번 더 누르시면 종료됩니다.", Toast.LENGTH_SHORT).show()
                } else {
                    killApp(this)
                }
            }

            val screenModifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
            val error = initializationError
            if (error != null) {
                PreparingScreen(message = "SDK 초기화에 실패했습니다.\n$error", modifier = screenModifier, showProgress = false)
                return@setContent
            }
            val prepareState by Vultus.prepareState.collectAsState()
            // 켜면 측정 화면이 열리자마자 버튼 없이 시작한다(비대화형). 끄면 버튼을 눌러 시작한다(대화형).
            var autoStart by rememberSaveable { mutableStateOf(false) }
            when (prepareState) {
                // LICENSE_INVALID 에서도 **측정 화면을 막지 않는다.** 대부분 일시적 네트워크 단절이고,
                // startMeasure 가 진입할 때 다시 검사해 통과하면 READY 로 돌아간다. 거절되면 버튼이 사유를 띄운다.
                Vultus.PrepareState.READY,
                Vultus.PrepareState.LICENSE_INVALID -> WayChooser(
                    licenseError = if (prepareState == Vultus.PrepareState.LICENSE_INVALID) {
                        Vultus.license.error ?: "사유 불명"
                    } else null,
                    autoStart = autoStart,
                    onAutoStartChange = { autoStart = it },
                    onDefaultWay = { startActivity(Intent(this, DefaultWayActivity::class.java).putExtra(EXTRA_AUTO_START, autoStart)) },
                    onCustomWay = { startActivity(Intent(this, CustomWayActivity::class.java).putExtra(EXTRA_AUTO_START, autoStart)) },
                    modifier = screenModifier,
                )

                Vultus.PrepareState.FAILED ->
                    PreparingScreen(message = "초기화에 실패했습니다.\n앱을 다시 실행해 주세요.", modifier = screenModifier, showProgress = false)

                Vultus.PrepareState.PREPARING ->
                    PreparingScreen(message = "측정 준비 중입니다...", modifier = screenModifier)
            }
        }
    }

    /**
     * 초기화 실패 사유. 성공했으면 null.
     *
     * **초기화 실패는 앱이 반드시 다뤄야 하는 지점이다.** 실패하면 SDK 는 아무것도 하지 않고,
     * `Vultus.getConfiguration()` 처럼 인스턴스를 꺼내는 접근은 `UninitializedPropertyAccessException` 으로
     * 앱을 멈춘다. 그래서 화면을 그리기 전에 여기서 갈라야 한다 — 로그만 남기고 넘어가면 곧바로 그 접근에서 죽는다.
     */
    private var initializationError by mutableStateOf<String?>(null)

    private fun setupVultus() {
        // **재초기화를 가드한다.** initialize 는 두 번째 호출에서 예외를 던지는데, Activity 는 다크모드
        // 전환이나 폰트 크기 변경만으로도 재생성되어 onCreate 가 다시 돈다.
        if (Vultus.isInitialized()) return
        try {
            Vultus.Initializer(BuildConfig.VULTUS_LICENSE_CODE, BuildConfig.VULTUS_HOLDER_KEY, BuildConfig.VULTUS_CLIENT_KEY)
                // 거치형 외장 카메라의 예시값이다 — 표시에 270 을 걸어야 서고, 셀피처럼 좌우를 뒤집어야 사용자의
                // 움직임과 맞는다. 미러는 SDK 프리뷰 뷰가 표시에만 건다. 외장이 꽂혀 있으면 두 길 모두 외장을 연다.
                .extCamConfiguration(Vultus.ExtCamConfiguration(270, useMirrorMode = true))
                // 측정 시작 전에도 얼굴을 검출한다. 기본값(false)이면 bbox 가 안 보여 자세와 거리를 잡을 수가 없다.
                .alwaysDetect(true)
                .enableFER(true)
                .initialize(this, this) // initialize vultus with activity context and lifecycle
        } catch (e: Vultus.InitializationError) {
            initializationError = e.message
        }
    }
}

/** 측정 화면을 열 때 버튼 없이 곧바로 시작할지 — 두 길의 Activity 가 읽는다. */
const val EXTRA_AUTO_START = "com.example.vultus.AUTO_START"

/** 두 길을 고른다. 각 길이 무엇을 쥐는지 한 줄로 붙인다 — SDK 사용자가 자기 앱에 맞는 쪽을 고르게. */
@Composable
private fun WayChooser(
    licenseError: String?,
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onDefaultWay: () -> Unit,
    onCustomWay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Vultus Sample", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = "SDK ${Vultus.sdkVersion}", fontSize = 12.sp, color = Color.Gray)

        WayButton(
            title = "Default",
            detail = "SDK 가 카메라 · 프리뷰 · 줌을 쥔다. 앱은 프리뷰 뷰를 놓기만 한다.",
            onClick = onDefaultWay,
        )
        WayButton(
            title = "Custom",
            detail = "앱이 카메라를 쥐고 SDK 세션에 프레임을 넘긴다. 줌은 SDK 권고를 앱이 건다.",
            onClick = onCustomWay,
        )

        // 시작 방식 — 두 길 모두에 건다.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "자동 시작", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "켜면 측정 화면이 열리자마자 버튼 없이 시작한다(비대화형). 끄면 버튼을 눌러 시작한다(대화형).",
                    fontSize = 12.sp,
                    color = Color.Gray,
                )
            }
            Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
        }

        if (licenseError != null) {
            Text(
                text = "라이선스 검증 실패 — $licenseError",
                color = Color.White,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun WayButton(title: String, detail: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.main_blue)),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
        }
    }
}
