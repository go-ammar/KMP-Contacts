package com.sliide.kmp

import android.app.Application
import com.sliide.kmp.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class SliideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidLogger()
            androidContext(this@SliideApplication)
        }
    }
}
