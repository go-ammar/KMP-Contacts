package com.sliide.kmp

import androidx.compose.ui.window.ComposeUIViewController
import com.sliide.kmp.di.initKoin
import com.sliide.kmp.ui.App
import platform.UIKit.UIViewController

/**
 * The iOS entry point. Koin is started here rather than in Swift so both platforms register
 * the same modules from the same call.
 */
fun MainViewController(): UIViewController {
    initKoinOnce()
    return ComposeUIViewController { App() }
}

private var isKoinStarted = false

private fun initKoinOnce() {
    if (isKoinStarted) {
        return
    }
    isKoinStarted = true
    initKoin()
}
