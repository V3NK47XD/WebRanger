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

    fun captureWebView(webView: WebView, fullResolution: Boolean = false, onCaptured: (Bitmap?) -> Unit) {
        ensureMeasured(webView)
        val width = webView.width
        val height = webView.height

        if (width <= 0 || height <= 0) {
            onCaptured(null)
            return
        }

        val activity = webView.context as? Activity
        // PixelCopy only works when the view is attached to an active window in the foreground
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            webView.isAttachedToWindow && !activity.isFinishing
        ) {
            captureWithPixelCopy(activity, webView) { bitmap ->
                val processed = if (fullResolution) bitmap else bitmap?.let { scaleThumbnail(it) }
                onCaptured(processed)
            }
        } else {
            // Safe software canvas drawing for background or unattached views
            captureWithDrawingCache(webView) { bitmap ->
                val processed = if (fullResolution) bitmap else bitmap?.let { scaleThumbnail(it) }
                onCaptured(processed)
            }
        }
    }

    suspend fun captureWebViewAsync(webView: WebView, fullResolution: Boolean = false): Bitmap? = suspendCoroutine { continuation ->
        captureWebView(webView, fullResolution) { bitmap ->
            continuation.resume(bitmap)
        }
    }

    private fun ensureMeasured(view: View) {
        if (view.width <= 0 || view.height <= 0) {
            val dm = view.context.resources.displayMetrics
            val w = if (view.width > 0) view.width else if (dm.widthPixels > 0) dm.widthPixels else 1080
            val h = if (view.height > 0) view.height else if (dm.heightPixels > 0) dm.heightPixels else 2400
            view.measure(
                View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, w, h)
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
            ensureMeasured(view)
            val w = view.width.coerceAtLeast(1080)
            val h = view.height.coerceAtLeast(1920)
            // Force a software layer so hardware-accelerated WebViews draw correctly
            val prev = view.layerType
            view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            view.setLayerType(prev, null)
            onCaptured(bitmap)
        } catch (e: Exception) {
            onCaptured(null)
        }
    }
}
