package com.example.vultus.utils

import android.app.Activity

fun killApp(activity: Activity) {
    activity.moveTaskToBack(true)
    activity.finishAndRemoveTask()
    android.os.Process.killProcess(android.os.Process.myPid())
}
