package com.wolfassistant

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * المركز الموحد لتوجيه أوامر التحكم في الهاتف.
 */
object WolfPhoneController {

    private const val TAG = "WolfPhoneController"

    const val ACTION_SHOW_GRID =
        "com.wolfassistant.action.SHOW_GRID"

    const val ACTION_HIDE_GRID =
        "com.wolfassistant.action.HIDE_GRID"

    const val ACTION_CAPTURE_SCREEN =
        "com.wolfassistant.action.CAPTURE_SCREEN"

    const val ACTION_TAP_CELL =
        "com.wolfassistant.action.TAP_CELL"

    fun execute(
        context: Context,
        action: String,
        row: Int? = null,
        column: Int? = null
    ): Boolean {

        if (!isAccessibilityEnabled(context)) {
            Log.w(TAG, "Accessibility service is disabled")
            return false
        }

        val serviceAction = when (action) {
            ACTION_SHOW_GRID ->
                WolfAccessibilityService.ACTION_SHOW_GRID

            ACTION_HIDE_GRID ->
                WolfAccessibilityService.ACTION_HIDE_GRID

            ACTION_CAPTURE_SCREEN ->
                WolfAccessibilityService.ACTION_CAPTURE

            ACTION_TAP_CELL ->
                WolfAccessibilityService.ACTION_TAP_CELL

            else -> return false
        }

        if (action == ACTION_TAP_CELL) {
            if (
                row == null ||
                column == null ||
                row !in 1..5 ||
                column !in 1..5
            ) {
                return false
            }
        }

        return try {
            val intent = Intent(serviceAction).apply {
                setPackage(context.packageName)

                if (action == ACTION_TAP_CELL) {
                    putExtra(
                        WolfAccessibilityService.EXTRA_ROW,
                        row!!
                    )

                    putExtra(
                        WolfAccessibilityService.EXTRA_COLUMN,
                        column!!
                    )
                }
            }

            context.sendBroadcast(intent)

            Log.i(TAG, "Command dispatched: $action")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Command failed", e)
            false
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val expectedComponent = ComponentName(
                context,
                WolfAccessibilityService::class.java
            ).flattenToString()

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            enabledServices.split(":").any {
                it.equals(
                    expectedComponent,
                    ignoreCase = true
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Accessibility check failed", e)
            false
        }
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Unable to open accessibility settings", e)
        }
    }
}
