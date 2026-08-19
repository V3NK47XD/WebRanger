package com.chromemobile.browser

import android.app.Application
import android.webkit.WebView

class ChromeMobileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable Chrome DevTools Protocol debugging on Android WebView
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
