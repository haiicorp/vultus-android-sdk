package com.example.vultus.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 외장(USB) 카메라의 USB 권한을 묻는다. **SDK 는 USB 권한을 요청하지 않는다** — 권한이 없으면 어댑터를 부르지 않고
 * `NO_PERMISSION` 으로 남으므로 앱이 묻는다.
 *
 * ⚠️ 다른 USB 장치가 곧이어 권한을 요청하면 앞의 권한 창이 결과 없이 닫힐 수 있다. 그래서 물은 뒤 [RETRY_MS] 안에 결과가 없으면 다시 묻는다.
 */
object UsbCameraPermission {
    private const val TAG = "UsbCameraPermission"
    private const val ACTION = "com.example.vultus.USB_CAMERA_PERMISSION"
    private const val RETRY_MS = 10_000L

    private val main = Handler(Looper.getMainLooper())
    private var receiver: BroadcastReceiver? = null
    private var pending: Runnable? = null

    /**
     * 권한이 없는 영상 USB 장치가 있으면 묻는다. 허용되면 [onGranted] 를 메인 스레드에서 부른다 — 어댑터를 다시 붙여 SDK 가 곧바로 설정을
     * 걸게 한다. 거절되면 다시 묻지 않는다(다시 꽂으면 다시 부르면 된다). 메인 스레드에서 부른다.
     */
    fun request(context: Context, onGranted: () -> Unit) {
        val app = context.applicationContext
        val usb = app.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return
        val device = usb.deviceList.values.firstOrNull { it.isVideoDevice() && !usb.hasPermission(it) } ?: return
        if (receiver == null) {
            val r = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    pending?.let { main.removeCallbacks(it) }
                    pending = null
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "permission granted=$granted")
                    if (granted) onGranted()
                }
            }
            // 권한 결과는 시스템이 보낸다 — NOT_EXPORTED 여도 받는다.
            ContextCompat.registerReceiver(app, r, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
            receiver = r
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        usb.requestPermission(device, PendingIntent.getBroadcast(app, 0, Intent(ACTION).setPackage(app.packageName), flags))
        pending?.let { main.removeCallbacks(it) }
        pending = Runnable { pending = null; request(app, onGranted) }.also { main.postDelayed(it, RETRY_MS) }
    }

    /** 화면을 떠날 때 부른다 — 기다리던 재요청과 수신자를 거둔다. */
    fun release(context: Context) {
        pending?.let { main.removeCallbacks(it) }
        pending = null
        receiver?.let { r -> runCatching { context.applicationContext.unregisterReceiver(r) } }
        receiver = null
    }

    private fun UsbDevice.isVideoDevice(): Boolean =
        deviceClass == UsbConstants.USB_CLASS_VIDEO ||
            (0 until interfaceCount).any { getInterface(it).interfaceClass == UsbConstants.USB_CLASS_VIDEO }
}
