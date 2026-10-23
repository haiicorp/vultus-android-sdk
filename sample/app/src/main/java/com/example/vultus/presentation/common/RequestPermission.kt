package com.example.vultus.presentation.common

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.vultus.R
import com.example.vultus.utils.killApp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale


@ExperimentalPermissionsApi
@Composable
fun RequestPermissions(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermissionState.status == PermissionStatus.Granted) {
        content()
    } else {
        PermissionDeniedContent(
            permissionState = cameraPermissionState,
            shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
            onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
            deniedPermission = { (context as Activity).finish() },
        )
    }
}

@ExperimentalPermissionsApi
@Composable
private fun PermissionDeniedContent(
    permissionState: PermissionState,
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    deniedPermission: () -> Unit
) {
    val context = LocalContext.current


    var backPressedTime by remember { mutableLongStateOf(0L) }
    val backMsg = stringResource(R.string.one_more_back_msg)
    BackHandler {
        if (System.currentTimeMillis() > backPressedTime + 2000) {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context as Activity, backMsg, Toast.LENGTH_SHORT).show()
        } else {
            killApp(context as Activity)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        if (shouldShowRationale) {
            AlertDialog(
                onDismissRequest = {},

                title = {
                    Text(
                        text = stringResource(R.string.permission_request_msg),
                        style = TextStyle(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Text(stringResource(R.string.rational_msg))
                },
                confirmButton = {
                    Button(onClick = { onRequestPermission() }) {
                        Text(stringResource(R.string.agree_msg))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { deniedPermission() }
                    ) {
                        Text(stringResource(R.string.reject_msg))
                    }
                }
            )
        } else {
            // 권한 재요청 -> 수락
            LaunchedEffect(true) {
                permissionState.launchPermissionRequest()
            }
            // 권한 재요청 -> 거절
            Card(
                modifier = Modifier
                    .padding(vertical = 55.dp)
                    .fillMaxWidth(0.4f)
                    .height(200.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(20.dp),
            ) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                )

                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = stringResource(R.string.setting_help_msg),
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // denied button
                    Button(
                        colors = ButtonDefaults.buttonColors(Color.White),
                        onClick = { run { killApp(context as Activity) } }) {
                        Text(text = "종료")
                    }

                    // confirm button
                    Button(
                        modifier = Modifier
                            .shadow(
                                elevation = 4.dp,
                                ambientColor = Color.DarkGray,
                                spotColor = Color.White,
                                shape = RoundedCornerShape(20.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Blue,
                            disabledContainerColor = Color.Gray
                        ),
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                ("package:" + context.packageName).toUri()
                            )

                            if (context.packageManager != null) {
                                context.startActivity(intent)
                            }
                        }) {
                        Text(stringResource(R.string.setting_msg), color = Color.White)
                    }
                }
            }
        }
    }
}