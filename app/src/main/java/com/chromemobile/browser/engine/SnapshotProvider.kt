package com.chromemobile.browser.engine

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object SnapshotProvider {

    fun captureWebView(webView: WebView, onCaptured: (Bitmap?) -> Unit) {
        val width = webView.width
        val height = webView.height

        if (width <= 0 || height <= 0) {
            onCaptured(null)
            return
        }

        val activity = webView.context as? Activity
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWithPixelCopy(activity, webView, onCaptured)
        } else {
            captureWithDrawingCache(webView, onCaptured)
        }
    }

    suspend fun captureWebViewAsync(webView: WebView): Bitmap? = suspendCoroutine { continuation ->
        captureWebView(webView) { bitmap ->
            continuation.resume(bitmap)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun captureWithPixelCopy(activity: Activity, view: View, onCaptured: (Bitmap?) -> Unit) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationInWindow(location)

        val rect = Rect(
            location[0],
            location[1],
            location[0] + view.width,
            location[1] + view.height
        )

        try {
            PixelCopy.request(
                activity.window,
                rect,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        onCaptured(bitmap)
                    } else {
                        // Fallback to software draw if PixelCopy returns error
                        captureWithDrawingCache(view, onCaptured)
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            captureWithDrawingCache(view, onCaptured)
        }
    }

    private fun captureWithDrawingCache(view: View, onCaptured: (Bitmap?) -> Unit) {
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            onCaptured(bitmap)
        } catch (e: Exception) {
            onCaptured(null)
        }
    }
}
