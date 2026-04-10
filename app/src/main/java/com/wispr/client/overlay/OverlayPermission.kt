package com.wispr.client.overlay

import android.content.Context
import android.provider.Settings

object OverlayPermission {
    fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
}
