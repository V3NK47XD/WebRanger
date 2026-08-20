package com.chromemobile.browser.engine

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
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

    // Target thumbnail dimensions — wide enough for the card grid, small enough to not waste memory
    private const val THUMB_W = 480
    private const val THUMB_H = 320

    fun captureWebView(webView: WebView, onCaptured: (Bitmap?) -> Unit) {
        val width = webView.width
        val height = webView.height

        if (width <= 0 || height <= 0) {
            onCaptured(null)
            return
        }

        val activity = webView.context as? Activity
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            captureWithPixelCopy(activity, webView) { bitmap ->
                onCaptured(bitmap?.let { scaleThumbnail(it) })
            }
        } else {
            captureWithDrawingCache(webView) { bitmap ->
                onCaptured(bitmap?.let { scaleThumbnail(it) })
            }
        }
    }

    suspend fun captureWebViewAsync(webView: WebView): Bitmap? = suspendCoroutine { continuation ->
        captureWebView(webView) { bitmap ->
            continuation.resume(bitmap)
        }
    }

    /** Downscale a full-resolution capture to a compact thumbnail. */
    private fun scaleThumbnail(src: Bitmap): Bitmap {
        if (src.width <= 0 || src.height <= 0) return src
        val scaleW = THUMB_W.toFloat() / src.width
        val scaleH = THUMB_H.toFloat() / src.height
        val scale = minOf(scaleW, scaleH).coerceAtMost(1f) // never upscale
        val dstW = (src.width * scale).toInt().coerceAtLeast(1)
        val dstH = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, dstW, dstH, true)
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
            // Force a software layer so hardware-accelerated WebViews draw correctly
            val prev = view.layerType
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            view.setLayerType(prev, null)
            onCaptured(bitmap)
        } catch (e: Exception) {
            onCaptured(null)
        }
    }
}
