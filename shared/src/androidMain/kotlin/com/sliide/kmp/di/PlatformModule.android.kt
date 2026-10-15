package com.sliide.kmp.di

import com.sliide.kmp.data.local.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single { DatabaseDriverFactory(androidContext()) }
    }
