package dev.showroom.fold.demos.platform

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

actual val currentPlatform: PlatformKind = PlatformKind.Android

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
