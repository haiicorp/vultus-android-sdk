package com.example.vultus.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

object InstallationIdProvider {
    private const val PREF_FILE_NAME = "device_id_prefs"

    /**
     * 저장 키.
     */
    private const val PREF_KEY_ID = "installation_id_v2"

    /**
     * Crockford Base32. `I` `L` `O` `U` 가 빠져 있어 1/0 과 헷갈리지 않고
     * 우연히 단어가 만들어지지도 않는다. 전화로 불러 주기 좋다.
     */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    private var uniqueID: String? = null

    /**
     * 설치 식별자. `XXXX-XXXX` 형태의 9자다.
     *
     * **기기 식별자가 아니다.** 앱이 처음 실행될 때 만든 40비트 난수를 저장해
     * 재사용하며, 앱을 지우면 함께 사라진다.
     *
     * **화면에 뜨는 값과 `appendix` 로 나가는 값이 같다.** 표시만 줄이면 사용자가
     * 읽어 준 번호로 서버 기록을 찾지 못하는 일이 생긴다.
     */
    @Synchronized
    fun getId(context: Context): String {
        uniqueID?.let { return it }

        val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(PREF_KEY_ID, null)
        if (saved != null) {
            uniqueID = saved
            return saved
        }

        val generated = generate()
        prefs.edit { putString(PREF_KEY_ID, generated) }
        uniqueID = generated
        return generated
    }

    /** 40비트 난수 → Base32 8자 → `XXXX-XXXX`. */
    private fun generate(): String {
        val bytes = ByteArray(5).also { SecureRandom().nextBytes(it) }
        var bits = 0L
        for (b in bytes) bits = (bits shl 8) or (b.toLong() and 0xFF)

        val chars = CharArray(8)
        for (i in 7 downTo 0) {
            chars[i] = ALPHABET[(bits and 0x1F).toInt()]
            bits = bits shr 5
        }
        return String(chars, 0, 4) + "-" + String(chars, 4, 4)
    }
}

/**
 * 설치 식별자를 화면 상태로 읽는다. 준비되기 전에는 빈 문자열이다 — 저장소 조회라 IO 스레드에서 읽는다.
 * 측정 meta 의 `appendix` 에 **앱이 스스로** 싣는 값이다(SDK 가 수집하지 않는다).
 */
@Composable
fun rememberInstallationId(): String {
    val context = LocalContext.current
    var id by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        id = withContext(Dispatchers.IO) { InstallationIdProvider.getId(context) }
    }
    return id
}
