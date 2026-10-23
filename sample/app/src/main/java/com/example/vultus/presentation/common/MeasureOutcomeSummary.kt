package com.example.vultus.presentation.common

import io.haii.vultus.VultusMeasureFailureReason
import io.haii.vultus.Vultus
import io.haii.vultus.VultusError
import io.haii.vultus.VultusMeasureOutcome
import io.haii.vultus.VultusAnalysisResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 측정의 결말(`startMeasure` 가 돌려준 값)을 버튼 위에 띄울 한 줄 요약으로 — 두 길이 같은 것을 쓴다.
 * 응답 JSON 은 클 수 있어 메인 밖에서 파싱한다 — 메인에서 파싱하면 그동안 화면이 멈춘다.
 */
suspend fun summarize(outcome: VultusMeasureOutcome): String = withContext(Dispatchers.Default) { summaryOf(outcome) }

private fun summaryOf(outcome: VultusMeasureOutcome): String = when (outcome) {
    is VultusMeasureOutcome.Finished -> VultusAnalysisResult.fromJson(outcome.responseJson)?.let { result ->
        val hrv = "HRV 수신 — 시간 ${result.hrv.timeDomain.size} · " +
            "주파수 ${result.hrv.freqDomain.size} · 파생 ${result.hrv.derivedData.size}"
        // 분석이 무엇을 얼마나 썼는가. 구 서버 응답이면 없다.
        val analysis = result.analysis?.let {
            // 박동 시각 신뢰 판정. false 여도 HRV 값은 온다 — 참고치로 표시한다.
            val trust = when (it.hrvReliable) {
                true -> " · HRV 신뢰"
                false -> " · HRV 참고치(신뢰 미달)"
                null -> ""
            }
            "분석 %.1f초 · 커버리지 %.0f%%".format(it.analyzedSeconds, it.coverage * 100) + trust
        }
        listOfNotNull(hrv, analysis).joinToString("\n")
    } ?: errorSummary(outcome.responseJson)

    VultusMeasureOutcome.Canceled -> "측정이 취소되었습니다."

    // 사용자 취소와는 다른 경로다. VultusMeasureFailureReason 은 앞으로 늘어날 수 있으므로 else 로 받는다.
    is VultusMeasureOutcome.Failed -> when (outcome.reason) {
        VultusMeasureFailureReason.UNSUPPORTED_IMAGE_FORMAT -> "카메라 이미지 포맷이 잘못됐습니다 (YUV_420_888 필요)."
        VultusMeasureFailureReason.MEASUREMENT_TOO_SHORT -> "측정 길이를 채우지 못해 전송하지 않았습니다."
        else -> "측정에 실패했습니다: ${outcome.reason}"
    }

    // 시작하지 않았다 — 측정이 아예 돌지 않았으므로 사유를 그대로 보인다. 라이선스 거절이면 그 사유도 붙인다.
    is VultusMeasureOutcome.Refused ->
        "측정을 시작하지 못했습니다 — code ${outcome.error.code} " +
            (if (outcome.error == VultusError.LICENSE_INVALID) Vultus.license.error ?: "" else "")
}

/**
 * 결과가 아니면 오류 JSON 의 `error_code` 를 보인다. 제출은 다시 보내지 않으니 다시 측정을 안내하되(가이드 10.5), 서버 인증서를
 * 믿을 수 없으면(측정 결과 제출 `API_TLS_UNTRUSTED` · 보내기 전 라이선스 검사 `TLS_UNTRUSTED`) 같은 네트워크에서 다시 측정해도 같으므로 네트워크를 바꾸도록
 * 안내한다(가이드 10.6).
 */
private fun errorSummary(json: String?): String {
    val code = json?.let { runCatching { org.json.JSONObject(it).optString("error_code") }.getOrNull() }?.takeIf { it.isNotEmpty() }
    return when (code) {
        null -> "응답 파싱 실패"
        "API_TLS_UNTRUSTED", "TLS_UNTRUSTED" -> "결과를 받지 못했습니다 — $code (네트워크 로그인을 마치거나 다른 네트워크에서 다시 측정하세요)"
        else -> "결과를 받지 못했습니다 — $code (다시 측정하세요)"
    }
}
