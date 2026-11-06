
package com.wolfassistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * المركز الموحد لأوامر التحكم في الهاتف.
 * يوجّه الأوامر إلى خدمات Android المناسبة.
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

    const val EXTRA_ROW = "row"
    const val EXTRA_COLUMN = "column"

    /**
     * إرسال أمر إلى خدمة التحكم بالشاشة.
     */
    fun execute(
        context: Context,
        action: String,
        row: Int? = null,
        column: Int? = null
    ): Boolean {

        val accessibilityService =
            getAccessibilityService()

        if (accessibilityService == null) {
            Log.w(TAG, "Accessibility service is not connected")
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
            val intent = Intent(serviceAction).setPackage(
                context.packageName
            )

            if (action == ACTION_TAP_CELL) {
                intent.putExtra(
                    WolfAccessibilityService.EXTRA_ROW,
                    row!!
                )

                intent.putExtra(
                    WolfAccessibilityService.EXTRA_COLUMN,
                    column!!
                )
            }

            context.sendBroadcast(intent)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Command failed", e)
            false
        }
    }

    /**
     * التحقق من تفعيل خدمة إمكانية الوصول.
     */
    fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            val enabledServices =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false

            enabledServices.contains(
                context.packageName,
                ignoreCase = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "Accessibility check failed", e)
            false
        }
    }

    /**
     * فتح إعدادات إمكانية الوصول ليقوم المستخدم
     * بتفعيل خدمة الذئب بنفسه.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_ACCESSIBILITY_SETTINGS
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(intent)
    }

    /**
     * نقطة الربط مع خدمة التحكم بالشاشة.
     * سنربطها بالخدمة في الخطوة التالية.
     */
    private fun getAccessibilityService():
        AccessibilityService? {

        return WolfAccessibilityService.instance
    }
}
