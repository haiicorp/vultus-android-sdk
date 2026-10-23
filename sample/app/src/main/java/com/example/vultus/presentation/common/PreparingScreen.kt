package com.example.vultus.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.vultus.R

/**
 * 네이티브 워밍업(Vultus.prepareState)이 끝나기 전까지 노출되는 전체 화면.
 * READY 이전에는 스피너와 '준비중' 문구를, FAILED에는 실패 안내만 표시한다.
 */
@Composable
fun PreparingScreen(
    message: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
) {
    Column(
        modifier = modifier.background(colorResource(id = R.color.white)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = colorResource(id = R.color.main_blue))
        }

        Text(
            text = message,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, start = 32.dp, end = 32.dp),
        )
    }
}
