package com.eventos.banana.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {
    // Defines IDs for our icons
    enum class AppIcon(val aliasName: String, val nameResId: Int, val iconResName: String) {
        DEFAULT("com.eventos.banana.MainActivityDefault", com.eventos.banana.R.string.app_icon_classic, "ic_launcher"),
        GOLD("com.eventos.banana.MainActivityGold", com.eventos.banana.R.string.app_icon_gold, "ic_launcher_gold"),
        NEON("com.eventos.banana.MainActivityNeon", com.eventos.banana.R.string.app_icon_neon, "ic_launcher_neon"),
        DARK("com.eventos.banana.MainActivityDark", com.eventos.banana.R.string.app_icon_dark, "ic_launcher_dark"),
        SUNSET("com.eventos.banana.MainActivitySunset", com.eventos.banana.R.string.app_icon_sunset, "ic_launcher_sunset")
    }

    fun setIcon(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        
        // Disable others
        AppIcon.values().forEach {
            if (it != icon) {
                val component = ComponentName(context, it.aliasName)
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        // Enable selected
        val component = ComponentName(context, icon.aliasName)
        pm.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
    
    fun getCurrentIcon(context: Context): AppIcon {
        val pm = context.packageManager
        AppIcon.values().forEach {
            val component = ComponentName(context, it.aliasName)
            if (pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return it
            }
        }
        return AppIcon.DEFAULT
    }
}
