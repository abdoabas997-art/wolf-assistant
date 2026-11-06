
package com.wolfassistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class WolfAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_CAPTURE =
            "com.wolfassistant.action.CAPTURE_SCREEN"

        const val ACTION_SHOW_GRID =
            "com.wolfassistant.action.SHOW_GRID"

        const val ACTION_HIDE_GRID =
            "com.wolfassistant.action.HIDE_GRID"

        const val ACTION_TAP_CELL =
            "com.wolfassistant.action.TAP_CELL"

        const val ACTION_SCREEN_CAPTURED =
            "com.wolfassistant.action.SCREEN_CAPTURED"

        const val EXTRA_ROW = "row"
        const val EXTRA_COLUMN = "column"
        const val EXTRA_SCREEN_PATH = "screen_path"

        private const val TAG = "WolfAccessibility"
        private const val GRID_SIZE = 5
    }

    private var windowManager: WindowManager? = null
    private var gridView: GridOverlayView? = null
    private var gridParams: WindowManager.LayoutParams? = null
    private var receiverRegistered = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {

                ACTION_CAPTURE -> captureScreen()

                ACTION_SHOW_GRID -> showGrid()

                ACTION_HIDE_GRID -> hideGrid()

                ACTION_TAP_CELL -> {
                    val row = intent.getIntExtra(EXTRA_ROW, -1)
                    val column = intent.getIntExtra(EXTRA_COLUMN, -1)

                    tapCell(row, column)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        windowManager =
            getSystemService(Context.WINDOW_SERVICE) as WindowManager

        registerCommandReceiver()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        Log.i(TAG, "Wolf Accessibility Service connected")
    }

    private fun registerCommandReceiver() {
        if (receiverRegistered) return

        val filter = IntentFilter().apply {
            addAction(ACTION_CAPTURE)
            addAction(ACTION_SHOW_GRID)
            addAction(ACTION_HIDE_GRID)
            addAction(ACTION_TAP_CELL)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                commandReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(commandReceiver, filter)
        }

        receiverRegistered = true
    }

    // التقاط صورة لما يظهر على الشاشة.
    private fun captureScreen() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screen capture requires Android 11 or newer")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {

                        override fun onSuccess(
                            screenshot: ScreenshotResult
                        ) {
                            val hardwareBuffer =
                                screenshot.hardwareBuffer

                            try {
                                val hardwareBitmap =
                                    Bitmap.wrapHardwareBuffer(
                                        hardwareBuffer,
                                        screenshot.colorSpace
                                    )

                                if (hardwareBitmap == null) {
                                    Log.e(TAG, "Unable to create screenshot bitmap")
                                    return
                                }

                                val bitmap = hardwareBitmap.copy(
                                    Bitmap.Config.ARGB_8888,
                                    false
                                )

                                hardwareBitmap.recycle()

                                if (bitmap != null) {
                                    saveScreenshot(bitmap)
                                    bitmap.recycle()
                                }

                            } catch (e: Exception) {
                                Log.e(TAG, "Screenshot conversion failed", e)
                            } finally {
                                hardwareBuffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            Log.e(
                                TAG,
                                "Screenshot failed: $errorCode"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot request failed", e)
            }
        }
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        try {
            val file = File(
                cacheDir,
                "wolf_screen_${System.currentTimeMillis()}.jpg"
            )

            FileOutputStream(file).use { output ->
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    85,
                    output
                )
            }

            val result = Intent(ACTION_SCREEN_CAPTURED).apply {
                setPackage(packageName)
                putExtra(EXTRA_SCREEN_PATH, file.absolutePath)
            }

            sendBroadcast(result)

            Log.i(TAG, "Screenshot saved")

        } catch (e: Exception) {
            Log.e(TAG, "Unable to save screenshot", e)
        }
    }

    // إظهار شبكة مرقمة فوق الشاشة.
    private fun showGrid() {
        mainHandler.post {
            if (gridView != null) return@post

            try {
                val metrics = getScreenMetrics()

                val overlay = GridOverlayView(this)

                val params = WindowManager.LayoutParams(
                    metrics.widthPixels,
                    metrics.heightPixels,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = 0
                    y = 0
                }

                windowManager?.addView(overlay, params)

                gridView = overlay
                gridParams = params

            } catch (e: Exception) {
                Log.e(TAG, "Unable to show grid", e)
            }
        }
    }

    private fun hideGrid() {
        mainHandler.post {
            try {
                gridView?.let {
                    windowManager?.removeView(it)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to remove grid", e)
            }

            gridView = null
            gridParams = null
        }
    }

    // النقر في مركز مربع محدد.
    // الصفوف والأعمدة تبدأ من الرقم 1.
    private fun tapCell(row: Int, column: Int) {
        if (row !in 1..GRID_SIZE ||
            column !in 1..GRID_SIZE
        ) {
            Log.w(TAG, "Invalid grid coordinates")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "Gesture control requires Android 7 or newer")
            return
        }

        val metrics = getScreenMetrics()

        val cellWidth =
            metrics.widthPixels.toFloat() / GRID_SIZE

        val cellHeight =
            metrics.heightPixels.toFloat() / GRID_SIZE

        val x = (column - 0.5f) * cellWidth
        val y = (row - 0.5f) * cellHeight

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    100
                )
            )
            .build()

        dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    Log.i(
                        TAG,
                        "Tapped row=$row column=$column"
                    )
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    Log.w(TAG, "Tap gesture cancelled")
                }
            },
            null
        )
    }

    @Suppress("DEPRECATION")
    private fun getScreenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val display = windowManager?.defaultDisplay

        if (display != null) {
            display.getRealMetrics(metrics)
        }

        return metrics
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // سنضيف هنا لاحقًا تحليل عناصر الشاشة والتعرف على الأزرار.
    }

    override fun onInterrupt() {
        hideGrid()
    }

    override fun onDestroy() {
        hideGrid()

        if (receiverRegistered) {
            try {
                unregisterReceiver(commandReceiver)
            } catch (_: Exception) {
            }

            receiverRegistered = false
        }

        screenshotExecutor.shutdown()

        super.onDestroy()
    }

    // رسم الصفوف والأعمدة والأرقام الجانبية.
    private class GridOverlayView(
        context: Context
    ) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            textAlign = Paint.Align.CENTER
            style = Paint.Style.FILL
            setShadowLayer(5f, 2f, 2f, Color.BLACK)
        }

        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(180, 20, 35, 55)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cellWidth = width.toFloat() / GRID_SIZE
            val cellHeight = height.toFloat() / GRID_SIZE

            // الخطوط الرأسية.
            for (column in 0..GRID_SIZE) {
                val x = column * cellWidth

                canvas.drawLine(
                    x,
                    0f,
                    x,
                    height.toFloat(),
                    linePaint
                )
            }

            // الخطوط الأفقية.
            for (row in 0..GRID_SIZE) {
                val y = row * cellHeight

                canvas.drawLine(
                    0f,
                    y,
                    width.toFloat(),
                    y,
                    linePaint
                )
            }

            // أرقام الأعمدة في الأعلى.
            for (column in 1..GRID_SIZE) {
                val x = (column - 0.5f) * cellWidth

                canvas.drawCircle(
                    x,
                    38f,
                    22f,
                    labelPaint
                )

                canvas.drawText(
                    column.toString(),
                    x,
                    48f,
                    textPaint
                )
            }

            // أرقام الصفوف على الجانب الأيسر.
            for (row in 1..GRID_SIZE) {
                val y = (row - 0.5f) * cellHeight

                canvas.drawCircle(
                    30f,
                    y,
                    22f,
                    labelPaint
                )

                canvas.drawText(
                    row.toString(),
                    30f,
                    y + 10f,
                    textPaint
                )
            }
        }
    }
}
