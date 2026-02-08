package com.eventos.banana.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconManager {
    // Defines IDs for our icons
    enum class AppIcon(val aliasName: String, val displayName: String, val iconResName: String) {
        DEFAULT("com.eventos.banana.MainActivityDefault", "Clásico", "ic_launcher_background"),
        GOLD("com.eventos.banana.MainActivityGold", "Gold", "ic_launcher_gold"),
        NEON("com.eventos.banana.MainActivityNeon", "Neon", "ic_launcher_neon"),
        DARK("com.eventos.banana.MainActivityDark", "Dark", "ic_launcher_dark")
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
